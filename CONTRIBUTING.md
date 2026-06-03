# Contributing to Music Mate

Thank you for your interest in contributing to Music Mate! This guide will help you set up your development environment and understand our build process.

## 🛠 Prerequisites

*   **JDK 17:** The project requires Java 17.
*   **Android Studio:** Latest stable version (Ladybug or higher recommended).
*   **Android SDK:** Target API 36 (Android 16).

## 🏗 Project Structure

Music Mate uses a modular architecture to support multiple pluggable server engines:

*   `:app` - The main Android application module.
*   `:core` - Shared business logic and interfaces.
*   `:server-jupnp` - Base DLNA/UPnP server + **SonicNIO** HTTP engine (default).
*   `:server-jupnp-httpcore` - **CoreHTTP** engine (Apache HttpCore 5).
*   `:server-jupnp-netty` - **Netty** engine (Netty 4.2).
*   `:server-jupnp-jetty` - Jetty 12 engine — **archived, no further updates**.
*   `:server-jupnp-undertow` - Undertow 2.4 engine — **archived, no further updates**.
*   `:library` - Internal UI and utility libraries.

## 🚀 Building the Project

The project uses Gradle flavors to manage different server engine implementations. You can select a flavor in Android Studio via the **Build Variants** tab.

### ✅ Actively Maintained Flavors

| Flavor | Server Engine | Code Location | Notes |
| :--- | :--- | :--- | :--- |
| `nio` | **SonicNIO** (Custom NIO Reactor) | `app/src/nio/` | **Default — recommended** |
| `httpcore` | **CoreHTTP** (Apache HttpCore 5) | `app/src/httpcore/` | Ultra-low memory |
| `netty` | **Netty 4.2** | `app/src/netty/` | High throughput |

### 🗄 Archived Flavors *(build and run, but no longer updated)*

| Flavor | Server Engine | Notes |
| :--- | :--- | :--- |
| `jetty` | Jetty 12 | Archived |
| `undertow` | Undertow 2.4 | Archived |

### Command Line Build

```bash
# Build the default SonicNIO debug variant
./gradlew assembleNioDebug

# Build the CoreHTTP (low-memory) debug variant
./gradlew assembleHttpcoreDebug

# Build the Netty debug variant
./gradlew assembleNettyDebug

# Build all variants
./gradlew assembleDebug
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

**Archived engines** (`jetty`, `undertow`) are kept as-is. They build and run, but PRs targeting only archived engines will not be merged.

*Note: Some engines require reflection hacks or "shadowed" classes to work on the Android ART runtime.*

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.
