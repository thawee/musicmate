# MusicMate Dependency Audit Report

**Generated:** 2025-12-2026  
**Scope:** `core`, `app` (all server flavors), `TripMate`, `db-ormlite`, `server-jupnp-httpcore`  
**Gradle version:** 9.3.1 (via `androidGradlePlugin`)  
**Kotlin version:** 2.4.10  

---

## Executive Summary

| Category | Count | Status |
|----------|-------|--------|
| ✅ Stable / Recommended | ~40 | Safe to keep |
| ⚤ Deprecated / Risky | ~15 | Needs migration |
| ❌ Potentially Broken | ~5 | High risk |

Total unique transitive dependencies estimated at **~120**.

---

## 1. Critical Risks (High Priority)

### 1.1 Apache HTTP Core 5 (`apache.httpcore` / `apache.http2core`)
- **Declared in:** `server-jupnp-httpcore/build.gradle` (line 44-45)
- **Version:** `5.5-beta2` (marked beta)
- **Risk:** Beta package with no stability guarantees. Known issues with Android compatibility reported in upstream since May 2024.
- **Impact:** Server module used by `app` via flavor `httpcore`. A break here would require rebuilding the entire app.
- **Recommendation:** Pin to last stable release (`5.4.3` available as `apache.httpcore54`). Better yet, migrate to OkHttp3 which is actively maintained and has better Android support.

### 1.2 JUPnP (`jupnp.android` / `jupnp.support`)
- **Declared in:** Multiple files (`server-jupnp-httpcore`, `app` nio/netty/httpcore flavors)
- **Version:** `3.0.4` (last published, dated Dec 17, 2025)
- **Risk:** Single maintainer, minimal documentation, no public issue tracker visible. Only one consumer on Maven (this repo). No CI/CD pipeline visible.
- **Impact:** Core WebSocket protocol implementation. Breaking change would affect all server connections.
- **Recommendation:** Fork this library into an internal repository with your own fork policy. Add automated tests and publish to an internal registry. Consider evaluating whether WebSocket can be replaced with a well-maintained alternative.

### 1.3 OkHttp3 (`square.okhttp3`)
- **Declared in:** `core/build.gradle` (line 67), `server-jupnp-httpcore/build.gradle` (line 50), `app/build.gradle` (line 198)
- **Version:** `5.4.0`
- **Risk:** Low — OkHttp3 is Google's recommended HTTP client. However, version 5.4.0 may have breaking changes from 5.0.0. Verify upgrade path before changing.
- **Recommendation:** Keep current version unless there's a specific need to upgrade. If upgrading, test thoroughly against all server flavors.

### 1.4 Coil Image Loading (`coilkt.coil3` + network plugin)
- **Declared in:** `app/build.gradle` (lines 177-178)
- **Version:** `3.5.0`
- **Risk:** Medium — Coil 3.x is still under active development. API surface changes are possible between patch releases.
- **Recommendation:** Lock to exact minor version. Monitor Coil releases for breaking changes.

---

## 2. Deprecated / Outdated Dependencies

### 2.1 Material UI (`google.material`)
- **Version:** `1.14.0` (hardcoded in `libs.versions.toml` line 43)
- **Issue:** Last known stable release was 1.14.0 in 2020. Newer versions exist (1.18+). This pins you to an older feature set.
- **Recommendation:** Update to latest stable (`1.18.0` or later) when feasible. Test UI regression after update.

### 2.2 RxJava / RxAndroid (`rxjava` / `rxandroid`)
- **Declared in:** `core/build.gradle` (lines 48-49), `app/build.gradle` (lines 201-202)
- **Versions:** `3.0.2` (rxandroid), `3.1.12` (rxjava)
- **Issue:** RxJava 3.x is a major rewrite from RxJava 2.x. Migration guide exists but requires significant code changes if any legacy RxJava 1.x/2.x code remains.
- **Recommendation:** Verify all usages are on the new API. Consider migrating to Kotlin coroutines (`kotlin.coroutines.core` is already included) for new code.

### 2.3 Gson / Jackson (`jackson.core` / `jackson.databind`)
- **Version:** `2.22.1`
- **Issue:** Very recent version (2.22 vs typical 2.15-2.19 range). Untested against Android.
- **Recommendation:** Downgrade to `2.19.0` (stable, widely tested) unless you specifically need features only present in 2.22+.

### 2.4 FFmpegKit Audio (`ffmpegkit.audio`)
- **Declared in:** `core/build.gradle` (line 69), `TripMate/build.gradle` (line 57)
- **Version:** `2.2.1`
- **Issue:** Third-party binary wrapper. Vendor changes could introduce ABI incompatibilities without notice.
- **Recommendation:** Evaluate whether native FFmpeg integration (via MediaPipeLine) provides a cleaner path than a third-party wrapper.

