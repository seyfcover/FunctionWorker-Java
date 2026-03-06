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
package com.fw.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a background Worker function.
 *
 * <pre>
 * {@literal @}Worker(
 *     name        = "processOrder",  // queue name (optional — defaults to method name)
 *     concurrency = 4,               // concurrent threads to allocate on this machine
 *     timeout     = 30_000,          // job timeout in milliseconds (InterruptedException)
 *     maxRetry    = 3,               // number of retries upon exponential failure backoff
 *     memoryMb    = 256              // soft memory limit in MB
 * )
 * public OrderResult processOrder(OrderRequest req) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Worker {

    /**
     * The unique queue name the worker will consume from.
     * If left blank, the exact method name will be used.
     *
     * @return Queue mapping name
     */
    String name() default "";

    /**
     * Maximum number of concurrent threads this worker will spawn on the host
     * machine.
     * Defaults to the total number of CPU cores available (-1).
     *
     * @return Number of concurrent threads
     */
    int concurrency() default -1;

    /**
     * Maximum execution time per job in milliseconds.
     * If the threshold is exceeded, the execution strand is fiercely interrupted
     * and marked as TIMEOUT.
     *
     * @return timeout duration in ms
     */
    long timeout() default 30_000;

    /**
     * Maximum exponential backoff retries before the job is placed into a dead
     * letter state.
     *
     * @return max retry limit
     */
    int maxRetry() default 3;

    /**
     * Soft JVM heap limit allocated per job. If exceeded, job is cancelled with
     * MEMORY_EXCEEDED.
     *
     * @return Memory allocation limit in MB
     */
    int memoryMb() default 512;

    /**
     * Short description of what this worker does, used for CLI listing and
     * Dashboard.
     *
     * @return Brief description
     */
    String description() default "";
}
