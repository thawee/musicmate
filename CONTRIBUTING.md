# Contributing to Music Mate

Thank you for your interest in contributing to Music Mate! This guide will help you set up your development environment and understand our build process.

## 🛠 Prerequisites

*   **JDK 17:** The project requires Java 17.
*   **Android Studio:** Latest stable version (Ladybug or higher recommended).
*   **Android SDK:** Minimum API 34 (Android 14), target API 37.

## 🏗 Project Structure

Music Mate uses a modular architecture: the UPnP layer depends only on the `WebServer` SPI in `:core`, implemented by SonicNIO in `:server-jupnp`. Everything ships in **one APK**.

*   `:app` - The main Android application module.
*   `:core` - Shared business logic and interfaces (SPI: `UpnpServer`, `WebServer`, `MediaServerHub`).
*   `:server-jupnp` - Base DLNA/UPnP server + **SonicNIO**, the streaming HTTP engine.
*   `:library` - Internal UI and utility libraries.

## 🚀 Building the Project

There are two product flavors (dimension `distribution`), with one streaming engine: SonicNIO (`NioWebServerImpl` in `:server-jupnp`, provided by `ServerModule`). There is no engine setting; Netty was retired in ADR-037.

*   `github` - The APK published on GitHub releases. Requests all-files access (`MANAGE_EXTERNAL_STORAGE`, in `app/src/github/AndroidManifest.xml`) and works on the library by path.
*   `play` - The Google Play build. No all-files access: it reads music with `READ_MEDIA_AUDIO` and changes files only in folders the user grants in the system folder picker (`AndroidMusicFileAccess`). `StorageMode` reads which mode applies from the manifest.

### Command Line Build

```bash
# Build the app
./gradlew assembleGithubDebug
./gradlew assemblePlayDebug

# Build and install on a connected device
./gradlew installGithubDebug
```

## 🧪 Testing

We use JUnit and AndroidX Test for verification.

```bash
# Run unit tests (all modules, both flavors)
./gradlew test

# Screenshot tests (references in app/src/screenshotTestGithubDebug)
./gradlew :app:validateGithubDebugScreenshotTest

# Run lint checks
./gradlew lint
```

## 📝 Coding Standards

*   **Language:** Java 17 and Kotlin.
*   **Style:** Follow standard Android and Kotlin coding conventions.
*   **DI:** We use **Dagger Hilt** for dependency injection.
*   **Reactive:** **RxJava 3** is used for asynchronous operations.

## 🐛 Debugging the Streaming Server

SonicNIO logs through `java.util.logging`, which appears in logcat under the `NioHttpServer` tag (errors as warnings with the stack trace; per-connection idle closes only at `FINE`). The Music Center Server tab shows live counters from `NioHttpServer.getStats()`: active streams, throughput, requests, and any evicted, refused or rate-limited requests. `NioHttpServerTest`, `NioHttpServerSoakTest` and `NioHttpServerFuzzTest` reproduce most server issues on the JVM; `tools/bench/stream-bench.sh` measures a device over the network.

*Note: CoreHTTP (ADR-035) and Netty (ADR-037) were removed on 2026-10-01; a saved engine choice from an earlier version is deleted at startup.*

---

## 📄 License

By contributing, you agree that your contributions will be licensed under the **Apache License, Version 2.0**.
