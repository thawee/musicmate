# Contributing to Music Mate

Thank you for your interest in contributing to Music Mate! This guide will help you set up your development environment and understand our build process.

## 🛠 Prerequisites

*   **JDK 17:** The project requires Java 17.
*   **Android Studio:** Latest stable version (Ladybug or higher recommended).
*   **Android SDK:** Target API 36 (Android 16).

## 🏗 Project Structure

Music Mate uses a modular architecture to support multiple pluggable server engines. All active engines ship in **one APK**; the engine is chosen at runtime, not at build time.

*   `:app` - The main Android application module.
*   `:core` - Shared business logic and interfaces (SPI: `UpnpServer`, `WebServer`, `MediaServerHub`).
*   `:server-jupnp` - Base DLNA/UPnP server + **SonicNIO** HTTP engine — **the default engine**.
*   `:server-jupnp-httpcore` - **CoreHTTP** engine (Apache HttpCore 5.5-beta3) — being retired.
*   `:server-jupnp-netty` - **Netty** engine (Netty 4.2.18).
*   `:library` - Internal UI and utility libraries.

## 🚀 Building the Project

There are **no Gradle flavors**. A single APK is built, and the active engine is read at runtime from the `preference_media_server_engine` preference via `CompositeWebServer`:

| Engine key | Server Engine | Module | Notes |
| :--- | :--- | :--- | :--- |
| `nio` | **SonicNIO** (Custom NIO Reactor) | `:server-jupnp` | **Default** — balanced, true zero-copy |
| `httpcore` | **CoreHTTP** (Apache HttpCore 5) | `:server-jupnp-httpcore` | Being retired — ultra-low memory |
| `netty` | **Netty 4.2** | `:server-jupnp-netty` | High throughput |

Users switch engines at runtime under **App Settings → Server Engine**. To change the default, edit `Constants.DEFAULT_SERVER_ENGINE`; every reader uses it.

### Command Line Build

```bash
# Build the app (single variant — all engines included)
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

## 🐛 Debugging Server Engines

If you encounter issues with a specific server engine, document them in a log file within the relevant module or create a new issue.

**Actively maintained engines** (`nio`, `httpcore`, `netty`) receive bug fixes and new features.

*Note: CoreHTTP needs a build-time bytecode patch (`patchHttpCore`, ADR-032) to run on Android ART.*

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.
