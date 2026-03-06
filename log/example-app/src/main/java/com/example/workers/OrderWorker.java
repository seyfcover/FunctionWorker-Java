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
package com.example.workers;

import com.fw.annotation.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sipariş işleme worker'ı.
 *
 * Geliştirici sadece şunu yapar:
 * 1. Sınıfı yaz
 * 2. @Worker ekle
 * 3. Runtime'ı başlat
 *
 * Gerisi otomatik: queue keşfi, concurrency, retry, timeout.
 */
public class OrderWorker {

    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class);

    /**
     * Sipariş işleme — 4 paralel thread, 30s timeout, 3 retry
     *
     * Input : OrderRequest (JSON'dan deserialize edilir)
     * Output: OrderResult (JSON'a serialize edilir)
     */
    @Worker(name = "processOrder", concurrency = 4, timeout = 30_000, maxRetry = 3, memoryMb = 256, description = "Sipariş doğrulama, stok kontrolü ve ödeme işlemi")
    public OrderResult processOrder(OrderRequest request) {
        log.info("Sipariş işleniyor: {}", request.getOrderId());

        // ── İş mantığı burada — tamamen stateless ────────────────────────────

        // 1. Sipariş doğrulama
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Sipariş boş olamaz");
        }

        // 2. Fiyat hesaplama (simüle)
        double total = request.getItems().stream()
                .mapToDouble(item -> item.price() * item.quantity())
                .sum();

        // 3. Stok kontrolü & ödeme (gerçekte external API çağrısı olur)
        simulateWork(1000); // 1 saniye bekle - Hem hızlı hem dashboard'da izlenebilir.

        // 4. Sonuç dön
        return new OrderResult(
                request.getOrderId(),
                "CONFIRMED",
                total,
                "ORD-" + System.currentTimeMillis());
    }

    /**
     * Sipariş iptali — ayrı bir worker tipi
     */
    @Worker(name = "cancelOrder", concurrency = 2, timeout = 10_000, maxRetry = 1)
    public OrderResult cancelOrder(OrderRequest request) {
        log.info("Sipariş iptal: {}", request.getOrderId());
        simulateWork(1000); // Demo için 1 saniye bekle
        return new OrderResult(request.getOrderId(), "CANCELLED", 0, null);
    }

    private void simulateWork(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Model sınıfları ───────────────────────────────────────────────────────

    public static class OrderRequest {
        private String orderId;
        private String customerId;
        private java.util.List<OrderItem> items;

        public OrderRequest() {
        }

        public OrderRequest(String orderId, String customerId, java.util.List<OrderItem> items) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.items = items;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String v) {
            this.orderId = v;
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String v) {
            this.customerId = v;
        }

        public java.util.List<OrderItem> getItems() {
            return items;
        }

        public void setItems(java.util.List<OrderItem> v) {
            this.items = v;
        }
    }

    public record OrderItem(String productId, double price, int quantity) {
    }

    public static class OrderResult {
        private String orderId;
        private String status;
        private double total;
        private String confirmationCode;

        public OrderResult() {
        }

        public OrderResult(String orderId, String status, double total, String code) {
            this.orderId = orderId;
            this.status = status;
            this.total = total;
            this.confirmationCode = code;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String v) {
            this.orderId = v;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String v) {
            this.status = v;
        }

        public double getTotal() {
            return total;
        }

        public void setTotal(double v) {
            this.total = v;
        }

        public String getConfirmationCode() {
            return confirmationCode;
        }

        public void setConfirmationCode(String v) {
            this.confirmationCode = v;
        }
    }
}
