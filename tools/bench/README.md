# Streaming benchmarks

## Streaming FLAC metadata skipping

`run-metadata-skip-comparison.py FRESH_SCRATCH PRESERVED_DECODER` compares explicitly preserved baseline/current metadata paths with STREAMINFO-only and 1 MiB padding fixtures, verified decoder origins and JVM allocation counters. It refuses to overwrite evidence. `run-metadata-skip-phone.py SCRATCH ADB SERIAL initial|confirmation` compares before.apk/after.apk using the separate opt-in Android benchmark; both APKs must retain database version 3. Initial runs include idle/unindexed/indexed work; confirmation reverses APK order with idle phases. The runner restores the candidate. `analyze-metadata-skip-phone.py SCRATCH` verifies raw records and exports every phase and pooled distribution. [Implementation, tests, methods, all samples and limits](../../tasks/performance/sonicnio-metadata-skip-2026-10-03/REPORT.md).

## Idle converted-seek attribution

`trace-idle-phone.py SCRATCH ADB SERIAL pilot|compare|confirm` records opt-in device-local workloads against a pinned installed server APK, using `PathIndexPhoneBenchmark` with `idleOnly=true` and `clientTrace=true`. It requires the private asset/test APK setup from the path-index experiment and a Perfetto `profile.pbtxt` in scratch. Comparison order is control/trace/trace/control; confirmation records trace/control and thread inventories. It restores the server activity and checks APK identity.

`analyze-idle-trace.py SCRATCH PROCESSOR MODE` maps device nanoTime intervals using MONOTONIC snapshots, verifies client trace containment and exports thread-state/frequency data. Missing worker ownership is explicitly incomplete until validated thread inventories are available. Decompress archived traces into scratch before analysis. [Concepts, every run, stack evidence, controls and limitations](../../tasks/performance/sonicnio-idle-seek-2026-10-03/REPORT.md).

## Request parser comparison

`run-parser-comparison.py SCRATCH compile|micro|staged|throughput` compares preserved baseline, copy-only and final sources. Save NioHttpServer.java and BoundedByteArrayOutputStream.java under `SCRATCH/baseline` and `SCRATCH/copy-only` before finalizing, export the Gradle classpath, and compile first. Fully buffered and staged extraction are separate models; staged uses an 8 KiB first read. Every micro sample checks its loaded class origin. `check-phone-framing.py` runs read-only protocol probes against the final installed APK via port 19000. [Detailed methods, compatibility and results](../../tasks/performance/sonicnio-parser-2026-10-03/REPORT.md).

`stream-bench.sh PHONE_IP TRACK_ID LABEL` measures a real phone over LAN. Use a real track ID from `/music/ID/file`. Its engine-setting comment predates Netty removal; the current application has only SonicNIO. Its MB/s output uses binary units, and it does not independently validate audio hashes or provide a comprehensive execution deadline. See [PERFORMANCE.md](../../PERFORMANCE.md) for interpretation limits.

## Standalone throughput experiment

`ThroughputBenchmark.java` measures host-loopback behavior with deterministic 16 MiB content. It compares one original-file connection, four original-file connections plus range traffic, and four generated bodies plus range traffic. Generated bodies are synthetic 4 KiB producer writes, not FLAC/ALAC decoding or actual WAV framing. Every completed full body is checked by SHA-256, and every 64 KiB range by exact bytes. Active connections, stream slots and generated-buffer reservations must drain to zero.

Each mode has one unreported two-second warmup and three reported two-second load periods. Clients complete their last transfer after the period ends. Persistent connections remove connection setup from subsequent full-file requests. A fifth connection sends ranges during the four-client cases, pausing ten milliseconds between requests. Range TTFB is measured at the first response-header byte; its p95 uses sorted samples at zero-based index floor(N × 0.95). The reported rate excludes range bytes and includes host client hashing and allocation overhead. This is an end-to-end same-JVM experiment, not an isolated socket capacity or server CPU benchmark.

Build/export the runtime classpath:

```sh
./gradlew -I tools/bench/test-classpath.gradle :core:writeStreamingBenchmarkClasspath
```

Compile and run, using a scratch output directory:

```sh
mkdir -p /private/tmp/musicmate-throughput-bench
javac -cp "$(cat build/bench/core-test-classpath.txt)" -d /private/tmp/musicmate-throughput-bench tools/bench/ThroughputBenchmark.java
java -cp "/private/tmp/musicmate-throughput-bench:$(cat build/bench/core-test-classpath.txt)" apincer.music.core.http.ThroughputBenchmark fixed-batch 262144 true
```

Arguments: label, solo file-write budget in bytes, batching boolean, optional mode (`file-single`, `file-four` or `pcm-four`). Shared file turns stay at 256 KiB. Solo budgets of 262144, 524288 and 1048576 permit fixed, 512 KiB and 1 MiB comparisons. A zero budget skips new configuration methods and allows the harness to run against the earlier implementation. Larger turns apply only when the server has exactly one connection and one selected key; the single-file mode tests this condition, whereas the contention modes normally use the shared allowance. Use false batching to isolate its effect.

Capture stdout JSON lines and stderr diagnostics separately. Abort on any nonzero process exit, invalid body or resource leak; partial results from a failed process are not a successful configuration. Report all samples and tail latencies, including regressions. A synthetic improvement cannot establish a Wi-Fi, decoder, renderer or Netty advantage.

