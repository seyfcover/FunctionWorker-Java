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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fw.annotation.Worker;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * FunctionWorker Runtime ana sınıfı.
 *
 * <h3>Kullanım:</h3>
 * 
 * <pre>
 * WorkerRuntime runtime = WorkerRuntime.builder()
 *         .redisHost("localhost")
 *         .redisPort(6379)
 *         .scanPackage("com.myapp.workers")
 *         .build();
 *
 * runtime.start(); // @Worker metodlarını keşfeder, executor'ları başlatır
 * runtime.awaitShutdown(); // CTRL+C ile temiz kapanış
 * </pre>
 *
 * <h3>Tek satır başlatma:</h3>
 * 
 * <pre>
 * WorkerRuntime.start("com.myapp.workers"); // localhost:6379 varsayılan
 * </pre>
 */
public class WorkerRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorkerRuntime.class);

    // ── Konfigürasyon ─────────────────────────────────────────────────────────

    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final String scanPackage;

    // ── Runtime state ─────────────────────────────────────────────────────────

    private JedisPool jedisPool;
    private ObjectMapper mapper;
    private JobDispatcher dispatcher;
    private HeartbeatService heartbeat;
    private StatsService statsService;
    private SchedulerService schedulerService;
    private final List<WorkerExecutor> executors = new ArrayList<>();
    private String hostname;

    // ── Constructor (Builder ile) ─────────────────────────────────────────────

    private WorkerRuntime(Builder b) {
        this.redisHost = b.redisHost;
        this.redisPort = b.redisPort;
        this.redisPassword = b.redisPassword;
        this.scanPackage = b.scanPackage;
    }

    // ── Kolay başlatma ────────────────────────────────────────────────────────

    /** Sıfır-konfigürasyonlu başlatma (localhost:6379) */
    public static WorkerRuntime start(String scanPackage) {
        WorkerRuntime runtime = builder().scanPackage(scanPackage).build();
        runtime.start();
        return runtime;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        log.info("╔══════════════════════════════════════╗");
        log.info("║   FunctionWorker Runtime v1.0        ║");
        log.info("╚══════════════════════════════════════╝");

        initCore();

        // 5. @Worker metodlarını keşfet
        List<WorkerRegistration> registrations = discoverWorkers();
        if (registrations.isEmpty()) {
            log.warn("⚠ Hiç @Worker metodu bulunamadı! Paket: {}", scanPackage);
        }

        // 6. Heartbeat servisi
        heartbeat = new HeartbeatService(jedisPool, hostname);

        // 7. Her @Worker için executor başlat
        for (WorkerRegistration reg : registrations) {
            WorkerExecutor executor = new WorkerExecutor(jedisPool, mapper, reg.bean, reg.method);
            executors.add(executor);
            heartbeat.registerWorker(executor.getWorkerName(), executor.getConcurrency());
            executor.start();
        }

        // 8. Heartbeat başlat
        heartbeat.start();

        // 9. Scheduler başlat
        schedulerService = new SchedulerService(jedisPool, mapper);
        schedulerService.start();

        // 10. Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "fw-shutdown"));

        log.info("🚀 Runtime hazır. {} worker tipi, {} executor aktif",
                registrations.size(), executors.size());
    }

    private final java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return; // Zaten kapatıldı
        }

        log.info("⏹ Runtime durduruluyor...");
        executors.forEach(WorkerExecutor::stop);
        if (heartbeat != null)
            heartbeat.stop();
        if (schedulerService != null)
            schedulerService.stop();
        if (jedisPool != null)
            jedisPool.close();
        log.info("✓ Runtime kapatıldı.");
    }

    /**
     * Ana thread'i bloklar — JVM shutdown sinyaline kadar bekler.
     * main() metodunun sonunda çağrılır.
     */
    public void awaitShutdown() {
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Worker Discovery ──────────────────────────────────────────────────────

    /**
     * Belirlenen paketi tarar, @Worker annotasyonunu içeren tüm metodları bulur.
     * Her metod için ilgili sınıfı instantiate eder.
     */
    private List<WorkerRegistration> discoverWorkers() {
        log.info("Taranıyor: {}", scanPackage);

        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                        .forPackage(scanPackage)
                        .addScanners(Scanners.MethodsAnnotated));

        var methods = reflections.getMethodsAnnotatedWith(Worker.class);
        List<WorkerRegistration> regs = new ArrayList<>();

        for (Method method : methods) {
            try {
                // Sınıfı no-arg constructor ile instantiate et
                Object bean = method.getDeclaringClass().getDeclaredConstructor().newInstance();
                method.setAccessible(true);

                Worker ann = method.getAnnotation(Worker.class);
                String name = ann.name().isBlank() ? method.getName() : ann.name();

                regs.add(new WorkerRegistration(bean, method));
                log.info("  ✓ @Worker keşfedildi: {}.{} → name='{}', concurrency={}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        name,
                        ann.concurrency() > 0 ? ann.concurrency() : Runtime.getRuntime().availableProcessors());

            } catch (Exception e) {
                log.error("  ✗ Worker instantiation hatası: {}.{}",
                        method.getDeclaringClass().getName(), method.getName(), e);
            }
        }

        return regs;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public JobDispatcher getDispatcher() {
        if (dispatcher == null) {
            initCore();
        }
        return dispatcher;
    }

    public JedisPool getJedisPool() {
        if (jedisPool == null) {
            initCore();
        }
        return jedisPool;
    }

    public ObjectMapper getMapper() {
        if (mapper == null) {
            initCore();
        }
        return mapper;
    }

    public StatsService getStatsService() {
        if (statsService == null) {
            initCore();
        }
        return statsService;
    }

    // ── Internal Init ──────────────────────────────────────────────────────────

    private synchronized void initCore() {
        if (jedisPool != null)
            return; // Zaten başlatıldı

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "host-" + System.nanoTime();
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);

        if (redisPassword != null && !redisPassword.isBlank()) {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
        } else {
            jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
        }

        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // Initialize Services
        dispatcher = new JobDispatcher(jedisPool, mapper);
        statsService = new StatsService(jedisPool);
    }

    public String getHostname() {
        return hostname;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        private int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        private String redisPassword = System.getenv("REDIS_PASSWORD");
        private String scanPackage = "";

        public Builder redisHost(String host) {
            this.redisHost = host;
            return this;
        }

        public Builder redisPort(int port) {
            this.redisPort = port;
            return this;
        }

        public Builder redisPassword(String pwd) {
            this.redisPassword = pwd;
            return this;
        }

        public Builder scanPackage(String pkg) {
            this.scanPackage = pkg;
            return this;
        }

        public WorkerRuntime build() {
            if (scanPackage == null || scanPackage.isBlank())
                throw new IllegalStateException("scanPackage boş olamaz");
            return new WorkerRuntime(this);
        }
    }

    // ── Inner ─────────────────────────────────────────────────────────────────

    private record WorkerRegistration(Object bean, Method method) {
    }
}
