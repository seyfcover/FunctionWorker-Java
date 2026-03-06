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
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

/**
 * Example Application built with Spring Boot.
 * 
 * Showcases how cleanly `FunctionWorker` integrates into modern microservices.
 *
 * Execution Modes:
 * 1. combined (default) — Starts Web Server + Job Dispatcher + Job Workers
 * 2. dispatcher — Starts Web Server + Job Dispatcher (No Workers)
 * 3. worker — Starts ONLY Workers (Port 8080 can still be bound or skipped)
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        // Read the requested mode from command line arguments (e.g. java -jar
        // target.jar worker)
        String mode = args.length > 0 ? args[0] : "combined";

        SpringApplication app = new SpringApplication(Main.class);

        // Pass the mode down to FunctionWorkerConfig to dynamically start/stop
        // components
        app.setDefaultProperties(Collections.singletonMap("fw.mode", mode));

        // If the mode is strictly "worker", we can prevent Spring from booting Tomcat
        // to save memory if needed. For this example we just run it normally.
        if ("worker".equalsIgnoreCase(mode)) {
            // Uncomment to prevent web server startup on pure worker machines
            // app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        }

        app.run(args);
    }
}
