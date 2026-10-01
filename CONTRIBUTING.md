# Contributing to Music Mate

Thank you for your interest in contributing to Music Mate! This guide will help you set up your development environment and understand our build process.

## 🛠 Prerequisites

*   **JDK 17:** The project requires Java 17.
*   **Android Studio:** Latest stable version (Ladybug or higher recommended).
*   **Android SDK:** Target API 36 (Android 16).

## 🏗 Project Structure

Music Mate uses a modular architecture: the UPnP layer depends only on the `WebServer` SPI in `:core`, implemented by SonicNIO in `:server-jupnp`. Everything ships in **one APK**.

*   `:app` - The main Android application module.
*   `:core` - Shared business logic and interfaces (SPI: `UpnpServer`, `WebServer`, `MediaServerHub`).
*   `:server-jupnp` - Base DLNA/UPnP server + **SonicNIO**, the streaming HTTP engine.
*   `:library` - Internal UI and utility libraries.

## 🚀 Building the Project

There are **no Gradle flavors**. A single APK is built, with one streaming engine: SonicNIO (`NioWebServerImpl` in `:server-jupnp`, provided by `ServerModule`). There is no engine setting; Netty was retired in ADR-037.

### Command Line Build

```bash
# Build the app (single variant)
./gradlew assembleDebug

# Build and install on a connected device
./gradlew installDebug
```

## 🧪 Testing

We use JUnit and AndroidX Test for verification.

```bash
# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint
```

## 📝 Coding Standards

*   **Language:** Java 17 and Kotlin.
*   **Style:** Follow standard Android and Kotlin coding conventions.
*   **DI:** We use **Dagger Hilt** for dependency injection.
*   **Reactive:** **RxJava 3** is used for asynchronous operations.

## 🐛 Debugging the Streaming Server

SonicNIO logs through `System.out`, which appears in logcat under the `System.out` tag. `NioHttpServerTest`, `NioHttpServerSoakTest` and `NioHttpServerFuzzTest` reproduce most server issues on the JVM; `tools/bench/stream-bench.sh` measures a device over the network.

*Note: CoreHTTP (ADR-035) and Netty (ADR-037) were removed on 2026-10-01; a saved engine choice from an earlier version is deleted at startup.*

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.
