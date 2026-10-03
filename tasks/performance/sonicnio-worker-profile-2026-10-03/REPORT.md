# SonicNIO busy-worker attribution

The reproduced busy workers are **library scanning tasks**, not SonicNIO socket workers. CPU call chains show `ScanAudioFileWorker.processPaths → FileRepository.scanMusicFile → TagRepository.getByPath → Room/SQLite`. The phone database has 8,289 tracks and no path index. On a checked copy, the exact lookup scans the table; an isolated non-unique path index changes it to an indexed search and preserves query results. This is the next optimization candidate; no live schema, application code or APK change was made.

## Concept and approach

The [preceding Perfetto investigation](../sonicnio-seek-profile-2026-10-03/REPORT.md) found four unnamed pool workers consuming 76.8–81.3% of scheduled app CPU but lacked stacks. This step samples their actual call chains under a reproduced candidate startup/workload. It separates background library work from FLAC decoding and socket work before tuning selector behavior. New samples identify the same numbered-worker pattern; attributing every earlier trace interval to these exact functions remains an inference because those earlier traces contain no stacks.

Exact file-path lookup occurs for each scanned file, including unchanged files. A table scan repeated across thousands of files is a concrete source of background database work. A non-unique index preserves the DAO's equality comparison and all matching rows, including paths shared by multiple records. This is a database candidate, not evidence that a transport-library rewrite is needed.

## Methods and capture limits

Same Samsung SM-S931B, Android 16/API 36, arm64 and lazy debug APK as the previous investigation: SHA-256 `81ad7cb39e44fa72ad529f8460471e3422848af0640488cd03d7e55550796444`. The capture harness reinstalls that same preserved APK once to reproduce process startup and checks installed identity. It fetches the converted WAV reference and verifies its known SHA-256, warms up with 64 HEADs and 16 seeks, then captures three phases in one process (PID 1306): foreground, HOME/background, foreground restored. Activity and candidate hash are restored/verified afterward. The isolated helper abort did not terminate that app process; final PID is still 1306.

Each phase contains 64 converted HEADs, 64 exact header-only ranges and 48 deterministic 64 KiB seeks, paced at 20/s outside timers via USB ADB forwarding, the same track/user agent as the previous report. All **528 measured requests passed** status/length/body checks. Warmups/reference are additional. Request TTFB ends at urllib's parsed response headers. No Netty, cold-cache, Wi-Fi or throughput comparison was run.

