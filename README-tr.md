# FunctionWorker (Java Edition) 🚀

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.java.net/)
[![Redis](https://img.shields.io/badge/Redis-5.0%2B-red.svg)](https://redis.io/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.seyfcover/worker-runtime)](https://central.sonatype.com/artifact/io.github.seyfcover/worker-runtime)
[![CI](https://github.com/seyfcover/FunctionWorker-Java/actions/workflows/ci.yml/badge.svg)](https://github.com/seyfcover/FunctionWorker-Java/actions/workflows/ci.yml)

FunctionWorker, Redis tabanlı, **annotation-driven dağıtık iş kuyruğu** ve arka plan worker çalışma zamanıdır.

Ana uygulamanızdan arka plan işlerini, harici API çağrılarını ve ağır hesaplamaları **Yüksek Erişilebilirlik (HA)** ve **Zamanlama** desteğiyle offload etmek için tasarlanmıştır.

---

## 👥 Bu Kütüphane Kimin İçin?

- **Spring Boot Geliştiricileri:** `@Async` görevlerini ayrı, ölçeklenebilir node'lara taşımak isteyenler.
- **Mikroservis Mimarları:** Basit iş orkestrasyonu için Kafka/RabbitMQ'ya hafif bir alternatif arayanlar.
- **Girişimler:** Mevcut Redis altyapısı üzerinde çalışan "Sıfır Konfigürasyon" arka plan worker sistemi arayanlar.
- **Kurumsal Sistemler:** Zamanlanmış görevler, güvenilir iş işleme (iş kaybı yok) ve gerçek zamanlı izleme gerektirenler.

---

## 🎯 Kullanım Senaryoları

- **E-ticaret:** Sipariş işleme, fatura oluşturma veya işlemsel e-posta gönderimi.
- **SaaS:** Ağır rapor üretimi, veri dışa aktarımı veya zamanlanmış temizlik görevleri.
- **Bildirimler:** Belirli bir gecikmeyle push bildirimi veya e-posta gönderimi.
- **ETL:** Kullanıcı arayüzünü bloklamadan sistemler arasında parça parça veri taşıma.

---

## 🔥 Temel Özellikler

- **Annotation-Driven:** `@Worker(name="...", concurrency=4)` ile anında worker tanımlayın.
- **Zamanlanmış İşler:** Görevleri ileriye dönük bir zaman damgasıyla planlayın (örn. "1 saat sonra e-posta gönder").
- **Güvenilir Kuyruklar (HA):** `RPOPLPUSH` desenini kullanır. Worker çökmesi durumunda görev **kaybolmaz**.
- **Gerçek Zamanlı Dashboard:** Aktif node'ları, kuyruk boyutlarını ve zamanlanmış görevleri izlemek için yerleşik web arayüzü.
- **Akıllı Ölçekleme:** Yatay ölçekleme desteği. Yük arttıkça daha fazla worker node ekleyin.
- **Dayanıklılık:** Üstel geri çekilme, zaman aşımı ve yumuşak bellek limitleriyle otomatik yeniden deneme.
- **Sıfır Konfigürasyon:** Varsayılan ayarlar yerel Redis ile kutudan çıkar çıkmaz çalışır.

---

## 🛠️ Kurulum

`pom.xml` dosyanıza şu bağımlılığı ekleyin:

```xml
<dependency>
    <groupId>io.github.seyfcover</groupId>
    <artifactId>worker-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Not:** Java 17+ ve çalışan bir Redis instance'ı (5.0+) gerektirir.

---

## 🚀 Kullanım

### 1. Worker Tanımlama

Sadece bir metodu annotation ile işaretleyin. Çalışma zamanı gerisini halleder.

```java
public class EmailWorker {
    @Worker(name = "sendEmail", concurrency = 8, maxRetry = 5)
    public void send(EmailRequest req) {
        // İş mantığınız buraya
    }
}
```

### 2. Worker Runtime'ı Başlatma

Sunucunuz başladığında worker'ları aktif etmek için:

```java
public class WorkerApplication {
    public static void main(String[] args) {
        // Paketi tarar ve tüm @Worker executor'larını başlatır
        WorkerRuntime.start("com.myproject.workers");
    }
}
```

### 3. Dispatcher Yapılandırması (API Gateway / İstemci Tarafı)

Kuyruğa iş göndermek için bir `JobDispatcher` yapılandırın:

```java
@Configuration
public class FunctionWorkerConfig {
    @Bean
    public JobDispatcher jobDispatcher() {
        WorkerRuntime runtime = WorkerRuntime.builder()
                .redisHost("localhost")
                .redisPort(6379)
                .scanPackage("com.myproject.workers")
                .build();

        // Sadece dispatcher'ı döndürün — runtime.start() çağırmanıza gerek yok!
        return runtime.getDispatcher();
    }
}
```

Controller'ınızda inject edip kullanın:

```java
@RestController
public class MyController {
    private final JobDispatcher dispatcher;

    public MyController(JobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/trigger-job")
    public String trigger(@RequestBody MyData data) {
        return dispatcher.dispatch("myWorkerTask", data);
    }
}
```

### 4. İş Gönderme Yöntemleri

#### A. Fire and Forget (Asenkron)
Anında bir Job ID döndürür. Ağır işlemler arka planda gerçekleşir.
```java
String jobId = dispatcher.dispatch("sendEmail", request);
```

#### B. Zamanlanmış İşler (Gecikmeli)
Belirli bir zaman damgasında çalışacak iş planlayın.
```java
long runAt = System.currentTimeMillis() + 60_000; // 1 dakika sonra
dispatcher.dispatchAt("processOrder", request, runAt);
```

#### C. Senkron Bekleme
Arka plan worker sonuç döndürene kadar isteği bloklar.
```java
OrderResult result = dispatcher.dispatchAndWait("processOrder", req, OrderResult.class, 30);
```

---

## 🖥️ Gerçek Zamanlı Dashboard

FunctionWorker, yerleşik bir dashboard ile birlikte gelir (örnek uygulamada `http://localhost:8080` adresinde erişilebilir).

- **Aktif Node'lar:** Bağlı tüm worker instance'larını ve kapasitelerini görün.
- **Kuyruk Durumu:** Her `@Worker` türü için gerçek zamanlı bekleyen iş sayısı.
- **Zamanlanmış İşler:** Çalışma zamanını bekleyen görevleri izleyin.

---

## ⚖️ Ölçekleme Stratejisi (K8s Hazır)

FunctionWorker **Cloud Native** ortamlar için tasarlanmıştır.

- **Yatay Ölçekleme:** Yüzlerce Worker container ayağa kaldırın — hepsi Redis üzerinden otomatik koordine olur.
- **Otomatik Ölçekleme (KEDA):** Kubernetes KEDA ile entegre edin, kuyruk uzunluğuna göre worker'ları otomatik ölçeklendirin. Cluster yük altında büyür, iş bitince küçülür.

---

## 🗺️ Yol Haritası

- [ ] Gradle desteği
- [ ] Spring Boot Otomatik Konfigürasyon
- [ ] Dead Letter Queue (DLQ)
- [ ] Prometheus metrik endpoint'i
- [ ] Redis Cluster desteği
- [ ] Daha fazla örnek ve entegrasyon rehberi

---

## 🤝 Katkıda Bulunma

Katkılarınızı bekliyoruz! Lütfen önce neyi değiştirmek istediğinizi tartışmak için bir issue açın.

1. Repoyu fork'layın
2. Feature branch'inizi oluşturun (`git checkout -b feature/harika-ozellik`)
3. Değişikliklerinizi commit edin (`git commit -m 'Harika özellik eklendi'`)
4. Branch'i push edin (`git push origin feature/harika-ozellik`)
5. Pull Request açın

---

## 📄 Lisans

MIT Lisansı. Yüksek performanslı Java sistemleri için ❤️ ile geliştirildi.
