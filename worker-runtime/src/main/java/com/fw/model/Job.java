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
package com.fw.model;

import java.time.Instant;
import java.util.UUID;

/**
 * The core data transfer object representing a Job dispatched to the queue.
 * Safely serialized as a language-agnostic JSON payload.
 */
public class Job {

    /** Unique universally unique identifier for the job */
    private String id;

    /** Target worker function name */
    private String functionName;

    /** JSON serialized input argument payload */
    private String payload;

    /** The designated Redis key where the final outcome will be published */
    private String replyKey;

    /** Timestamp of job creation (epoch millis) */
    private long createdAt;

    /** Number of retries executed so far */
    private int retryCount;

    /** Maximum allowed retries (copied from @Worker annotation) */
    private int maxRetry;

    /** Allocated thread timeout in milliseconds */
    private long timeoutMs;

    /** The originating dispatcher's hostname */
    private String origin;

    /** Timestamp for scheduled execution (epoch millis) */
    private Long scheduledAt; // null if execution is immediate

    // ── Constructors ──────────────────────────────────────────────────────────

    public Job() {
    }

    public static Job create(String functionName, String payload, int maxRetry, long timeoutMs) {
        Job j = new Job();
        j.id = UUID.randomUUID().toString();
        j.functionName = functionName;
        j.payload = payload;
        j.replyKey = "fw:result:" + j.id;
        j.createdAt = Instant.now().toEpochMilli();
        j.retryCount = 0;
        j.maxRetry = maxRetry;
        j.timeoutMs = timeoutMs;
        try {
            j.origin = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            j.origin = "unknown";
        }
        return j;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getReplyKey() {
        return replyKey;
    }

    public void setReplyKey(String replyKey) {
        this.replyKey = replyKey;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Long getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Long scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    @Override
    public String toString() {
        return "Job{id='" + id + "', fn='" + functionName + "', retry=" + retryCount + "/" + maxRetry + "}";
    }
}
