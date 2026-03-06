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

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;

/**
 * Provides real-time metrics about the cluster status.
 * Scans Redis for heartbeats and queue sizes.
 */
public class StatsService {

    private final JedisPool jedisPool;
    private static final String HEARTBEAT_PREFIX = "fw:workers:";
    private static final String QUEUE_PREFIX = "fw:queue:";
    private static final String SCHEDULED_KEY = JobDispatcher.SCHEDULED_KEY;

    public StatsService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public Map<String, Object> getClusterStats() {
        Map<String, Object> stats = new HashMap<>();

        try (Jedis jedis = jedisPool.getResource()) {
            // 1. Get Active Nodes and discover registered worker names
            Set<String> nodes = jedis.keys(HEARTBEAT_PREFIX + "*");
            List<Map<String, String>> nodeDetails = new ArrayList<>();
            Set<String> registeredQueues = new HashSet<>();

            for (String key : nodes) {
                Map<String, String> data = jedis.hgetAll(key);
                nodeDetails.add(data);

                // Extract worker names from JSON array: ["processOrder:4","sendEmail:2"]
                String workersJson = data.get("workers");
                if (workersJson != null && workersJson.startsWith("[")) {
                    String clean = workersJson.substring(1, workersJson.length() - 1);
                    if (!clean.isEmpty()) {
                        for (String w : clean.split(",")) {
                            // w is "processOrder:4"
                            String name = w.replace("\"", "").split(":")[0];
                            registeredQueues.add(name);
                        }
                    }
                }
            }
            stats.put("activeNodes", nodeDetails);

            // 2. Get Queues and their sizes
            Map<String, Long> queueStats = new HashMap<>();
            for (String qName : registeredQueues) {
                // Doğrudan kuyruk adıyla sorgula (fw:queue:processOrder gibi)
                long size = jedis.llen(QUEUE_PREFIX + qName);
                queueStats.put(qName, size);
            }
            stats.put("queues", queueStats);

            // 3. Get Scheduled Jobs count
            long scheduledCount = jedis.zcard(SCHEDULED_KEY);
            stats.put("scheduledCount", scheduledCount);

            stats.put("timestamp", System.currentTimeMillis());
        } catch (Exception e) {
            stats.put("error", e.getMessage());
        }

        return stats;
    }
}
