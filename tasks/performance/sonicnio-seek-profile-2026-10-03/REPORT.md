# SonicNIO seek-tail and memory profiling

Lazy FLAC allocation produces fewer observed GC cycles in this matched workload, but **keep the release hold**. The new untraced controls do not reproduce the previous PSS increase consistently. Neither latency nor root-cause attribution is settled. Four unnamed app workers dominate CPU, and clock alignment is too coarse to identify a specific request's delay. No production code was changed; the candidate APK is restored and independently hash-verified.

## Concept and approach

The lazy decoder avoids approximately 1 MiB of scratch allocation for each metadata-only probe. Lower allocation can reduce collection frequency, but application latency can also depend on decoding, queueing, scheduling, UI work, storage and transport. Profile those separately rather than equating concurrent GC duration with a stop-the-world pause. This is a follow-up to the [allocation and phone comparisons](../sonicnio-lazy-flac-2026-10-03/PHONE_RESULTS.md), not a new throughput or Netty benchmark.

Capture scheduling, CPU frequency/idle, ART slices and process counters with system Perfetto. Add untraced controls because tracing itself can alter performance. Android's background Perfetto capture and graceful termination workflow follows the [official Android tracing guide](https://perfetto.dev/docs/learning-more/android); analysis uses the [official Trace Processor CLI](https://perfetto.dev/docs/reference/trace-processor-cli). SQL queries and raw outputs are archived, not screenshots alone.

## Test methodology

Samsung SM-S931B, Android 16/API 36, arm64, USB ADB forwarding from host port 19000 to phone 9000. Same preserved eager/lazy debug APKs as the previous comparison; only classes13.dex differs. Every installation is checked against the APK's SHA-256. Baseline `248f307565582319caabea86b001cc0665f52dac49a0595101d3cae9bc3a7cb3`; candidate `81ad7cb39e44fa72ad529f8460471e3422848af0640488cd03d7e55550796444`.

Track 2122216336, LG webOS user agent, converted WAV 29,529,404 bytes, SHA-256 `edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676`. Each install fetches and compares the full reference, then warms up with 64 HEADs and 16 converted seeks. Measured phases each contain 64 converted HEADs, 64 header-only ranges (bytes 0–43) and 48 deterministic 64 KiB converted seeks. HEAD status/length and every range's exact bytes/status are checked. TTFB ends when urllib returns parsed response headers, not at the first body byte. Requests are paced at 20/s outside timers to avoid the production request limiter; ordinary urllib connection behavior is preserved.

Four installs run eager/lazy/lazy/eager. Phase order alternates control/trace, trace/control, control/trace, trace/control. This yields two independent phases per variant per instrumentation condition, 1,408 measured requests total, including 384 seeks. The separate candidate pilot contributes another 352 verified requests and is excluded from comparative pooling. All requests passed; all eight measured phases retained the same PID within the phase.

External `dumpsys meminfo --local` before/after each phase avoids the app callback/GC caused by ordinary meminfo. Two samples describe boundary PSS; they are not peak memory or live-object counts. Warm storage, background/UI activity and device temperature are not controlled. These conditions differ from the preceding twelve-run bulk workload: do not merge their PSS/latency values or calculate a combined speedup.

The [configuration](profile.pbtxt) uses a 32 MiB ftrace buffer and 2 MiB process buffer, sched_switch/sched_waking, CPU frequency/idle, dalvik/am atrace categories and 100 ms process-stat sampling. Duration is capped at 30 s; each trace is terminated after its workload, boundary memory sampling and clock calibration, then flushed and pulled. Trace Processor non-info, nonzero diagnostic queries return no rows for the four comparative traces. This supports successful capture; it does not guarantee all possible ART or application events are instrumented.

