# Idle converted-seek attribution — 2026-10-03

The unchanged indexed server candidate still has approximately 18 ms idle seek latency in the initial untraced controls. Device-aligned scheduling traces and CPU stacks identify substantial **FLAC metadata reading before response headers**. In the confirmation trace, median seek latency is 13.871 ms, including 8.818 ms of summed worker CPU time. CPU-only stacks locate this work in `FlacToWav.open → readAndHandleMetadataBlock → readFully`; byte reads and CRC updates account for 73.24% of worker self event periods.

**Next experiment:** a streaming-specific metadata path that consumes required STREAMINFO/seek-table data and skips unused blocks, while preserving validation, offsets and decoded bytes. No production optimization is implemented here, and no throughput or Netty advantage is claimed. Existing lazy-decoder/parser release holds remain. The candidate APK and activity are restored.

## Concept and scope

The preceding [path-index experiment](../sonicnio-path-index-2026-10-03/REPORT.md) made fixed database work much cheaper but did not improve streaming. Against one server APK, idle/indexed seeks were about 18 ms, while sustained unindexed query load produced about 5 ms. This investigation distinguishes CPU execution, runnable scheduling delay, sleeping and recorded GC suspension. A selector sleeping while a worker prepares a response is expected; that state alone does not establish a selector backoff defect.

Production inspection shows `PcmSource.open` calls `FlacToWav.open` before returning headers. That opens a FLAC decoder and consumes all metadata blocks. The decoder currently allocates each block's byte array and `readFully` reads it byte by byte through `readUint(8)`, with CRC processing underneath. Actual PCM decoding runs separately on `nio-stream-prod`; CPU after headers is outside the per-request header interval.

The selector's 20 ms sleep requires more than ten consecutive empty selections that return in under 50 ms. It is not a routine delay on every request. Off-CPU stacks show selector waits in `Selector.select → poll`; no request-level backoff counter was captured, so occasional backoff is neither proved nor ruled out.

## Approach and test methodology

