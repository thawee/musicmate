# Lazy FLAC: physical-phone results

Allocation reduction is confirmed on ART, but **hold this change from release pending profiling**. Application throughput is essentially unchanged; sampled PSS is higher in both series and latency is mixed. This is a release recommendation, not a rollback: candidate source/build outputs and the candidate APK remain restored/installed. The previous combined parser/copy release hold also remains unresolved.

## Isolated ART allocation result

Device: Samsung SM-S931B, Android 16/API 36, arm64. Six fresh standalone `dalvikvm` processes ran before/after/after/before/before/after. Each performed 64 warmups and three measured samples of 128 metadata probes; nine samples and 1,152 measured probes per variant. The ART heap maximum was 256 MiB. These processes ran **after** all app comparisons, so they did not compete with app measurements.

The fixture has the same final STREAMINFO-only layout as the host test: 42 bytes, stereo 24-bit 96 kHz, declared 96,000 samples, no audio frames. Each probe opens, consumes metadata, verifies sample count/depth and closes the file in `/data/local/tmp`. This is a metadata microbenchmark, not MusicMate's storage path, server process or playback workload. A pre-measurement reflection check verifies whether the loaded decoder is eager/lazy. Device DEX jar hashes were checked before execution. Before/after jars share all library classes except FlacDecoder and use the same harness.

| Isolated ART measurement | Eager | Lazy | Observation |
| --- | ---: | ---: | --- |
| Median allocated bytes/probe | 1,066,240 | 9,184 | **−99.1%** |
| Median metadata probe time | 35.684 µs | 18.210 µs | −49.0%; isolated fixture only |
| GC counter delta, measured probes summed | 7 | 0 | Across 1,152 probes/variant; excludes warmups |
| GC time counter delta, summed | 69 ms | 0 ms | Approximate runtime counter, not pause time |

The harness reads `Debug.getRuntimeStat` allocation/GC totals. Android documents these as approximate process statistics; individual allocations can be reflected later. Counter calls and runtime activity contribute small overhead, and concurrent collections can cross sample boundaries. This result does not establish the app process's allocation rate or GC pause reduction. [Android Debug API](https://developer.android.com/reference/android/os/Debug#getRuntimeStat(java.lang.String)).

[Every ART sample](phone/android-micro/SAMPLES.md), [raw counters](phone/android-micro/android-results.jsonl), [summary](phone/android-micro/android-summary.json), [device DEX hashes](phone/android-micro/device-dex-hashes.json), [harness](phone/FlacMetadataAndroidBenchmark.java), [runner](phone/run-flac-android-micro.py).

## App comparison design

Fresh baseline and candidate debug APKs use the same working-tree production sources, except the eager/lazy FlacDecoder implementation. Baseline compilation uses a Gradle source override rather than editing production files. Source hashes were preserved for 871 production files. APK entries and uncompressed contents were compared: **only classes13.dex differs**; manifest, resources and packaged libraries match. Candidate build outputs were restored after baseline compilation and all 222 core/UPnP tests pass.

| APK | SHA-256 |
| --- | --- |
| Eager baseline | `248f307565582319caabea86b001cc0665f52dac49a0595101d3cae9bc3a7cb3` |
| Lazy candidate | `81ad7cb39e44fa72ad529f8460471e3422848af0640488cd03d7e55550796444` |

APKs remain in `/private/tmp/musicmate-lazy-flac-phone-20261003/{before,after}.apk`; their hashes, sources and build logs are archived. [APK comparison](phone/initial/apk-comparison.json), [production source manifest](phone/initial/source-manifest.json), [source override](phone/initial/baseline-source.gradle).

Transport is **USB ADB forwarding**, host `127.0.0.1:19000` to phone port 9000. It is not a Wi-Fi/LAN or renderer throughput test. Track `2122216336` has an original body of 9,940,667 bytes and converted WAV of 29,529,404 bytes. The LG webOS user agent selects converted WAV. Each request uses urllib's ordinary HTTP connection behavior; no custom connection pool/keep-alive tuning was added. Storage caches are warmed rather than controlled cold.

Twelve retained app runs, six per APK, form two series:

- Initial: after/before/before/after/before/after.
- Confirmation: before/after/after/before/after/before, reversing order after the initial adverse result.

Each installation verifies the installed APK hash and launches the app. One full workload warmup follows every installation. The retained common workload checks a full original transfer, twelve original seeks, four simultaneous original bodies, full converted WAV and four converted ranges. Then a second full WAV establishes the byte reference for added probes. Dedicated warmups perform sixteen HEAD/header-range pairs. Retained probes perform 128 converted HEAD requests, 128 exact 44-byte WAV-header ranges and 48 converted 64 KiB seeks distributed across the file.

Added probes are paced at at most 20 requests/second, below the verified production limit of 50 requests/second. Pacing waits occur before request timers. An initial unpaced attempt received 429; it is preserved separately and contributes no retained statistics. Production rate limiting was not changed.

Client TTFB ends when urllib opens the response, including header receipt. This is not a server first-successful-write timestamp. Percentiles use nearest rank. With 48 seeks/run, per-run p99 equals that run's maximum; the median of those maxima is distinct from pooled p99. Common original-seek `seek_p95_ms` is also the maximum of its twelve seeks. Combined table entries are medians of six per-run metrics per APK unless explicitly labeled pooled. Samples within an installation share process/thermal state and are not independent APK trials.

## Results, including adverse observations