Five shell-clock measurements before/after each phase record host RTT and device epoch time. The lowest RTT sample maps host epoch to Perfetto's REALTIME snapshots. A conservative uncertainty envelope combines half RTT, offset drift and snapshot disagreement. **The resulting ±23.66–24.17 ms uncertainty is comparable to the seek latency. Per-request overlap and scheduler sums in the analysis JSON are nominal estimates; do not use them as causal evidence.** No CPU stacks or per-request device trace markers were captured.

## Results for every phase

Each row has 48 measured seeks. Quantiles use linear interpolation at (N−1)×p. Pooled quantiles below use 96 seeks per variant/condition, not medians of run quantiles.

| Phase | Seek median ms | p95 ms | p99 ms | Maximum ms | Boundary PSS MiB |
| --- | ---: | ---: | ---: | ---: | ---: |
| compare-before-0-control | 12.453 | 18.900 | 20.470 | 20.555 | 220.91 → 221.93 |
| compare-before-0-trace | 11.164 | 19.245 | 22.168 | 23.520 | 223.06 → 233.78 |
| compare-after-1-trace | 13.162 | 22.227 | 24.403 | 24.949 | 224.82 → 219.02 |
| compare-after-1-control | 13.199 | 19.996 | 20.944 | 21.250 | 228.94 → 228.08 |
| compare-after-2-control | 13.897 | 23.686 | 26.622 | 28.128 | 231.09 → 219.62 |
| compare-after-2-trace | 12.895 | 23.767 | 25.228 | 26.166 | 221.10 → 227.11 |
| compare-before-3-trace | 14.938 | 24.272 | 27.936 | 29.113 | 219.01 → 217.26 |
| compare-before-3-control | 14.859 | 24.746 | 25.972 | 26.531 | 223.32 → 218.54 |

## Compared results and instrumentation control

| Pooled seek TTFB | Eager control | Lazy control | Eager trace | Lazy trace |
| --- | ---: | ---: | ---: | ---: |
| Median ms | 13.525 | 13.332 | 12.337 | 13.087 |
| p95 ms | 23.281 | 20.762 | 22.187 | 23.732 |
| p99 ms | 25.400 | 25.085 | 26.734 | 25.010 |
| Maximum ms | 26.531 | 28.128 | 29.113 | 26.166 |
| Converted HEAD median ms | 13.516 | 12.102 | 14.355 | 13.859 |
| Header-only range median ms | 14.225 | 12.133 | 14.088 | 14.781 |

Untraced lazy seek median is 1.4% lower and p95 10.8% lower, while the maximum is 6.0% higher. Tracing reverses the direction of the median/p95 comparison. Metadata latency also changes under tracing. With two phases per condition and substantial run variation, these are descriptive results, not confidence intervals or evidence of a robust latency win. The earlier adverse memory signal remains important, but these boundary PSS samples overlap (eager 217.26–233.78 MiB; lazy 219.02–231.09 MiB) and do not consistently reproduce it.

## Trace findings

GC cycle counts cover each whole capture, including boundary observations. Suspension values refer specifically to the recorded `Mutator threads suspended for ScopedPause` slice; concurrent GC wall time is not pause time.

| Trace | GC cycles | Recorded suspension total ms | Largest scope ms | Four pool-7 threads CPU s / share of app CPU | Clock uncertainty ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| compare-before-0-trace | 10 | 5.997 | 0.838 | 27.23 / 79.2% | ±24.17 |
| compare-after-1-trace | 4 | 2.886 | 1.066 | 28.07 / 79.1% | ±23.93 |
| compare-after-2-trace | 3 | 2.062 | 0.860 | 28.03 / 81.3% | ±23.71 |
| compare-before-3-trace | 8 | 6.100 | 1.119 | 28.18 / 76.8% | ±23.66 |

Eager captures contain 18 GC cycles versus 7 in lazy captures: a descriptive 61.1% reduction across matched request counts, not an allocation-rate measurement or a guarantee for playback. Summed recorded suspension scopes fall from 12.097 to 4.948 ms (59.1%). The largest recorded scope is 1.119 ms before and 1.066 ms after. Individual concurrent GC cycles run as long as 71.805 ms; those durations must not be reported as stop-the-world stalls. Other ART suspension mechanisms are outside this particular slice total. These measured scopes alone do not explain 20–29 ms seeks; concurrent collection CPU effects are still possible.

