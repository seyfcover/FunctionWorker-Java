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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Heartbeat servisi — bu worker'ın canlı olduğunu Redis'e bildirir.
 *
 * Redis key: fw:workers:{hostname}
 * Value: JSON hash {hostname, workers, startedAt, lastBeat}
 * TTL: 15 saniye (5s interval ile yenilenir)
 *
 * Monitoring: CLI bu key'leri okuyarak cluster durumunu gösterir.
 */
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    static final String WORKERS_PREFIX = "fw:workers:";
    static final int TTL_SECONDS = 15;
    static final int BEAT_INTERVAL = 5; // saniye

    private final JedisPool jedisPool;
    private final String hostname;
    private final Map<String, Integer> workerConcurrency; // name → concurrency
    private final ScheduledExecutorService scheduler;

    private long startedAt;

    public HeartbeatService(JedisPool jedisPool, String hostname) {
        this.jedisPool = jedisPool;
        this.hostname = hostname;
        this.workerConcurrency = new HashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "fw-heartbeat"));
    }

    public void registerWorker(String name, int concurrency) {
        workerConcurrency.put(name, concurrency);
    }

    public void start() {
        this.startedAt = System.currentTimeMillis();
        // Boot anında bir kez çalıştır
        beat();
        // Sonra BEAT_INTERVAL'da bir çalıştır
        scheduler.scheduleAtFixedRate(this::beat, BEAT_INTERVAL, BEAT_INTERVAL, TimeUnit.SECONDS);
        log.info("❤ Heartbeat started for host '{}' (interval={}s, TTL={}s)",
                hostname, BEAT_INTERVAL, TTL_SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        // Redis'teki kaydı temizle
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(WORKERS_PREFIX + hostname);
            log.info("Heartbeat removed for host '{}'", hostname);
        } catch (Exception e) {
            log.warn("Could not remove heartbeat key", e);
        }
    }

    // ── Beat ─────────────────────────────────────────────────────────────────

    private void beat() {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = WORKERS_PREFIX + hostname;

            Map<String, String> info = new HashMap<>();
            info.put("hostname", hostname);
            info.put("startedAt", String.valueOf(startedAt));
            info.put("lastBeat", String.valueOf(System.currentTimeMillis()));
            info.put("workers", workerNamesJson());
            info.put("pid", String.valueOf(ProcessHandle.current().pid()));

            jedis.hset(key, info);
            jedis.expire(key, TTL_SECONDS);

        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    private String workerNamesJson() {
        // Basit JSON array: ["processOrder:4","sendEmail:2"]
        StringBuilder sb = new StringBuilder("[");
        workerConcurrency.forEach(
                (name, concurrency) -> sb.append("\"").append(name).append(":").append(concurrency).append("\","));
        if (sb.charAt(sb.length() - 1) == ',')
            sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
