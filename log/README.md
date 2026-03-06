# FunctionWorker (Java Edition) 🚀

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.java.net/)
[![Redis](https://img.shields.io/badge/Redis-5.0%2B-red.svg)](https://redis.io/)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.seyfcover/worker-runtime)](https://central.sonatype.com/artifact/io.github.seyfcover/worker-runtime)
[![CI](https://github.com/seyfcover/FunctionWorker-Java/actions/workflows/ci.yml/badge.svg)](https://github.com/seyfcover/FunctionWorker-Java/actions/workflows/ci.yml)

FunctionWorker is a lightweight, **annotation-driven distributed job queue** and background worker runtime for Java, backed by Redis.

It is designed to offload background jobs, external API calls, and heavy computations from your main web application with **High Availability (HA)** and **Scheduling** support.

---

## 👥 Who is this for?

- **Spring Boot Developers:** Who want to offload `@Async` tasks to separate, scalable nodes.
- **Microservices Architects:** Who need a lightweight alternative to Kafka/RabbitMQ for simple job orchestration.
- **Startups:** Looking for a "Zero-Config" background worker system that runs on top of their existing Redis.
- **Enterprise Systems:** Requiring scheduled tasks, reliable job processing (no job loss), and real-time monitoring.

---

## 🎯 Use Cases

- **E-commerce:** Processing orders, generating invoices, or sending transactional emails.
- **SaaS:** Heavy reports generation, data exports, or scheduled cleanup tasks.
- **Notifications:** Sending push notifications or emails with a specific delay.
- **ETL:** Moving data between systems in chunks without blocking the UI.

---

## 🔥 Key Features

- **Annotation-Driven:** Define workers instantly with `@Worker(name="...", concurrency=4)`.
- **Scheduled Jobs:** Plan tasks to run in the future (e.g., "send an email in 1 hour").
- **Reliable Queues (HA):** Uses the `RPOPLPUSH` pattern. If a worker crashes mid-job, the task is **not lost**.
- **Real-time Dashboard:** Built-in web interface to monitor active nodes, queue sizes, and scheduled tasks.
- **Smart Scaling:** Horizontal scaling support. Add more worker nodes as load increases.
- **Resilience:** Automatic retries with exponential backoff, timeouts, and soft memory limits.
- **Zero Configuration:** Default settings work out-of-the-box with local Redis.

---

## 🛠️ Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.seyfcover</groupId>
    <artifactId>worker-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Note:** Requires Java 17+ and a running Redis instance (5.0+).

---

## 🚀 Usage

### 1. Define a Worker

Just annotate a method. The runtime handles the rest.

```java
public class EmailWorker {
    @Worker(name = "sendEmail", concurrency = 8, maxRetry = 5)
    public void send(EmailRequest req) {
        // Your logic here
    }
}
```

### 2. Start the Worker Runtime

To activate the workers when your server starts:

```java
public class WorkerApplication {
    public static void main(String[] args) {
        // Scans the package and starts all @Worker executors
        WorkerRuntime.start("com.myproject.workers");
    }
}
```

### 3. Configure the Dispatcher (API Gateway / Client Side)

To send jobs to the queue, configure a `JobDispatcher`:

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

        // Just return the dispatcher — no need to call runtime.start()!
        return runtime.getDispatcher();
    }
}
```

Inject and use it in your Controller:

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

### 4. Dispatching Jobs

#### A. Fire and Forget (Async)
Instantly returns a Job ID. Heavy lifting happens in the background.
```java
String jobId = dispatcher.dispatch("sendEmail", request);
```

#### B. Scheduled Jobs (Delayed)
Plan a job to run at a specific timestamp.
```java
long runAt = System.currentTimeMillis() + 60_000; // 1 minute later
dispatcher.dispatchAt("processOrder", request, runAt);
```

#### C. Synchronous Wait
Blocks the request until the background worker returns a result.
```java
OrderResult result = dispatcher.dispatchAndWait("processOrder", req, OrderResult.class, 30);
```

---

## 🖥️ Real-time Dashboard

FunctionWorker comes with a built-in dashboard (available at `http://localhost:8080` in the example app).

- **Active Nodes:** See all connected worker instances and their capacity.
- **Queue Status:** Real-time pending counts for every `@Worker` type.
- **Scheduled Jobs:** Monitor tasks waiting for their execution time.

---

## ⚖️ Scaling Strategy (K8s Ready)

FunctionWorker is designed for **Cloud Native** environments.

- **Horizontal Scaling:** Spin up 100s of Worker containers — they coordinate via Redis automatically.
- **Autoscaling (KEDA):** Integrate with Kubernetes KEDA to scale workers based on queue length. Your cluster grows dynamically under load and shrinks when finished.

---

## 🗺️ Roadmap

- [ ] Gradle support
- [ ] Spring Boot Auto-Configuration
- [ ] Dead Letter Queue (DLQ)
- [ ] Prometheus metrics endpoint
- [ ] Redis Cluster support
- [ ] More examples and integration guides

---

## 🤝 Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

MIT License. Created with ❤️ for high-performance Java systems.
