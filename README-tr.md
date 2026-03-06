# FunctionWorker (Java Sürümü) 🚀

[![Lisans: MIT](https://img.shields.io/badge/Lisans-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.java.net/)
[![Redis](https://img.shields.io/badge/Redis-5.0%2B-red.svg)](https://redis.io/)

FunctionWorker, Java için destekleyici Redis tabanlı, hafif, **anotasyon güdümlü dağıtık iş kuyruğu** ve arka plan worker altyapısıdır.

Arka plan işlerini, dış API çağrılarını ve ağır hesaplamaları ana web uygulamanızdan ayırmak için **Yüksek Erişilebilirlik (HA)** ve **Zamanlanmış İşler (Scheduling)** desteğiyle tasarlanmıştır.

---

## 👥 Bu Sistem Kimler İçin?

- **Spring Boot Geliştiricileri:** `@Async` görevlerini daha ölçeklenebilir, bağımsız sunuculara taşımak isteyenler.
- **Mikroservis Mimarları:** Basit iş orkestrasyonu için Kafka veya RabbitMQ gibi ağır sistemlere hafif bir alternatif arayanlar.
- **Startuplar:** Hali hazırda kullandıkları Redis üzerinden çalışan, "Sıfır Konfigürasyonlu" bir worker sistemi arayanlar.
- **Kurumsal Sistemler:** Zamanlanmış görevlere, güvenli iş işlemeye (iş kaybı yaşanmayan) ve gerçek zamanlı izlemeye ihtiyaç duyanlar.

---

## 🎯 Kullanım Senaryoları

- **E-Ticaret:** Sipariş işleme, fatura oluşturma veya işlem e-postaları gönderme.
- **SaaS:** Ağır rapor oluşturma, veri dışa aktarma veya planlanmış temizlik görevleri.
- **Bildirimler:** Belirli bir gecikmeyle anlık bildirimler veya e-postalar gönderme.
- **ETL:** Verileri kullanıcıyı bloklamadan sistemler arasında parçalar halinde taşıma.

---

## 🔥 Temel Özellikler

- **Anotasyon Destekli:** İşçileri (Worker) anında `@Worker(name="...", concurrency=4)` ile tanımlayın.
- **Zamanlanmış İşler:** Görevleri gelecekte bir zamanda çalışacak şekilde planlayın (örneğin: "bu e-postayı 1 saat sonra gönder").
- **Güvenli Kuyruklar (HA):** `RPOPLPUSH` modelini kullanır. Bir worker işin ortasında çökerse, görev asla **kaybolmaz**.
- **Gerçek Zamanlı Dashboard:** Aktif düğümleri, kuyruk boyutlarını ve planlanmış görevleri izlemek için yerleşik web arayüzü.
- **Akıllı Ölçeklendirme:** Yatay ölçeklendirme desteği. Yük arttıkça daha fazla worker düğümü (Hydra-Processor) ekleyin.
- **Dayanıklılık:** Üstel geri çekilme ile otomatik yeniden deneme, zaman aşımları ve hafıza sınırları.
- **Sıfır Konfigürasyon:** Varsayılan ayarlar, yerel Redis ile kutudan çıktığı gibi çalışır.

---

## 🚀 Kullanım

### 1. Worker Tanımlama
Sadece bir metodu işaretleyin. Gerisini altyapı halleder.

```java
public class EmailWorker {
    @Worker(name = "sendEmail", concurrency = 8, maxRetry = 5)
    public void send(EmailRequest req) {
        // İş mantığınız buraya gelir
    }
}
```

### 2. İş Gönderme (API Gateway / İstemci)

#### A. Arka Planda İşleme (Async)
Anında bir İş ID'si döner. Ağır iş arka planda yapılır.
```java
String jobId = dispatcher.dispatch("sendEmail", request);
```

#### B. Zamanlanmış İşler (Delayed)
Bir işi gelecekteki bir zaman damgasında çalışacak şekilde planlayın.
```java
long runAt = System.currentTimeMillis() + 60_000; // 1 dakika sonra
dispatcher.dispatchAt("processOrder", request, runAt);
```

#### C. Senkron Bekleme
Arka plan işçisi sonucu dönene kadar HTTP isteğini bloklar.
```java
OrderResult result = dispatcher.dispatchAndWait("processOrder", req, OrderResult.class, 30);
```

---

## 🖥️ Gerçek Zamanlı Dashboard
FunctionWorker, yerleşik bir dashboard ile birlikte gelir (örnek uygulamamızda `http://localhost:8080` adresinde mevcuttur).

- **Aktif Düğümler:** Bağlı tüm worker örneklerini ve kapasitelerini görün.
- **Kuyruk Durumu:** Her `@Worker` tipi için gerçek zamanlı bekleyen iş sayıları.
- **Zamanlanmış İşler:** Çalışma vaktini bekleyen görevleri izleyin.

---

## 🛠️ Detaylı Kurulum ve Entegrasyon Rehberi

FunctionWorker'ı projenize dahil etmek oldukça basittir. İşte adım adım rehber:

### 1. Kütüphaneyi Sisteminize İndirin (Build & Install)
Henüz Maven Central'da olmadığı için, kaynak kodunu indirip yerel Maven deponuza (`.m2`) kurmanız gerekir:

```bash
# Repo'yu klonlayın
git clone https://github.com/seyfcover/functionworker-java.git
cd functionworker-java

# Runtime kütüphanesini derleyip kurun
mvn clean install -f worker-runtime/pom.xml
```

### 2. Projenize Bağımlılık Olarak Ekleyin
Kendi uygulamanızın (API Gateway veya Worker projesi) `pom.xml` dosyasına şu bağımlılığı ekleyin:

```xml
<dependency>
    <groupId>com.functionworker</groupId>
    <artifactId>worker-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. API Gateway / İstemci Tarafı Yapılandırması (Dispatcher)
İşleri kuyruğa atmak (Dispatch) için bir `JobDispatcher` yapılandırmanız gerekir. En kolay yol `WorkerRuntime` builder'ını kullanmaktır, ancak worker'ların aksine `.start()` metodunu **çağırmamanız** gerekir.

```java
@Configuration
public class FunctionWorkerConfig {
    @Bean
    public JobDispatcher jobDispatcher() {
        // Redis bağlantısını builder ile yapılandırın
        // Not: scanPackage şu anki sürümde builder tarafından zorunlu tutulmaktadır
        WorkerRuntime runtime = WorkerRuntime.builder()
                .redisHost("localhost")
                .redisPort(6379)
                .scanPackage("com.projem.workers") 
                .build();
                
        // Sadece dispatcher'ı dönün. runtime.start() çağırmanıza gerek yok!
        return runtime.getDispatcher();
    }
}
```

Ardından **Controller** içerisinde işleri şu şekilde tetikleyebilirsiniz:

```java
@RestController
public class OrderController {
    private final JobDispatcher dispatcher;

    public OrderController(JobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/siparis-ver")
    public String createOrder(@RequestBody OrderDTO order) {
        // 'processOrder' isimli worker metoduna işi gönderir (ASENKRON)
        return dispatcher.dispatch("processOrder", order);
    }
}
```

### 4. İşçi (Worker) Sunucusu Tarafında Yapılandırma
Worker düğümlerinde bir metodun iş çekmesini istiyorsanız, onu `@Worker` ile işaretlemeniz yeterlidir:

```java
public class MyOrdersWorker {
    
    @Worker(
        name = "processOrder",  // Dispatcher'ın kullandığı isimle aynı olmalı
        concurrency = 5,        // Aynı anda kaç thread bu işi yapsın?
        timeoutMs = 10000,      // İş 10 sn'den uzun sürerse iptal edilir
        maxRetry = 3            // Hata olursa kaç kere tekrar denensin?
    )
    public String process(OrderDTO order) {
        // Ağır hesaplamalar, fatura oluşturma, mail gönderme vs.
        return "Sipariş başarıyla işlendi: " + order.getId();
    }
}
```

### 5. Worker Runtime'ı Başlatma
Sunucu başladığında worker'ların aktif olması ve işleri Redis'ten çekmeye başlaması için Runtime'ı tetiklemek gerekir:

```java
public class WorkerApp {
    public static void main(String[] args) {
        // Belirlediğiniz paketi tarar ve içindeki @Worker'ları ayağa kaldırır
        WorkerRuntime.start("com.projem.workers");
    }
}
```

---

## ⚖️ Ölçeklendirme Stratejisi (K8s Hazır)
FunctionWorker, **Bulut Bilişim (Cloud Native)** ortamları için tasarlanmıştır.

- **Yatay Ölçeklendirme:** 100'lerce Worker konteyneri açabilirsiniz. Hepsi Redis üzerinden otomatik olarak koordineli çalışır.
- **Otomatik Ölçeklendirme (KEDA):** İşçileri **Kuyruk Uzunluğuna** göre ölçeklendirmek için Kubernetes KEDA ile entegre olun. 1 milyon iş gelirse, cluster'ınız dinamik olarak büyür ve iş bittiğinde küçülür.

## 📄 Lisans
MIT Lisansı. Yüksek performanslı Java sistemleri için ❤️ ile oluşturuldu.

