# FunctionWorker (Java Edition) 🚀

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.java.net/)
[![Redis](https://img.shields.io/badge/Redis-5.0%2B-red.svg)](https://redis.io/)

FunctionWorker is a lightweight, **annotation-driven distributed job queue** and background worker runtime for Java, backed by Redis. 

It is designed to offload background jobs, external API calls, and heavy computations from your main web application with **High Availability (HA)** and **Scheduling** support.

---

## � Who is this for?

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

## �🔥 Key Features

- **Annotation-Driven:** Define workers instantly with `@Worker(name="...", concurrency=4)`.
- **Scheduled Jobs:** Plan tasks to run in the future (e.g., "send an email in 1 hour").
- **Reliable Queues (HA):** Uses the `RPOPLPUSH` pattern. If a worker crashes mid-job, the task is **not lost**.
- **Real-time Dashboard:** Built-in web interface to monitor active nodes, queue sizes, and scheduled tasks.
- **Smart Scaling:** Horizontal scaling support. Add more worker nodes (Hydra-Processors) as load increases.
- **Resilience:** Automatic retries with exponential backoff, timeouts, and soft memory limits.
- **Zero Configuration:** Default settings work out-of-the-box with local Redis.

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

### 2. Dispatching Jobs (API Gateway / Client)

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
FunctionWorker comes with a built-in dashboard (available at `http://localhost:8080` in our example app).

- **Active Nodes:** See all connected worker instances and their capacity.
- **Queue Status:** Real-time pending counts for every `@Worker` type.
- **Scheduled Jobs:** Monitor tasks waiting for their execution time.

---

## 🛠️ Step-by-Step Integration Guide

Integrating FunctionWorker into your project is straightforward. Follow these steps:

### 1. Build & Install the Library
Since it's not yet on Maven Central, you need to build the source and install it to your local Maven repository (`.m2`):

```bash
# Clone the repository
git clone https://github.com/seyfcover/functionworker-java.git
cd functionworker-java

# Build and install the core runtime
mvn clean install -f worker-runtime/pom.xml
```

### 2. Add Dependency to Your Project
Add the following dependency to your application's (API Gateway or Worker project) `pom.xml`:

```xml
<dependency>
    <groupId>com.functionworker</groupId>
    <artifactId>worker-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. API Gateway / Client Side Configuration (Dispatcher)
To send jobs to the queue, you need to configure a `JobDispatcher`. The easiest way is to use the `WorkerRuntime` builder but **not** call `.start()` (which is for workers).

```java
@Configuration
public class FunctionWorkerConfig {
    @Bean
    public JobDispatcher jobDispatcher() {
        // Use the builder to configure connection
        // Note: scanPackage is currently required by the builder
        WorkerRuntime runtime = WorkerRuntime.builder()
                .redisHost("localhost")
                .redisPort(6379)
                .scanPackage("com.myproject.workers") 
                .build();
                
        // Just return the dispatcher. No need to call runtime.start()!
        return runtime.getDispatcher();
    }
}
```

Now, you can inject and use it in your **Controller**:

```java
@RestController
public class MyController {
    private final JobDispatcher dispatcher;

    public MyController(JobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/trigger-job")
    public String trigger(@RequestBody MyData data) {
        // Dispatches the job to the "myWorkerTask" queue (ASYNC)
        return dispatcher.dispatch("myWorkerTask", data);
    }
}
```

### 4. Define Your Background Worker (Server Side)
Simply mark a method with `@Worker` on your worker nodes:

```java
public class MyServiceWorker {
    
    @Worker(
        name = "myWorkerTask",   // Must match the name used by the dispatcher
        concurrency = 4,        // Number of concurrent threads for this task
        timeoutMs = 15000,      // Max execution time before cancellation
        maxRetry = 3            // Retry attempts on failure
    )
    public void process(MyData data) {
        // Perform heavy tasks: API calls, batch processing, emails, etc.
        System.out.println("Processing data: " + data.getId());
    }
}
```

### 5. Start the Worker Runtime
To activate the workers when your server starts, trigger the `WorkerRuntime`:

```java
public class WorkerApplication {
    public static void main(String[] args) {
        // Scans the package and starts all @Worker executors
        WorkerRuntime.start("com.myproject.workers");
    }
}
```

---

## ⚖️ Scaling Strategy (K8s Ready)
FunctionWorker is designed for **Cloud Native** environments. 

- **Horizontal Scaling:** You can spin up 100s of Worker containers. They will all coordinate via Redis automatically.
- **Autoscaling (KEDA):** Integrate with Kubernetes KEDA to scale workers based on **Queue Length**. If 1 million jobs arrive, your cluster can grow dynamically and shrink when finished.

## 📄 License
MIT License. Created with ❤️ for high-performance Java systems.