| Combined app metric | Eager | Lazy | Change |
| --- | ---: | ---: | ---: |
| Four-stream native throughput | 39.569 MiB/s | 39.451 MiB/s | −0.3%; essentially unchanged |
| Full converted WAV duration | 0.867019 s | 0.891725 s | +2.8% |
| Converted HEAD median | 14.274 ms | 16.672 ms | +16.8%; direction changes between series |
| Converted header-range median | 13.901 ms | 17.381 ms | +25.0% |
| Converted seek median | 13.424 ms | 12.874 ms | −4.1%; direction changes between series |
| Median of converted-seek run maxima | 23.145 ms | 24.665 ms | **+6.6%; adverse in both series** |
| Pooled converted seek p95, 288 seeks/variant | 22.748 ms | 22.377 ms | −1.6% |
| Pooled converted seek p99 | 26.217 ms | 24.943 ms | −4.9%; direction changes between series |
| Absolute converted seek maximum | 27.075 ms | 26.445 ms | −2.3%; baseline's worst run is in confirmation |
| Median of original-seek run maxima | 20.777 ms | 22.397 ms | +7.8%; direction changes between series |
| Median of per-run sampled maximum PSS | 225.621 MiB | 257.349 MiB | **+14.1%; adverse in both series** |
| Median of per-run sampled maximum Java heap PSS | 31.703 MiB | 58.520 MiB | +84.6%; not live allocated bytes |

Tail results are mixed: pooled p99 improved in the combined data, while median per-run maxima increased in both series. Do not summarize this as all tails regressing or a general seek improvement. Small process sample counts, installation/JIT/cache behavior, UI/background work and temperature drift limit causal attribution. Battery temperature ranged 30.1–38.2°C; temperature is a coarse condition indicator, not proof of CPU thermal throttling.

| Series | Throughput eager → lazy | HEAD median eager → lazy | Converted-seek median eager → lazy | Median run maximum eager → lazy | Sampled maximum PSS eager → lazy |
| --- | ---: | ---: | ---: | ---: | ---: |
| Initial | 39.465 → 39.562 MiB/s | 12.781 → 19.167 ms | 12.502 → 14.169 ms | 20.661 → 24.386 ms (+18.0%) | 225.474 → 255.260 MiB |
| Confirmation | 39.673 → 39.165 MiB/s | 15.150 → 14.177 ms | 14.622 → 12.611 ms | 23.682 → 24.943 ms (+5.3%) | 227.373 → 259.438 MiB |

Lower allocation need not lower sampled resident memory: GC timing, allocator retention and unrelated app allocations can affect PSS. Their contribution here is unproven. The isolated ART counter reduction does not explain or cancel the adverse app PSS observations.

## Memory and GC methodology correction

The first complete six-run series used regular `dumpsys meminfo`. It produced four explicit GC records per measured metadata phase, matching the four in-process snapshots. Upstream Android's memory dump invokes GC; the phone's help confirms `--local` avoids calling into the process. This series is retained as an **instrumentation control and excluded from the main app comparison**. [Android ActivityThread memory dump implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityThread.java).

Both retained series use `dumpsys meminfo --local`: before the dedicated metadata phase, after each of four blocks, and after converted seeks. That supplies six external samples/run. Peak columns mean the maximum of those six samples, not a continuously observed high-water mark. Java heap PSS is resident/shared-memory attribution, not an allocated-byte counter. Local meminfo does not provide valid heap allocation counters; the runner records their absence instead of treating its zero fields as measurements.

GC logs use device epoch timestamps and the last 2,000 logcat records filtered to the measured app PID. Metadata phases show zero visible GC records for both APKs. **This does not establish zero GC or equivalent GC activity**: logs are bounded, not every collection is necessarily logged, and full-body/seek phases are outside this phase count. Exact app runtime allocation/GC counters and continuous memory sampling remain open. The separate ART microbenchmark supplies counters for its own process only.

[Control runs and records](phone/observer-control/phone.json), [control runner](phone/observer-control/observer-runner.py), [failed unpaced attempt](phone/failed-unpaced/failure.log).

## Correctness and evidence

All retained checks pass. Across twelve measured app runs: **2,304 exact original/converted ranges**, **1,536 HEAD responses**, **48 parallel original-body hashes**, twelve metadata-header identities and both full-body identities. Warmups pass another 384 exact ranges and 48 parallel hashes. The observer control's checks also pass but are not pooled into these counts. No process restarted during the dedicated measured phases.

- Original SHA-256: `69619cbf611ad6711cfa7962916eec48879759dc7b839af8ba15b590eb818626`.
- Converted WAV SHA-256: `edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676`.
- ETag, Last-Modified, Content-Range and Content-Type identities match between APKs.
- Installed candidate SHA-256 was checked again after both app series and the ART microbenchmark setup.

[Every app run](phone/RUNS.md), [initial raw data](phone/initial/phone.json), [confirmation raw data](phone/confirmation/phone.json), [all calculated comparisons](phone/initial/phone-summary.json), [recalculation script](phone/analyze-results.py), [runner](phone/run-flac-phone-comparison.py), [common harness](phone/phone-check.py), [device build identity](phone/initial/device.json), [evidence hashes](manifest.json). Per-request times, seek offsets, warmups, process memory samples, GC records, build logs and failed/control attempts are retained under `phone/`.

Reproduce app runs with the preserved APKs using `run-flac-phone-comparison.py SCRATCH ADB SERIAL`, then a separate scratch directory and optional `confirmation` mode for reversed order. `run-flac-android-micro.py SCRATCH ADB SERIAL` uses the preserved before/after DEX jars; its source was compiled with Java 17, the restored JustFLAC runtime jar, Android API 36 stubs and build-tools 37 d8 (`--min-api 36`). Run the ART benchmark after app measurements. It writes only the task directory in `/data/local/tmp` and does not modify the installed app.

The release gate is not met. Profile app allocation/GC timing and response preparation/producer startup, correlate seek tails and PSS, and control warmup/temperature effects before promoting this isolated change. No Netty performance advantage, Wi-Fi throughput gain or audible playback improvement has been established.
