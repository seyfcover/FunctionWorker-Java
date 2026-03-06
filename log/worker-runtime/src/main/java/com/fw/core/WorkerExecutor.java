/*
 * Copyright (c) 2026 FunctionWorker Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.fw.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fw.annotation.Worker;
import com.fw.model.Job;
import com.fw.model.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/**
 * Tek bir @Worker metodunu n-concurrency ile çalıştıran executor.
 *
 * Her instance:
 * - kendi thread pool'una sahip (concurrency adet thread)
 * - Redis'ten blocking pop ile job alır
 * - timeout/memory sandbox uygular
 * - sonucu Redis'e yazar (SETEX 60s TTL)
 * - hata durumunda retry queue'ya geri dönderir
 */
public class WorkerExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutor.class);

    // Redis key prefix'leri
    static final String QUEUE_PREFIX = "fw:queue:";
    static final String RESULT_PREFIX = "fw:result:";
    static final String RETRY_PREFIX = "fw:retry:";

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;
    private final Object targetBean; // @Worker metodunun bulunduğu nesne
    private final Method method;
    private final Worker annotation;
    private final String workerName;
    private final int concurrency;
    private final ExecutorService consumerPool;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService monitorPool;
    private volatile boolean running = false;

    private final String hostname;

    public WorkerExecutor(JedisPool jedisPool, ObjectMapper mapper,
            Object targetBean, Method method) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
        this.targetBean = targetBean;
        this.method = method;
        this.annotation = method.getAnnotation(Worker.class);

        // Worker adı: annotation veya method adı
        this.workerName = annotation.name().isBlank() ? method.getName() : annotation.name();

        // Concurrency: annotation veya CPU çekirdek sayısı
        this.concurrency = annotation.concurrency() > 0
                ? annotation.concurrency()
                : Runtime.getRuntime().availableProcessors();

        // Consumer pool: Kuyruktan brpop yapan thread'ler
        this.consumerPool = Executors.newFixedThreadPool(concurrency,
                r -> new Thread(r, "fw-consumer-" + workerName + "-" + System.nanoTime()));

        // Worker pool: Gerçek işi yapan thread'ler (Kilitlenme için consumer'dan ayrı
        // tutuyoruz)
        this.workerPool = Executors.newFixedThreadPool(concurrency,
                r -> new Thread(r, "fw-worker-" + workerName + "-" + System.nanoTime()));

        // Timeout monitor için ayrı pool
        this.monitorPool = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, "fw-monitor-" + workerName));

        try {
            this.hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("WorkerExecutor created: name={}, concurrency={}, timeout={}ms",
                workerName, concurrency, annotation.timeout());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        running = true;
        // Her concurrency slotu için ayrı consumer thread başlat
        for (int i = 0; i < concurrency; i++) {
            final int slot = i;
            consumerPool.submit(() -> consumeLoop(slot));
        }
        log.info("▶ Worker '{}' started with {} concurrent threads", workerName, concurrency);
    }

    public void stop() {
        running = false;
        consumerPool.shutdownNow();
        workerPool.shutdownNow();
        monitorPool.shutdownNow();
        log.info("■ Worker '{}' stopped", workerName);
    }

    // ── Consumer Loop ─────────────────────────────────────────────────────────

    private void consumeLoop(int slot) {
        String queueKey = QUEUE_PREFIX + workerName;
        String processingKey = "fw:processing:" + workerName + ":" + hostname + ":" + slot;
        log.debug("[{}][slot-{}] Listening (Reliable) on queue '{}'", workerName, slot, queueKey);

        while (running && !Thread.currentThread().isInterrupted()) {
            try (Jedis jedis = jedisPool.getResource()) {
                // RPOPLPUSH (Reliable Queue Pattern)
                // İşi kuyruktan alır ve atomik olarak "işleniyor" listesine taşır.
                String jobJson = jedis.brpoplpush(queueKey, processingKey, 2);

                if (jobJson == null)
                    continue;

                try {
                    Job job = mapper.readValue(jobJson, Job.class);
                    log.info("[{}] Job received: {}", workerName, job.getId());

                    // Job'u çalıştır (bloklar)
                    executeWithSandbox(job);
                } finally {
                    // İş bitti (başarılı, timeout veya kalıcı hata), işleniyor listesinden sil.
                    jedis.lrem(processingKey, 1, jobJson);
                }

            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted())
                    break;
                log.error("[{}] Consumer loop error", workerName, e);
                sleepSilently(1000); // kısa bekle ve tekrar dene
            }
        }
    }

    // ── Sandbox Execution ─────────────────────────────────────────────────────

    /**
     * Job'u sandbox içinde çalıştırır:
     * - Timeout (ScheduledFuture ile iptal)
     * - Memory kontrolü (job bitmeden anlık kontrol)
     * - Retry mantığı (hata durumunda)
     */
    private void executeWithSandbox(Job job) {
        long startMs = System.currentTimeMillis();

        // Job execution future
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                // ── Memory kontrolü (before execution) ──────────────────────
                checkMemory(job);

                // ── Parametre deserializasyonu ───────────────────────────────
                Class<?> paramType = method.getParameterCount() > 0
                        ? method.getParameterTypes()[0]
                        : null;

                Object input = (paramType != null && job.getPayload() != null && !job.getPayload().equals("null"))
                        ? mapper.readValue(job.getPayload(), paramType)
                        : null;

                // ── Metodu çağır ─────────────────────────────────────────────
                Object output = (input != null)
                        ? method.invoke(targetBean, input)
                        : method.invoke(targetBean);

                // ── Sonucu serialize et ──────────────────────────────────────
                return mapper.writeValueAsString(output);

            } catch (Exception e) {
                // Gerçek exception'ı unwrap et
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException(cause);
            }
        });

        // Timeout tanımlı süre sonra future'ı iptal eder
        ScheduledFuture<?> timeoutGuard = monitorPool.schedule(
                () -> future.cancel(true),
                annotation.timeout(),
                TimeUnit.MILLISECONDS);

        try {
            String resultJson = future.get(annotation.timeout() + 500, TimeUnit.MILLISECONDS);
            timeoutGuard.cancel(false);

            long duration = System.currentTimeMillis() - startMs;
            JobResult result = JobResult.success(job.getId(), resultJson, duration,
                    hostname + "/" + workerName);

            writeResult(job.getReplyKey(), result);
            log.info("[{}] ✓ Job {} completed in {}ms", workerName, job.getId(), duration);

        } catch (CancellationException | TimeoutException e) {
            long duration = System.currentTimeMillis() - startMs;
            JobResult result = JobResult.timeout(job.getId(), duration, hostname + "/" + workerName);
            writeResult(job.getReplyKey(), result);
            log.warn("[{}] ⏱ Job {} TIMEOUT after {}ms", workerName, job.getId(), duration);

        } catch (ExecutionException e) {
            long duration = System.currentTimeMillis() - startMs;
            Throwable cause = e.getCause();
            String stackTrace = getStackTrace(cause);

            // Retry kontrolü
            if (job.getRetryCount() < job.getMaxRetry()) {
                job.setRetryCount(job.getRetryCount() + 1);
                requeueForRetry(job);
                log.warn("[{}] ↩ Job {} failed, retry {}/{}: {}",
                        workerName, job.getId(), job.getRetryCount(), job.getMaxRetry(), cause.getMessage());
            } else {
                JobResult result = JobResult.failed(job.getId(), cause.getMessage(),
                        stackTrace, duration, hostname + "/" + workerName);
                writeResult(job.getReplyKey(), result);
                log.error("[{}] ✗ Job {} FAILED permanently: {}", workerName, job.getId(), cause.getMessage());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            timeoutGuard.cancel(false);
        }
    }

    // ── Redis Helpers ─────────────────────────────────────────────────────────

    private void writeResult(String replyKey, JobResult result) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(result);
            jedis.setex(replyKey, 120, json); // 120 saniye TTL
        } catch (Exception e) {
            log.error("Result write failed for key {}", replyKey, e);
        }
    }

    private void requeueForRetry(Job job) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Kısa gecikme ile retry (exponential backoff: 1s, 2s, 4s...)
            long delayMs = (long) Math.pow(2, job.getRetryCount()) * 1000;
            Thread.sleep(delayMs);

            String jobJson = mapper.writeValueAsString(job);
            jedis.lpush(QUEUE_PREFIX + workerName, jobJson);
        } catch (Exception e) {
            log.error("Requeue failed for job {}", job.getId(), e);
        }
    }

    // ── Memory Check ─────────────────────────────────────────────────────────

    private void checkMemory(Job job) {
        if (annotation.memoryMb() <= 0)
            return;

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long usedMb = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long limitMb = annotation.memoryMb();

        if (usedMb > limitMb) {
            throw new RuntimeException("MEMORY_EXCEEDED: used=" + usedMb + "MB limit=" + limitMb + "MB");
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String getStackTrace(Throwable t) {
        if (t == null)
            return "";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String getWorkerName() {
        return workerName;
    }

    public int getConcurrency() {
        return concurrency;
    }
}