Each CPU recording is capped at 12 s, uses Simpleperf `task-clock:u`, 199 Hz and DWARF call chains (`-g`). Recording starts one second before the measured request sequence. Reports preserve all CSV rows and call graphs with a 0.5% entry cutoff. Simpleperf identifies itself as `1.build.S931BXXSCCZH1`; binary hashing is denied by the device, so no binary digest is claimed. [Official Android application profiling documentation](https://android.googlesource.com/platform/system/extras/+/android16-release/simpleperf/doc/android_application_profiling.md) supports profiling debug builds without APK changes and Java call chains on modern Android.

These are diagnostic captures with sampling overhead, a fixed phase order and uncontrolled temperature/UI/background work. The scan appears only in the first capture; it completes during the sequence. Moving HOME cannot be credited with stopping it or improving CPU use. Captures after completion still vary in seek latency. No device-side request timestamps were added, so this step does not resolve the prior ±24 ms clock-alignment limitation or prove which request was delayed by a scan or selector backoff.

## Every capture and result

Each phase has 48 seeks. Quantiles use linear interpolation at (N−1)×p. Sampled event time is an aggregate of user-mode task-clock event periods, not elapsed time or exact scheduler CPU accounting.

| Capture | Samples recorded / lost | Four pool-7 workers share | Sampled task-clock event seconds | Seek median ms | p95 ms | p99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| foreground-first | 3626 / 0 | 68.62% | 18.221 | 11.160 | 16.044 | 19.173 | 20.244 |
| background | 613 / 0 | 0.00% | 3.080 | 10.955 | 20.597 | 21.483 | 21.931 |
| foreground-restored | 1342 / 0 | 0.00% | 6.744 | 15.736 | 23.175 | 24.467 | 25.195 |

The first capture contains 3,626 samples, zero lost samples and one reported erroneous call chain (0.0276%). The other captures report zero lost samples and no error-callchain header. Four pool workers account for 68.62% of sampled event periods in the first capture. Their stacks include scan, repository path lookup, generated TrackDao implementation and SQLite cursor-window execution. Do not sum `Children` percentages across nested callgraph entries: those entries overlap. All raw rows, call chains, thread inventories and per-request timings are retained.

With scanning absent from the later two samples, FLAC functions such as `restoreLpc`, CRC updates and Rice decoding are prominent in producer samples; RenderThread contributes substantial foreground-restored activity. This supports separating scan/UI/decoder work in future experiments. The restored foreground seek median is higher despite the absence of scan samples; CPU dominance alone therefore does not explain the observed tail variation. The phase sequence is not a controlled busy-versus-idle performance comparison.

## Path query and index experiment

Repository source verifies:

- `ScanAudioFileWorker.processPaths` submits a scan task per path through `MusicMateExecutors.scan`.
- `FileRepository.scanMusicFile` calls `getByPath` before deciding whether file metadata is outdated.
- `TrackDao.getByPath` is `SELECT * FROM musictag WHERE path = :path`, without a limit.
- `TrackEntity` declares ten indexes, including uniqueKey, but no index on path. Room database version is 2; exportSchema is currently false.

Java thread dumps are blocked because debuggerd requires root. An isolated Java/app_process helper intended to open the live DB read-only aborted without useful runtime diagnostics. Instead the DB and its WAL were copied with app-context reads into private scratch. Host SQLite 3.53.4 opens that copy read-only, and `PRAGMA quick_check` returns `ok`. Its schema confirms all ten declared indexes, version 2 and **8,289 tracks**. The copied-device query plan is `SCAN musictag`. This is a host query-plan inspection of phone data, not an on-device query-plan/timing result or a guaranteed atomic snapshot of every live row.

Two offline copies are made through SQLite backup. One preserves the existing schema; the other gets `CREATE INDEX index_musictag_path ON musictag(path)`. The candidate plan becomes `SEARCH musictag USING INDEX index_musictag_path (path=?)`. Each batch uses 48 deterministic actual path hits and 16 missing paths; samples execute that batch twice (128 operations), after 64 warmup operations. Full returned rows are compared for all probes, not merely counts. Three fresh connection samples per variant run before/indexed/indexed/before/before/indexed. Probe results have the same digest in both series. The original database and the phone's schema remain untouched.

| Host lookup experiment | Existing schema median µs | Indexed median µs | Interpretation |
| --- | ---: | ---: | --- |
| Initial three samples/variant | 3,693.166 | 16.329 | 226.2× isolated lookup difference |
| Confirmation three samples/variant | 1,810.340 | 13.967 | 129.6×; timing varies, same plan/results |

All twelve individual samples are in [initial results](path-index-results.json) and [confirmation results](path-index-confirmation-results.json). The confirmation closes copy-building connections before timing and checks page counts/footprint. The indexed DB grows from 2,974 to 3,272 pages at 4,096 bytes/page: **1,220,608 bytes (1.164 MiB, 10.0%)**. Initial file-size readings before copy connection closure did not account for WAL content and must not be used to assess footprint; the initial harness and outputs are retained for transparency. Added index write/insert cost and Android performance are unmeasured. These host query improvements must not be translated into a streaming speedup or percentages added to previous optimizations.

## Decision and next implementation

Prioritize a **non-unique Room path index with a version 2→3 migration** before further socket changes. Preserve all matching rows and existing data; verify both fresh creation and migration from versions 1 and 2, including listening history and records sharing a path. Do not replace the list-returning query with LIMIT 1. Equality lookup is the measured target; prefix searches with LIKE are a separate planner/collation question.

Measure the index on Android during an explicitly observed scan, including unchanged-file scan rate, per-query time, worker CPU, database footprint and index maintenance cost. Compare streaming alone versus scanning plus streaming in separately labeled, repeated conditions with scan progress and thermal/UI state recorded. Use device-monotonic request/queue/decode/write timestamps and an explicit backoff event before attributing individual seeks. None of this migration/instrumentation is implemented in this step.

Keep the lazy FLAC and parser/copy release holds. Confirmed allocation savings, fewer captured collections and this background-query finding do not establish a robust playback/tail-latency improvement. The candidate remains restored for investigation.

## Evidence, failures and verification

[Raw request results](results.json), [recomputed worker summary](worker-summary.json), [copied-device schema/query plan](copied-device-query-plan.json), [final APK hash](final-apk-sha256.txt), [capture harness](profile-stream-workers.py), [analysis harness](analyze-stream-workers.py), [offline index harness](check-path-index.py).

Three compressed Simpleperf recordings, native CSV reports, callgraph reports, process inventories, capture logs and all host-index samples are archived here. Actual DB/WAL copies and APKs stay in private scratch; they are not included in this report bundle. Capture scratch is `/private/tmp/musicmate-worker-profile-20261003`; candidate APK remains `/private/tmp/musicmate-seek-profile-20261003/after.apk`. Unpack raw `.data.gz` to inspect with a compatible Simpleperf reporter; rerun the analysis harness against archived CSV/request/log files without a device.

Failures/controls are retained: root-only debuggerd refusal; direct run-as profiler property failure; a shell-app pilot with zero samples, excluded from conclusions; isolated query helper abort; attempted profiler binary hashing denied; a stale APK path after reinstall, corrected using the current package-manager path. The supported shell `--app` mode succeeds with all three recordings. Failed helper source is archived solely as failure evidence, not a supported tools/bench utility.

All 871 previously preserved production file hashes remain unchanged. Prior 222 core/UPnP tests remain applicable and were not rerun because no production code changed. Verification covers 528 measured requests, captured sample counts, recomputed CSV totals/quantiles, source/APK preservation, benchmark Python syntax, report links, trace compression/digests and scoped whitespace checks. [Verification](verification.json) and `manifest.json` preserve those checks. No commits or rollback were performed.