For a before/after comparison, preserve the entire old runtime JAR before rebuilding (the exported classpath refers to mutable build outputs). Verify loaded classes come from the intended snapshot. The 2026-10-03 experiment also reconstructed the three baseline sources in scratch, verified their pre-edit SHA-256 values, and compiled them into an isolated directory placed ahead of the current runtime JAR for repeated baseline measurements.

## Metadata comparison

`FlacMetadataBenchmark.java` and `run-flac-metadata-comparison.py SCRATCH` compare isolated HEAD/current FLAC metadata-only allocation with loaded-class origin checks. Export the core test classpath first. The fixture contains STREAMINFO and no audio frames, so results describe metadata probes, not playback throughput. After committing the change, use the archived baseline source/revision rather than current HEAD. [Methods and results](../../tasks/performance/sonicnio-lazy-flac-2026-10-03/REPORT.md).

`run-flac-phone-comparison.py SCRATCH ADB SERIAL [confirmation]` uses preserved before/after APKs, paced converted HEAD/header/seek probes and external-only `meminfo --local` samples. Ordinary meminfo triggers GC and must not be used for the main comparison. Confirmation reverses installation order; candidate restoration is hash-verified. `FlacMetadataAndroidBenchmark.java` and `run-flac-android-micro.py SCRATCH ADB SERIAL` measure approximate allocation/GC counters in separate ART processes using precompiled DEX jars, after app runs finish. [Phone results and limits](../../tasks/performance/sonicnio-lazy-flac-2026-10-03/PHONE_RESULTS.md).

`MetadataBenchmark.java` measures ETag/date allocation and complete conditional/range response preparation. `run-metadata-comparison.py SCRATCH compile|micro|throughput` compares preserved/current FileResponse classes against the same runtime. Preserve `SCRATCH/baseline/FileResponse.java` before editing, export the Gradle classpath and compile first. Both variants use identical heap settings; every metadata sample records and validates its loaded class origin.

`run-metadata-phone.py SCRATCH ADB SERIAL` installs preserved `before.apk`/`after.apk` snapshots in an interleaved sequence, checks installed hashes, runs a complete warmup after every installation and compares real audio, ranges and validators. It uses the archived track-specific phone workload and leaves the optimized APK installed. [Full methods and results](../../tasks/performance/sonicnio-metadata-2026-10-03/REPORT.md).

## Android seek profiling

`profile-flac-phone.py SCRATCH ADB SERIAL pilot|compare` installs preserved `before.apk`/`after.apk`, verifies APK identity and runs paced, byte-checked converted HEAD/header-range/seek workloads with and without Perfetto. SCRATCH must contain `profile.pbtxt`; compare uses eager/lazy/lazy/eager and balanced phase order, restoring the candidate on exit. It uses external `meminfo --local`; ordinary meminfo can induce GC and contaminate results. Trace configuration/capture includes no CPU stacks or device request markers.

`analyze-flac-profile.py SCRATCH TRACE_PROCESSOR` queries unpacked traces with the pinned processor and writes SQL/CSV, phase quantiles and nominal request correlations. It requires `compare-results.json` and matching `.pftrace` files. Calibration uncertainty must be considered before interpreting overlaps; in the recorded run, ±24 ms prevents reliable per-request attribution. [Full methods, all phase results and archived traces](../../tasks/performance/sonicnio-seek-profile-2026-10-03/REPORT.md).

## Busy-worker attribution and path-index candidate

`profile-stream-workers.py SCRATCH ADB SERIAL APK` reinstalls the preserved candidate once, records three bounded 12-second Simpleperf CPU-stack captures alongside byte-verified converted ranges, and restores its activity/hash. It uses supported shell `--app` mode; direct run-as recording may fail to set profiling properties. Foreground/background phases are diagnostic and cannot isolate scan completion or thermal/UI effects.

`analyze-stream-workers.py SCRATCH` recomputes phase quantiles and sampled thread shares from archived native CSV, request JSON and record logs. `check-path-index.py SOURCE_DB NEW_OUTPUT_DIRECTORY` opens the source read-only, creates independent SQLite backups and measures exact path lookup before/after a non-unique index. It never changes the live/source DB and refuses an existing output directory. Its host query times are not Android streaming gains. [Full attribution and methods](../../tasks/performance/sonicnio-worker-profile-2026-10-03/REPORT.md).

## Room path-index Android benchmark

`PathIndexPhoneBenchmark` is an opt-in db-room Android test. Build its test APK with a private, integrity-checked v2 SQLite backup supplied as `phone-v2.db` through a scratch asset directory. Keep private database assets outside repository sources. Run the instrumentation class with `pathIndexBenchmark=true`, and repeat with `reverseOrder=true`. It compares native lookup timing and a fixed four-worker query workload while checking actual converted responses from the installed app's localhost server. All conditions use the indexed server APK; this is a lookup-contention model, not a before/after server or full-file-scan benchmark.

`analyze-path-index-phone.py SCRATCH` validates both completed instrumentation logs and recomputes micro samples, worker quotas/CPU, phase/pool quantiles and active-work overlap. [Migration, reproduction and measured limits](../../tasks/performance/sonicnio-path-index-2026-10-03/REPORT.md).