- Device: Samsung SM-S931B, Android 16/API 36, arm64, serial <device-serial>. Server PID remains 12989 through all captures.
- Installed server SHA-256: `4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16`. No server installs, database migrations or production edits in this step.
- Opt-in Android benchmark changes add `idleOnly=true` and `clientTrace=true`. A separate test APK makes device-local HTTP requests to `/music/2122216336/file` on port 9000. Each measured phase interleaves 64 HEAD, 64 WAV-header ranges and 48 converted 64 KiB seeks. Two phases give 352 requests/invocation; client pacing is 20 requests/s, outside the latency timer. Connections close after each request.
- The benchmark first performs its existing isolated database setup, lookup samples, complete reference fetch and 64 HEAD/16 seek warmups. `idleOnly` suppresses measured query contention, not setup. It does not certify the absence of every app background job.
- All response statuses, lengths, WAV headers and range bytes are checked against a full reference with SHA-256 `edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676`. Nine invocations verify **3,168 measured requests**, including 864 seeks; warmups/reference fetches are additional.
- Latency is `headers_ns - start_ns`, measured using device `System.nanoTime()`. This includes connection setup and client header parsing; it is not a timestamp of the first successful server write. An Android Trace section surrounds the timed interval. Both traced and control workloads execute the same marker calls.
- Perfetto records sched_switch/sched_waking, reported CPU frequency/idle, process statistics and app/client trace sections. Buffers are 64 MiB ftrace plus 2 MiB process statistics; maximum duration is 45 seconds, stopped after the workload. Pilot/comparison use [initial configuration](profile-initial.pbtxt); confirmation enables thread-name recording in [final configuration](profile.pbtxt).
- MONOTONIC clock snapshots map device timestamps into Perfetto. Snapshot offset spread is 104 ns in the pilot, 0/156 ns in the comparison and 105 ns in confirmation. All **1,408 traced measured requests** lie within trace bounds and exactly one matching client section. This resolves the earlier host-clock uncertainty. [Android uptime clock implementation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/os/SystemClock.java), [Perfetto clock snapshots](https://perfetto.dev/docs/reference/trace-packet-proto).
- Thread states are clipped to each request's start-to-headers interval. Running is on-CPU; R/R+ is runnable; S is sleeping; D is uninterruptible. Values are summed within thread groups. Eight workers' sleeping totals can exceed wall latency and are **not** request queue time. Medians from different groups are not additive critical-path components.
- Reported frequency is weighted by overlapping Running time on each CPU. Confirmation has 100% worker frequency coverage. Frequency counters are not measurements of effective clock cycles, and correlation cannot establish governor causation.
- Initial comparison traces omit process ownership for some existing workers. Their raw partial worker aggregates are retained with `worker_attribution_complete=false` and must not be interpreted as low/zero CPU. Confirmation records before/after ADB thread inventories and uses a LEFT JOIN plus validated TIDs to recover ownership; all eight worker TIDs are present. Thread-name recording alone is insufficient to assume ownership. [Perfetto process statistics configuration](https://perfetto.dev/docs/reference/traced_probes).
- Battery temperature, thermal status and approximate **benchmark-process** GC counters are preserved at phase boundaries. Ordinary meminfo is not used. Server recorded ScopedPause overlaps are queried separately; zero recorded overlap is not proof that all GC effects are absent.

## Every latency run

Each row has 352 verified measured requests and 96 seeks. Percentiles use linear interpolation between sorted samples. These are sequential measurements, not independent randomized trials or confidence intervals. All HEAD/header/seek distributions and environment snapshots are in the raw records and [complete latency table](all-latencies.csv).

| Invocation | Seek median, ms | p95, ms | p99, ms | Maximum, ms |
| --- | ---: | ---: | ---: | ---: |
| pilot-0-trace | 11.707 | 18.949 | 20.316 | 21.328 |
| compare-0-control | 17.736 | 18.857 | 20.592 | 20.764 |
| compare-1-trace | 14.404 | 19.322 | 19.892 | 19.946 |
| compare-2-trace | 13.362 | 19.581 | 20.248 | 22.380 |
| compare-3-control | 17.696 | 18.894 | 19.399 | 19.846 |
| confirm-0-trace | 13.871 | 18.872 | 19.165 | 19.318 |
| confirm-1-control | 16.506 | 21.020 | 22.267 | 22.802 |
| offcpu stack workload | 18.162 | 19.501 | 20.070 | 20.456 |
| oncpu stack workload | 17.845 | 19.040 | 19.213 | 19.227 |

The comparison uses **control, trace, trace, control**; confirmation uses trace, control. Tracing reduces median latency materially while tails remain near 19–22 ms. This is an observer effect, not an optimization. Confirmation's later control also differs from the first controls; runtime state is not perfectly stable. Neither traced latency nor stack workload latency should substitute for an uninstrumented production result.

## Confirmation scheduling and frequency results

For 96 seeks, median selector Running/runnable/sleeping time is **1.272/0.548/12.056 ms**. Median worker Running/runnable time is **8.818/0.475 ms**; producer Running time before headers is **0.248 ms**, client Running time **1.932 ms**. The small producer value says little about decoding the body after headers.

| Within confirmation trace | Fastest 24 seeks | Slowest 24 seeks |
| --- | ---: | ---: |
| Median headers latency | 7.695 ms | 18.507 ms |
| Summed worker Running time | 4.629 ms | 11.789 ms |
| Summed worker runnable time | 0.251 ms | 0.784 ms |
| Running-time-weighted reported worker frequency | 1,475 MHz | 557 MHz |
| Selector Running time | 0.756 ms | 1.585 ms |
| Selector runnable time | 0.372 ms | 0.659 ms |
| Client Running time | 1.143 ms | 2.449 ms |

No recorded server ScopedPause overlaps any of these 96 seek header intervals. All four captures have empty non-info, nonzero quality-stat queries. These checks support the trace analysis but cannot guarantee that every relevant event is observable. The frequency/CPU relationship is consistent with expensive metadata preparation taking longer under idle runtime conditions; workload, CPU placement, frequency and profiling effects are not independently controlled. [Exact quartiles](quartiles.json), [request intervals and thread-state totals](confirm-analysis/confirm-0-trace-requests.json), [SQL/raw query results](confirm-analysis/summary.json).

## Stack measurements

Two separate 30-second Simpleperf recordings run the same idle workload. Both use user-mode `task-clock:u`, 199 Hz sampling and Java/native call graphs in the existing debuggable app. The off-CPU run adds `--trace-offcpu`; it records 10,605 samples with zero lost. Its reports include waiting time and are used to locate waits, **not** to estimate CPU shares. Poll-based selector waits dominate its selector branch; some Thread.sleep stacks elsewhere belong to jUPnP tasks.

The CPU-only run records **1,588 samples, zero lost**, with total sampled event periods of 7.980 seconds. Workers contribute 3.136 seconds (624 samples, 39.3%); the producer contributes 3.744 seconds (46.9%); the web selector contributes 0.342 seconds (4.3%). These cover warmup/reference work, measured headers and bodies across the recording; they are not per-request timing and do not represent total kernel CPU. [Android Simpleperf profiling methods](https://android.googlesource.com/platform/system/extras/+/android16-release/simpleperf/doc/android_application_profiling.md).

| Worker self symbol | Worker event-period share |
| --- | ---: |
| AbstractFlacLowLevelInput.readUint | 30.77% |
| AbstractFlacLowLevelInput.updateCrcs | 20.03% |
| AbstractFlacLowLevelInput.readUnderlying | 17.31% |
| AbstractFlacLowLevelInput.readFully | 5.13% |

The filtered worker call graphs place these functions below metadata opening in the response handler. Nested children percentages are local to branches and must not be added across frames. No selector Thread.sleep self samples appear in the CPU-only report; a sleeping function's absence from CPU samples cannot rule out backoff. [CPU worker call graphs](oncpu-workers.txt), [self event summary](stack-summary.json), [complete CPU report](oncpu-csv.csv), [off-CPU call graphs](offcpu-callgraph.txt).

## Verification, failures and reproducibility

The test APK builds successfully; every invocation reports `OK (1 test)` and successful instrumentation completion. The 226 correctness cases from the preceding implementation remain the applicable production check; they are not rerun because production code is unchanged. Hash verification covers 886 previously recorded source/assets/test files, excluding the deliberately changed opt-in benchmark. Scoped main comparison was inspected, but main lacks many current classes and is not a clean baseline for this profiling step. [Verification](verification.json), [preserved source hashes](production-source-verification.json), [build output](build-final.log).

Investigation corrections: process-name filtering initially missed unnamed processes; explicit ADB PID and client trace sections replaced it. Missing worker ownership in comparison traces required confirmation thread inventories and a LEFT JOIN. The first inventory parser used the wrong ps column count; inspected tokens and corrected it before final confirmation analysis. A scratch one-line Python summary used literal escaped newlines and failed syntax; a standalone script replaced it. Archive verification initially included the intentionally modified benchmark in the unchanged-source check; only that exact test file is excluded, with production hashes still enforced. Missing assumed README/SHA256SUMS paths were resolved using actual repository file listings. No failed command was repeated with identical arguments.

Archived evidence includes all nine raw workload logs, parsed records, four gzip-compressed Perfetto traces, both compressed Simpleperf recordings, configurations, query SQL/CSV/stderr, clock/identity inspection and exact harness sources. Compression round trips are verified. No private database or audio content is included. Trace processor is v58.2-add693d8b, SHA-256 `d29864d1ba3b36855527bb1b0ca3aa7f703cdce338b9680bb922c5c151b358fa`; device Perfetto is v51.2.

To reproduce, supply the same private database asset through the preceding path-index benchmark build procedure, install only the separate test APK, and run `trace-idle-phone.py SCRATCH ADB SERIAL pilot|compare|confirm` with the archived configuration. `analyze-idle-trace.py SCRATCH PROCESSOR MODE` consumes the corresponding results JSON and decompressed trace filenames. Initial modes must use the initial config; confirmation uses final config and thread inventories. The capture harness pins the server APK hash. Uncompress recordings into scratch; keep private assets there. `capture-cpu.py` and `summarize.py` preserve the bounded CPU capture/aggregation procedure. Installed server remains on database version 3 throughout; no downgrade is needed.

## Decision and next validation

Retain the measured path index as a database candidate. Prioritize eliminating unused metadata reading/allocation over transport changes. Add an explicit streaming metadata API while preserving the existing block-returning API for callers that need metadata bytes. Verify duplicate/order checks, truncation, unknown/reserved blocks, seek-table behavior, metadata end offsets and audio CRCs; skipping must not silently weaken accepted input validation. First measure CPU/allocation with large metadata fixtures, then compare byte-identical WAV/ranges and untraced Android idle/busy latency in reversed order. Throughput and a current Netty comparison require separate matched measurements. Do not change device power policy or remove selector backoff on this evidence.
