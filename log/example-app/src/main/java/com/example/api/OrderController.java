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
package com.example.api;

import com.example.workers.EmailWorker.BulkEmailRequest;
import com.example.workers.EmailWorker.EmailRequest;
import com.example.workers.OrderWorker.OrderItem;
import com.example.workers.OrderWorker.OrderRequest;
import com.example.workers.OrderWorker.OrderResult;
import com.fw.core.JobDispatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final JobDispatcher dispatcher;

    // Dispatcher automatically injected by FunctionWorkerConfig
    public OrderController(JobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * FIRE AND FORGET APPROACH
     * Takes the order, pushes it to Redis background queue, and instantly responds.
     */
    @PostMapping("/async")
    public ResponseEntity<Map<String, String>> createOrderAsync(@RequestBody OrderRequestDTO req) {

        OrderRequest internalReq = new OrderRequest(
                UUID.randomUUID().toString(),
                req.customerId(),
                List.of(new OrderItem(req.product(), req.price(), req.quantity())));

        // Fire and Forget
        String jobId = dispatcher.dispatch("processOrder", internalReq);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Order received successfully and will be processed in the background.",
                "jobId", jobId));
    }

    /**
     * DELAYED APPROACH
     * Schedules the work to be executed 10 seconds in the future.
     */
    @PostMapping("/async-delayed")
    public ResponseEntity<Map<String, String>> createOrderDelayed(@RequestBody OrderRequestDTO req) {

        OrderRequest internalReq = new OrderRequest(
                UUID.randomUUID().toString(),
                req.customerId(),
                List.of(new OrderItem(req.product(), req.price(), req.quantity())));

        // Schedule for +10 seconds
        long runAt = System.currentTimeMillis() + 10_000;
        String jobId = dispatcher.dispatchAt("processOrder", internalReq, runAt);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Order scheduled for +10 seconds.",
                "jobId", jobId,
                "scheduledAt", String.valueOf(runAt)));
    }

    /**
     * SYNCHRONOUS WAIT APPROACH
     * Pushes to queue, waits up to 20 seconds for a worker node to complete it,
     * then returns result.
     */
    @PostMapping("/sync")
    public ResponseEntity<?> createOrderSync(@RequestBody OrderRequestDTO req) {

        OrderRequest internalReq = new OrderRequest(
                UUID.randomUUID().toString(),
                req.customerId(),
                List.of(new OrderItem(req.product(), req.price(), req.quantity())));

        try {
            // Blocking Wait (Throws TimeoutException after 20 seconds)
            OrderResult result = dispatcher.dispatchAndWait(
                    "processOrder",
                    internalReq,
                    OrderResult.class,
                    20);
            return ResponseEntity.ok(result);
        } catch (java.util.concurrent.TimeoutException e) {
            return ResponseEntity.status(408).body(Map.of("error", "Worker response timeout"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(@RequestBody OrderRequest req) {
        String jobId = dispatcher.dispatch("cancelOrder", req);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "Cancellation queued"));
    }

    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailRequest req) {
        String jobId = dispatcher.dispatch("sendEmail", req);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "Email queued"));
    }

    @PostMapping("/email/bulk")
    public ResponseEntity<Map<String, String>> sendBulkEmail(@RequestBody BulkEmailRequest req) {
        String jobId = dispatcher.dispatch("sendBulkEmail", req);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "Bulk email queued"));
    }

    // A simple DTO wrapper
    public record OrderRequestDTO(String customerId, String product, double price, int quantity) {
    }
}
