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
package com.fw.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Worker CLI — cluster durumu, queue istatistikleri ve log izleme.
 *
 * Kullanım:
 * java -jar worker-runtime.jar status
 * java -jar worker-runtime.jar queue-stats
 * java -jar worker-runtime.jar dispatch processOrder '{"orderId":"123"}'
 */
public class WorkerCLI {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

        if (args.length == 0) {
            startInteractiveShell(redisHost, redisPort);
            return;
        }

        try (JedisPool pool = new JedisPool(redisHost, redisPort);
                Jedis jedis = pool.getResource()) {

            handleCommand(jedis, args);
        } catch (Exception e) {
            System.err.println(RED + "Error: " + e.getMessage() + RESET);
            System.err.println(GRAY + "Redis connection: " + redisHost + ":" + redisPort + RESET);
        }
    }

    private static void startInteractiveShell(String host, int port) {
        System.out.println(BOLD + CYAN + "FunctionWorker Interactive Shell" + RESET);
        System.out.println(GRAY + "Redis: " + host + ":" + port + RESET);
        System.out.println(GRAY + "Type 'exit' to quit. Type 'help' for commands." + RESET);
        System.out.println();

        try (Scanner scanner = new Scanner(System.in);
                JedisPool pool = new JedisPool(host, port)) {

            while (true) {
                System.out.print(BOLD + GREEN + "fw> " + RESET);
                if (!scanner.hasNextLine())
                    break;

                String line = scanner.nextLine().trim();
                if (line.isEmpty())
                    continue;
                if (line.equals("exit") || line.equals("quit") || line.equals("q"))
                    break;
                if (line.equals("help") || line.equals("h")) {
                    printHelp();
                    continue;
                }

                String[] args = line.split("\\s+");
                try (Jedis jedis = pool.getResource()) {
                    handleCommand(jedis, args);
                } catch (Exception e) {
                    System.err.println(RED + "Error: " + e.getMessage() + RESET);
                }
            }
        }
        System.out.println(GRAY + "Goodbye!" + RESET);
    }

    private static void handleCommand(Jedis jedis, String[] args) throws Exception {
        String cmd = args[0].toLowerCase();
        switch (cmd) {
            case "status", "st" -> printStatus(jedis);
            case "queue", "qs" -> printQueueStats(jedis);
            case "dispatch", "dp" -> dispatchJob(jedis, args);
            case "result", "rs" -> getResult(jedis, args);
            case "watch", "w" -> watchContinuous(jedis);
            default -> {
                System.out.println(YELLOW + "Unknown command: " + cmd + RESET);
                printHelp();
            }
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    private static void printStatus(Jedis jedis) {
        Set<String> keys = jedis.keys("fw:workers:*");

        System.out.println(BOLD + CYAN + "── CLUSTER STATUS " + "─".repeat(48) + RESET);
        if (keys.isEmpty()) {
            System.out.println(YELLOW + "   No active workers found." + RESET);
            return;
        }

        System.out.printf(BOLD + "   %-22s %-12s %-12s %-20s%n" + RESET, "NODE", "UPTIME", "LAST BEAT", "CAPACITY");

        long now = System.currentTimeMillis();
        for (String key : keys) {
            Map<String, String> info = jedis.hgetAll(key);
            if (info == null || info.isEmpty())
                continue;

            long lastBeat = Long.parseLong(info.getOrDefault("lastBeat", "0"));
            long startedAt = Long.parseLong(info.getOrDefault("startedAt", String.valueOf(now)));
            long agoSec = (now - lastBeat) / 1000;
            long uptimeSec = (now - startedAt) / 1000;

            String status = agoSec < 15 ? GREEN + "●" + RESET : RED + "○" + RESET;
            String uptimeStr = formatDuration(uptimeSec);

            System.out.printf("   %s %-20s %-12s %-12s %s%n",
                    status,
                    info.getOrDefault("hostname", "?"),
                    uptimeStr,
                    agoSec + "s ago",
                    GRAY + info.getOrDefault("workers", "[]") + RESET);
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60)
            return seconds + "s";
        if (seconds < 3600)
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    // ── Queue Stats ───────────────────────────────────────────────────────────

    private static void printQueueStats(Jedis jedis) {
        System.out.println(BOLD + "── QUEUE STATS " + "─".repeat(51) + RESET);

        Set<String> queues = jedis.keys("fw:queue:*");
        if (queues.isEmpty()) {
            System.out.println(GRAY + "   No active queues found." + RESET);
        } else {
            System.out.printf(BOLD + "   %-25s %-10s%n" + RESET, "NAME", "PENDING");
            for (String key : queues) {
                long size = jedis.llen(key);
                String color = size == 0 ? GRAY : size > 10 ? RED : YELLOW;
                System.out.printf("   %-25s %s%d%s%n", key.replace("fw:queue:", ""), color, size, RESET);
            }
        }

        long scheduled = jedis.zcard("fw:scheduled_jobs");
        if (scheduled > 0) {
            System.out.println(YELLOW + "   ⏰ Scheduled: " + BOLD + scheduled + RESET);
        }

        long results = jedis.keys("fw:result:*").size();
        System.out.println(GRAY + "   Stored Results: " + results + RESET);
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private static void dispatchJob(Jedis jedis, String[] args) {
        if (args.length < 3) {
            System.err.println(RED + "Usage: dp <functionName> '<json-payload>'" + RESET);
            return;
        }

        String fnName = args[1];
        String payload = args[2];

        // Basit Job oluştur
        String jobId = UUID.randomUUID().toString();
        String replyKey = "fw:result:" + jobId;

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("id", jobId);
            job.put("functionName", fnName);
            job.put("payload", payload);
            job.put("replyKey", replyKey);
            job.put("createdAt", System.currentTimeMillis());
            job.put("retryCount", 0);
            job.put("maxRetry", 3);
            job.put("timeoutMs", 30000);
            job.put("origin", "cli");

            jedis.lpush("fw:queue:" + fnName, mapper.writeValueAsString(job));
            System.out.println(GREEN + "✓ Job dispatched!" + RESET);
            System.out.println("  Job ID : " + CYAN + jobId + RESET);
            System.out.println("  Fn     : " + fnName);
            System.out.println("  Result : rs " + jobId);
        } catch (Exception e) {
            System.err.println(RED + "Dispatch error: " + e.getMessage() + RESET);
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    private static void getResult(Jedis jedis, String[] args) {
        if (args.length < 2) {
            System.err.println(RED + "Usage: rs <jobId>" + RESET);
            return;
        }

        String key = "fw:result:" + args[1];
        String result = jedis.get(key);

        if (result == null) {
            System.out.println(YELLOW + "No result yet (job pending or expired)" + RESET);
        } else {
            System.out.println(GREEN + "Result:" + RESET);
            System.out.println(result);
        }
    }

    // ── Watch ─────────────────────────────────────────────────────────────────

    private static void watchContinuous(Jedis jedis) throws InterruptedException {
        System.out.print("\033[?25l"); // Hide cursor
        try {
            // Clear input buffer before starting
            while (System.in.available() > 0)
                System.in.read();

            while (!Thread.currentThread().isInterrupted()) {
                System.out.print("\033[H\033[J"); // Home + Clear
                System.out.println(BOLD + GREEN + "FUNCTION WORKER DASHBOARD " + RESET + GRAY + "["
                        + FMT.format(Instant.now()) + "]" + RESET);
                System.out.println();

                printStatus(jedis);
                System.out.println();
                printQueueStats(jedis);

                System.out.println();
                System.out.println(
                        BOLD + YELLOW + "» Press [ENTER] to stop watching and return to interactive shell" + RESET);

                // Check for input to exit
                if (System.in.available() > 0) {
                    // Consume any input
                    while (System.in.available() > 0)
                        System.in.read();
                    break;
                }

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            // Quiet exit
        } finally {
            System.out.print("\033[?25h"); // Show cursor
            System.out.print("\033[H\033[J"); // Clear screen one last time before returning
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private static void printHelp() {
        System.out.println(BOLD + "Available Commands:" + RESET);
        System.out.printf("  %s%-12s %s%s%n", CYAN, "st, status", RESET, "Show cluster nodes");
        System.out.printf("  %s%-12s %s%s%n", CYAN, "qs, queue", RESET, "Show queue metrics");
        System.out.printf("  %s%-12s %s%s%n", CYAN, "dp, dispatch", RESET, "Send a new job (dp <fn> <json>)");
        System.out.printf("  %s%-12s %s%s%n", CYAN, "rs, result", RESET, "Get job output (rs <jobId>)");
        System.out.printf("  %s%-12s %s%s%n", CYAN, "w, watch", RESET, "Live dynamic dashboard");
        System.out.printf("  %s%-12s %s%s%n", CYAN, "q, exit", RESET, "Exit the shell");
        System.out.println();
    }
}
