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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * Worker'ın işi bitirdikten sonra Redis'e yazdığı sonuç paketi.
 */
public class JobResult {

    public enum Status {
        SUCCESS, FAILED, TIMEOUT, MEMORY_EXCEEDED
    }

    private String jobId;
    private Status status;

    /** Başarılı sonuç — JSON serileştirilmiş */
    private String result;

    /** Hata mesajı (status != SUCCESS ise dolu) */
    private String errorMessage;

    /** Stack trace (debug için) */
    private String stackTrace;

    /** İşin tamamlandığı epoch millis */
    private long completedAt;

    /** İşin kaç ms sürdüğü */
    private long durationMs;

    /** Hangi worker/makine işledi */
    private String processedBy;

    // ── Factory Methods ───────────────────────────────────────────────────────

    public static JobResult success(String jobId, String result, long durationMs, String worker) {
        JobResult r = new JobResult();
        r.jobId = jobId;
        r.status = Status.SUCCESS;
        r.result = result;
        r.completedAt = Instant.now().toEpochMilli();
        r.durationMs = durationMs;
        r.processedBy = worker;
        return r;
    }

    public static JobResult failed(String jobId, String errorMsg, String stackTrace, long durationMs, String worker) {
        JobResult r = new JobResult();
        r.jobId = jobId;
        r.status = Status.FAILED;
        r.errorMessage = errorMsg;
        r.stackTrace = stackTrace;
        r.completedAt = Instant.now().toEpochMilli();
        r.durationMs = durationMs;
        r.processedBy = worker;
        return r;
    }

    public static JobResult timeout(String jobId, long durationMs, String worker) {
        JobResult r = new JobResult();
        r.jobId = jobId;
        r.status = Status.TIMEOUT;
        r.errorMessage = "Job exceeded timeout of " + durationMs + "ms";
        r.completedAt = Instant.now().toEpochMilli();
        r.durationMs = durationMs;
        r.processedBy = worker;
        return r;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    @JsonIgnore
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
