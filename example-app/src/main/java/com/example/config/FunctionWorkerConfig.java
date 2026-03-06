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
package com.example.config;

import com.fw.core.JobDispatcher;
import com.fw.core.WorkerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
public class FunctionWorkerConfig {

    private static final Logger log = LoggerFactory.getLogger(FunctionWorkerConfig.class);
    private WorkerRuntime runtime;

    @Bean
    public JobDispatcher jobDispatcher(
            @Value("${fw.mode:combined}") String mode,
            @Value("${redis.host:localhost}") String redisHost,
            @Value("${redis.port:6379}") int redisPort) {

        log.info("Initializing FunctionWorker Runtime in '{}' mode...", mode);

        runtime = WorkerRuntime.builder()
                .redisHost(redisHost)
                .redisPort(redisPort)
                .scanPackage("com.example.workers")
                .build();

        // If this node is supposed to process background jobs, start the executing
        // runtime
        if ("worker".equalsIgnoreCase(mode) || "combined".equalsIgnoreCase(mode)) {
            runtime.start();
        }

        // Always return the dispatcher so Controllers can fire commands
        return runtime.getDispatcher();
    }

    @Bean
    public com.fw.core.StatsService statsService() {
        return runtime.getStatsService();
    }

    @PreDestroy
    public void onShutdown() {
        if (runtime != null) {
            log.info("Spring Boot shutting down, stopping WorkerRuntime safely...");
            runtime.stop();
        }
    }
}