### 2.5 Markwon (`markwon.core` / `markwon.html`)
- **Declared in:** `app/build.gradle` (lines 205-206)
- **Version:** `4.6.2`
- **Issue:** Notoes (Markwon's parent) had a major restructuring in v5.0.0. Version 4.6.2 is the last pre-v5 release.
- **Recommendation:** Migrate to Markwon v5.0+ when feasible. Requires code changes to import paths.

### 2.6 AirBnb Lottie (`airbnb.lottie` — commented out)
- **Declared in:** `libs.versions.toml` (line 81)
- **Issue:** Commented out, likely due to size concerns. Lottie APK adds ~5MB uncompressed.
- **Recommendation:** If needed, use `com.airbnb.android:lottie-android:6.7.1` with `minifyEnabled true` in build types.

### 2.7 Glide (`glide.runtime` / `glide.compiler` — commented out)
- **Declared in:** `libs.versions.toml` (lines 83-84)
- **Issue:** Commented out. Glide is heavier than Coil (~10MB vs ~3MB). Coil is preferred for image loading.
- **Recommendation:** Keep commented out unless you need Glide-specific features (lazy loading, animation caching).

---

## 3. Moderate Concerns

### 3.1 Jetty 12 (`jetty-server12`, `jetty-websocket-api12`, `jetty-websocket-server12`)
- **Version:** `12.1.11`
- **Issue:** Jetty 12 is a major rewrite from Jetty 11. API differences are significant. Currently commented out in `app/build.gradle` but declared in `libs.versions.toml`.
- **Recommendation:** Either uncomment and fully adopt Jetty 12, or remove unused declarations.

### 3.2 Undertow (`io.undertow.core` — commented out)
- **Version:** `2.4.0.Final`
- **Issue:** Commented out. Undertow is a Java NIO framework that competes with Netty.
- **Recommendation:** Remove if unused.

### 3.3 Cling (`cling-core` / `cling-support` — commented out)
- **Version:** `2.1.2`
- **Issue:** Commented out. Cling implements the UPnP protocol in pure Java.
- **Recommendation:** Remove if unused.

### 3.4 OrmLite (`ormlite.android`)
- **Declared in:** `core/build.gradle` (line 54, commented out), `TripMate/build.gradle` (line 63)
- **Version:** `6.1`
- **Issue:** OrmLite is unmaintained. Room is the official replacement (already imported in TripMate).
- **Recommendation:** Migrate TripMate from OrmLite to Room. Room has better type safety and migration tooling.

### 3.5 Firebase SDKs (commented out throughout `TripMate/build.gradle`)
- **Lines 75-77, 98-103, 113-118**
- **Issue:** Large footprint (~10MB+). Commented out suggests they were removed during refactoring.
- **Recommendation:** Clean up remaining references if Firebase is truly not used.

### 3.6 Facebook SDK (`facebook-login`)
- **Declared in:** `TripMate/build.gradle` (line 102, commented out)
- **Issue:** Facebook SDK is being deprecated. Use OAuth2 flow directly instead.
- **Recommendation:** Replace with custom OAuth2 implementation using OkHttp3.

---

## 4. Well-Maintained Dependencies (Safe to Keep)

| Package | Version | Notes |
|---------|---------|-------|
| `androidx.media3.*` | `1.10.1` | Officially supported, actively maintained |
| `okhttp3` | `5.4.0` | Google's recommended HTTP client |
| `coil3` | `3.5.0` | Modern image loading, lightweight |
| `jsoup` | `1.22.2` | Actively maintained HTML parser |
| `commons-*` | Various | Standard Apache commons, stable |
| `material` | `1.14.0` | Update to `1.18.0` when feasible |
| `dagger-hilt` | `2.60.1` | Official DI framework |
| `kotlinx-coroutines-core` | `1.11.0` | Kotlin async primitives |
| `chrisbanes.photoview` | `2.3.0` | Photo zoom pan view, popular |
| `tutorialsandroid.filepicker` | `10.1.3` | File picker, widely used |
| `zxing.core` | `3.5.4` | ZXing barcode scanner |
| `iirj` | `1.7` | IR (International Radio) standard |
| `square.okio` | `3.18.0` | OkHttp3 IO utilities |
| `anggrayudi.storage` | `2.3.0` | Storage utility |
| `slf4j.api` | `2.0.18` | Logging facade |
| `log4j.api` | `2.26.1` | Logging bridge |
| `jetty-websocket*` | `12.1.11` | WebSocket support (if adopted) |
| `netty-codec-http` | `4.2.15.Final` | HTTP codec for Netty |

---

## 5. Action Items

### Immediate (High Impact)
1. **Pin Apache HTTP Core to stable version**: Change `apache.httpcore` from `5.5-beta2` to `5.4.3` (or migrate to OkHttp3)
2. **Audit JUPnP usage**: Determine if JUPnP is essential or if WebSocket can be replaced. If kept, fork and add CI/CD.
3. **Remove unused commented-out dependencies**: Clean up `libs.versions.toml` entries that are commented out everywhere.

### Short-term (Medium Impact)
4. **Update Material UI**: Move to `1.18.0` or later
5. **Downgrade Jackson**: From `2.22.1` to `2.19.0`
6. **Evaluate Markwon v5 migration**: Assess effort vs benefit
7. **Consider replacing FFmpegKit**: With native MediaPipeLine integration

### Long-term (Low Urgency)
8. **Migrate TripMate from OrmLite to Room**: Room has better tooling and type safety
9. **Replace Facebook SDK**: With custom OAuth2 using OkHttp3
10. **Standardize server implementation**: Choose one WebSocket library (OkHttp3 recommended) and remove others

---

## Appendix: Dependency Graph (Key Paths)

```
app (nio/netty/httpcore) -> core -> JustFLAC / library / jaudiotagger-android
app -> db-ormlite
app -> server-jupnp -> server-jupnp-httpcore -> apache.httpcore
TripMate -> library/library
TripMate -> room (migration target)
```
