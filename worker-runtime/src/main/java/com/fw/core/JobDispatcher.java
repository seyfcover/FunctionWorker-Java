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
import com.fw.model.Job;
import com.fw.model.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Job gönderme ve sonuç bekleme — client tarafı.
 *
 * <pre>
 * JobDispatcher dispatcher = runtime.getDispatcher();
 *
 * // Fire-and-forget (sonucu umursamıyoruz)
 * dispatcher.dispatch("sendEmail", emailReq);
 *
 * // Sonucu bekle (sync)
 * OrderResult result = dispatcher.dispatchAndWait("processOrder", orderReq, OrderResult.class, 30);
 * </pre>
 */
public class JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private static final String QUEUE_PREFIX = WorkerExecutor.QUEUE_PREFIX;
    private static final String RESULT_PREFIX = WorkerExecutor.RESULT_PREFIX;
    public static final String SCHEDULED_KEY = "fw:scheduled_jobs";

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;

    public JobDispatcher(JedisPool jedisPool, ObjectMapper mapper) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
    }

    // ── Fire-and-Forget ───────────────────────────────────────────────────────

    /**
     * Job'u queue'ya bırakır, sonucu beklemez.
     * 
     * @return Job ID (sonradan takip için)
     */
    public String dispatch(String functionName, Object input) {
        return dispatch(functionName, input, 3, 30_000);
    }

    public String dispatch(String functionName, Object input, int maxRetry, long timeoutMs) {
        Job job = createJob(functionName, input, maxRetry, timeoutMs);
        try (Jedis jedis = jedisPool.getResource()) {
            String jobJson = mapper.writeValueAsString(job);
            jedis.lpush(QUEUE_PREFIX + functionName, jobJson);
            log.info("Job dispatched → fn='{}', id='{}'", functionName, job.getId());
            return job.getId();
        } catch (Exception e) {
            throw new RuntimeException("Dispatch failed for function: " + functionName, e);
        }
    }

    /**
     * Belirli bir zaman diliminde çalışacak iş planlar.
     * 
     * @param functionName Hedef fonksiyon
     * @param input        Input datası
     * @param scheduledAt  Zaman damgası (epoch millis)
     * @return Job ID
     */
    public String dispatchAt(String functionName, Object input, long scheduledAt) {
        Job job = createJob(functionName, input, 3, 30_000);
        job.setScheduledAt(scheduledAt);

        try (Jedis jedis = jedisPool.getResource()) {
            String jobJson = mapper.writeValueAsString(job);
            // ZSET: score=zaman, member=job
            jedis.zadd(SCHEDULED_KEY, (double) scheduledAt, jobJson);
            log.info("Job scheduled (at {}) → fn='{}', id='{}'",
                    java.time.Instant.ofEpochMilli(scheduledAt), functionName, job.getId());
            return job.getId();
        } catch (Exception e) {
            throw new RuntimeException("Schedule failed for function: " + functionName, e);
        }
    }

    private Job createJob(String functionName, Object input, int maxRetry, long timeoutMs) {
        try {
            String payload = (input != null) ? mapper.writeValueAsString(input) : "null";
            return Job.create(functionName, payload, maxRetry, timeoutMs);
        } catch (Exception e) {
            throw new RuntimeException("Job creation failed", e);
        }
    }

    // ── Sync Wait ─────────────────────────────────────────────────────────────

    /**
     * Job'u queue'ya bırakır ve sonucu bekler (blocking).
     *
     * @param functionName Hedef worker fonksiyon adı
     * @param input        Input nesnesi (JSON'a serialize edilir)
     * @param resultType   Beklenen sonuç tipi
     * @param timeoutSec   Maksimum bekleme süresi (saniye)
     */
    public <T> T dispatchAndWait(String functionName, Object input,
            Class<T> resultType, long timeoutSec)
            throws TimeoutException, JobFailedException {

        String jobId = dispatch(functionName, input);
        String replyKey = RESULT_PREFIX + jobId;

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSec);

        log.debug("Waiting for result of job '{}' (timeout={}s)", jobId, timeoutSec);

        while (System.currentTimeMillis() < deadline) {
            try (Jedis jedis = jedisPool.getResource()) {
                String resultJson = jedis.get(replyKey);

                if (resultJson != null) {
                    JobResult jobResult = mapper.readValue(resultJson, JobResult.class);

                    if (jobResult.isSuccess()) {
                        // Sonucu beklenen tipe dönüştür
                        return mapper.readValue(jobResult.getResult(), resultType);
                    } else {
                        throw new JobFailedException(jobId, jobResult.getStatus().name(),
                                jobResult.getErrorMessage());
                    }
                }
            } catch (JobFailedException jfe) {
                throw jfe;
            } catch (Exception e) {
                log.error("Result polling error for job {}", jobId, e);
            }

            // Yoksa 100ms bekle ve tekrar kontrol et
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        throw new TimeoutException("Job " + jobId + " did not complete within " + timeoutSec + "s");
    }

    /**
     * Job sonucunu asenkron olarak kontrol eder (non-blocking).
     * 
     * @return JobResult veya null (henüz tamamlanmadıysa)
     */
    public JobResult pollResult(String jobId) {
        String replyKey = RESULT_PREFIX + jobId;
        try (Jedis jedis = jedisPool.getResource()) {
            String resultJson = jedis.get(replyKey);
            if (resultJson == null)
                return null;
            return mapper.readValue(resultJson, JobResult.class);
        } catch (Exception e) {
            log.error("Poll error for job {}", jobId, e);
            return null;
        }
    }

    // ── Custom Exception ──────────────────────────────────────────────────────

    public static class JobFailedException extends RuntimeException {
        private final String jobId;
        private final String status;

        public JobFailedException(String jobId, String status, String message) {
            super("[" + status + "] Job " + jobId + " failed: " + message);
            this.jobId = jobId;
            this.status = status;
        }

        public String getJobId() {
            return jobId;
        }

        public String getStatus() {
            return status;
        }
    }
}
