package com.fw.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fw.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled jobs (ZSET) -> Worker Queue mover.
 * Runs on every node, but Redis atomicity ensures tasks are moved only once.
 */
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final String QUEUE_PREFIX = WorkerExecutor.QUEUE_PREFIX;
    private final String SCHEDULED_KEY = JobDispatcher.SCHEDULED_KEY;

    public SchedulerService(JedisPool jedisPool, ObjectMapper mapper) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "fw-scheduler"));
    }

    public void start() {
        // Poll every 1 second
        scheduler.scheduleAtFixedRate(this::pollAndMove, 1, 1, TimeUnit.SECONDS);
        log.info("▶ SchedulerService started");
    }

    public void stop() {
        scheduler.shutdown();
        log.info("■ SchedulerService stopped");
    }

    private void pollAndMove() {
        try (Jedis jedis = jedisPool.getResource()) {
            long now = System.currentTimeMillis();

            // Score'u 0 ile 'now' arasında olan 10 işi getir
            java.util.List<String> readyJobs = jedis.zrangeByScore(SCHEDULED_KEY, 0, (double) now, 0, 10);

            for (String jobJson : readyJobs) {
                // ATOMIC MOVE: zrem returns 1 if successfully removed (only one node can win)
                if (jedis.zrem(SCHEDULED_KEY, jobJson) > 0) {
                    Job job = mapper.readValue(jobJson, Job.class);
                    String targetQueue = QUEUE_PREFIX + job.getFunctionName();

                    jedis.lpush(targetQueue, jobJson);
                    log.info("⬂ Scheduled job moved to queue: {} (id: {})", job.getFunctionName(), job.getId());
                }
            }
        } catch (Exception e) {
            log.error("Scheduler error", e);
        }
    }
}
