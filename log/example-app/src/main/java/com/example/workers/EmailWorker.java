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
 * Email gönderme worker'ı.
 * Farklı concurrency ve timeout ayarları ile bağımsız çalışır.
 */
public class EmailWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);

    @Worker(name = "sendEmail", concurrency = 8, // Email gönderme I/O bound — daha fazla concurrency
            timeout = 15_000, // 15s timeout
            maxRetry = 5, // Email retry — çok önemli
            description = "SMTP üzerinden email gönderimi")
    public EmailResult sendEmail(EmailRequest request) {
        log.info("Email gönderiliyor → {}", request.getTo());

        // Simüle edilmiş SMTP gönderimi
        if (request.getTo() == null || !request.getTo().contains("@")) {
            throw new IllegalArgumentException("Geçersiz email adresi: " + request.getTo());
        }

        // Gerçekte: JavaMailSender, AWS SES, SendGrid API vs.
        try {
            Thread.sleep(1000); // Demo için 1 saniye bekle
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String messageId = "MSG-" + System.nanoTime();
        log.info("Email gönderildi: {} → {}", messageId, request.getTo());

        return new EmailResult(messageId, "SENT", request.getTo());
    }

    @Worker(name = "sendBulkEmail", concurrency = 2, // Bulk — bandwidth sınırı
            timeout = 120_000, // 2 dakika
            maxRetry = 1)
    public BulkResult sendBulkEmail(BulkEmailRequest request) {
        log.info("Bulk email: {} alıcı", request.getRecipients().size());

        int sent = 0;
        for (String recipient : request.getRecipients()) {
            // Her birine gönder
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
            sent++;
        }

        return new BulkResult(sent, request.getRecipients().size());
    }

    // ── Model sınıfları ───────────────────────────────────────────────────────

    public static class EmailRequest {
        private String to;
        private String subject;
        private String body;
        private String templateId;

        public EmailRequest() {
        }

        public String getTo() {
            return to;
        }

        public void setTo(String v) {
            this.to = v;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String v) {
            this.subject = v;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String v) {
            this.body = v;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String v) {
            this.templateId = v;
        }
    }

    public record EmailResult(String messageId, String status, String recipient) {
    }

    public static class BulkEmailRequest {
        private java.util.List<String> recipients;
        private String subject;
        private String body;

        public BulkEmailRequest() {
        }

        public java.util.List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(java.util.List<String> v) {
            this.recipients = v;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String v) {
            this.subject = v;
        }
    }

    public record BulkResult(int sent, int total) {
    }
}