The pilot alone records five GC cycles despite previous logcat-based counts showing none. Absence of GC log lines was not evidence of zero app GC. Its largest recorded ScopedPause is 0.571 ms. Pilot results are retained separately.

Four app threads named pool-7-thread-1 through pool-7-thread-4 account for 76.8–81.3% of scheduled app CPU time. Main and RenderThread are also active. CPU slices for streaming producers total roughly 0.80–0.86 s per trace; selector thread totals roughly 0.19–0.25 s. **This establishes substantial concurrent app work, not its function or proof that it caused a particular seek delay.** Java's numbered pool names do not identify an executor reliably; repository executor definitions are insufficient to assign these threads without stacks. Do not disable or reprioritize them based on names alone.

ART's emitted `Heap size (KB)` counter spans 20,325–67,009 and 20,193–70,577 in eager captures, and 22,101–74,813 and 21,729–72,005 in lazy captures. These are emitted counters, not direct allocated bytes, retained heap or comparable PSS peaks. Sampled RSS maxima are higher in the lazy captures (about 326.8/321.7 MiB versus 303.2/294.3 MiB); RSS and PSS describe different accounting. No leak or cause of the earlier PSS increase is established.

The selector has a 20 ms empty-select backoff in source, but scheduling-only traces cannot distinguish that sleep from normal waits reliably. Current diagnostics time pending work and selected-key processing in separate calls; they do not directly expose backoff entry. Do not infer that a similarly sized seek was caused by the backoff.

## Decision and next experiment

Keep the lazy allocation change held from release and preserve the earlier parser/copy hold. Confirmed allocation savings and fewer captured collections strengthen the mechanism, but do not resolve mixed end-to-end tails or memory behavior. Candidate remains installed for investigation; no rollback, additional transport optimization or Netty comparison was performed.

Next: identify the dominant pool workers with device CPU stacks/thread dumps; then repeat under explicitly idle UI/background work and under a separately labeled busy condition. Add device-side request/queue/decode/write timestamps or trace sections with a shared monotonic clock, plus an explicit selector-backoff event, before attributing individual tails. Measure a selector-owned bounded immediate-write experiment only after those measurements identify write queueing as material. These are proposed follow-ups, not implemented changes or measured improvements.

## Evidence and reproduction

[All phase/request measurements](compare-results.json), [pilot](pilot-results.json), [computed summaries](analysis/summary.json), [toolchain and restored APK](toolchain.json), [raw trace hashes](traces.json). Five gzip-compressed raw traces are archived beside this report; decompress in scratch before running analysis. The original trace files and preserved APKs remain under `/private/tmp/musicmate-seek-profile-20261003`; the processor binary is pinned by URL/hash in toolchain.json and not copied into the repository.

[Capture harness](profile-flac-phone.py) and [analysis harness](analyze-flac-profile.py) are identical to their tools/bench copies. SQL/CSV evidence includes clocks, GC, process counters, scheduling states, all app thread CPU totals and trace diagnostics; per-request JSON preserves nominal overlap calculations with their calibration limitation. Memory snapshots, trace acknowledgments, progress logs and download manifest are retained. `manifest.json` hashes every other archived artifact. Reproduction requires the existing workload track/settings, preserved APKs and attached phone for capture; analysis only requires unpacked traces, compare-results.json and the pinned processor.

Verification: all 1,408 comparative requests and 352 pilot requests passed; full references match; candidate SHA rechecked after profiling. All 871 previously preserved production file hashes remain identical. Prior 222 core/UPnP tests remain applicable because this step changes benchmark scripts/documentation only; they were not rerun. Scoped whitespace checks, Python syntax, archived digest checks and recomputed-analysis comparison are recorded in verification.json.
