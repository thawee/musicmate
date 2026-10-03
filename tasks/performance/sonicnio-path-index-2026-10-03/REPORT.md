# Room path index: migration and Android results

The non-unique path index is implemented with a data-preserving Room version 2→3 migration. **All 8,289 live tracks and 704 listening-history rows survive the upgrade with identical hashes across every field.** All 226 correctness tests pass. Android lookup time falls about 51× and fixed-work lookup-worker CPU falls 98.2%. Streaming latency does not improve in the controlled model: indexed phases behave like idle, while the busy unindexed workload has substantially faster seeks. Keep this as a database optimization candidate, without claiming a streaming or Netty advantage; existing lazy-FLAC and parser/copy release holds remain.

## Concept and production approach

The [worker-attribution investigation](../sonicnio-worker-profile-2026-10-03/REPORT.md) identified library scanning's repeated exact-path table scans. `TrackDao.getByPath` returns all records with a path. Add `@Index(value={"path"})` to TrackEntity, default non-unique, so records sharing a source file remain valid. Keep the equality query/list result unchanged.

Room database version changes from 2 to 3. Register `MIGRATION_2_3` alongside the existing 1→2 migration; it only creates `index_musictag_path` on musictag(path). Fresh databases receive the same index through Room's generated schema. Existing fallback behavior is unchanged, and the explicit supported migration is tested without destructive fallback. No selector, PCM, codec or admission-limit changes are included. [Room migration API](https://developer.android.com/reference/androidx/room/RoomDatabase.Builder) explains explicit migrations and destructive fallback behavior.

## Verification and live upgrade

Three isolated Android tests validate fresh creation/reopen, version 2→3 preservation, and the 1→2→3 chain. Tests verify a non-unique indexed query plan, duplicate/shared paths, Unicode/quote/wildcard path text, nullable paths, every seeded track field and existing listening-history fields. The v2 fixture is preserved from the pre-change Room-generated createAllTables statements, including its original identity hash. The v1 fixture reconstructs the documented pre-listening-history schema by omitting that table; it is not an independently exported historical v1 release schema. Room validates each final schema; these tests use no destructive fallback.

179 core + 43 UPnP + one Room entity unit test + three Android migration tests = **226 passing correctness cases**, no failures/errors/skips in the archived reports. Debug assembly succeeds. The benchmark is opt-in and runs separately in two successful AndroidJUnitRunner invocations. [Every correctness case](TEST_RESULTS.md).

Before installing, verify the preserved v2 APK and stop its process, then copy DB/WAL into private scratch. With no writers, a read-only host connection checks integrity and produces a consistent SQLite backup for private test assets. Before/after live upgrade snapshots have version 2/3 respectively, `quick_check=ok`, 8,289 tracks and 704 history records, with equal all-field hashes for both tables. Preservation is checked **before benchmark streaming requests**, which can legitimately update playback history. The candidate is launched successfully and remains installed; the old APK is retained as evidence rather than used to downgrade the migrated live database.

| APK | SHA-256 |
| --- | --- |
| Version 2 / before index | `81ad7cb39e44fa72ad529f8460471e3422848af0640488cd03d7e55550796444` |
| Version 3 / indexed candidate | `4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16` |

Only classes3.dex and classes4.dex differ in uncompressed APK contents; manifest/resources/packaged libraries match. [APK entry comparison](apk-comparison.json), [before preservation](before-preservation.json), [after preservation](after-preservation.json). The two changed production sources are archived before/after. The previous 871-file manifest excludes db-room; all its covered sources remain unchanged. Module changes for tests add the runner, isolated fixtures, an opt-in benchmark and cleartext HTTP in the test-only manifest.

## Android methodology

Samsung SM-S931B, Android 16/API 36, arm64; Room 2.8.5. Measurements use the phone's native SQLite engine from a separate test process, not host SQLite or the earlier failed app_process helper. The saved 8,289-track v2 database is a private asset supplied through a scratch Gradle init script; copies are created in the test process's cache. One preserves the schema, one gets the non-unique index. Both use a native SQLiteDatabase with WAL enabled. The live app stays on the indexed candidate throughout; indexed/unindexed contention is generated on these isolated copies. This is not an old/new server APK comparison.

For microbenchmarks, select 48 deterministic distinct path hits and 16 misses. Compare full returned fields on both copies for every probe. Each sample warms up with 64 operations and measures 128 operations, materializing cursor fields into a checksum. Initial order is before/indexed/indexed/before/before/indexed; confirmation reverses it. Three samples/variant/series, six samples/variant overall, **1,536 timed lookup operations total**. Android thread CPU and System.nanoTime wall time are recorded. All checksum/probe comparisons match. This models the exact query and cursor work, not Room entity construction or full file scanning.

Each streaming series fetches and hashes the full 29,529,404-byte WAV, then warms up with 64 HEADs and 16 seeks. Track 2122216336 and LG webOS user agent match preceding comparisons. The test process connects directly to phone localhost port 9000; this differs from earlier host/USB urllib measurements and cannot be pooled with them. HttpURLConnection requests explicitly close connections. TTFB ends at parsed response headers; device System.nanoTime timestamps avoid the prior host/device alignment uncertainty for client and model workers. Server queue/decode/write events and CPU-frequency traces are still absent.

A phase interleaves 64 HEADs, 64 header-only ranges and 48 deterministic 64 KiB seeks, paced at 20 requests/s outside timers. Unlike the earlier sequential request-kind blocks, seeks are interleaved early so they overlap lookup work. Every HEAD length/status and every range's exact bytes/status are verified. **Twelve phases ×176 = 2,112 measured requests**, including 576 seeks; warmups and two full references are additional.

Idle phases do no lookup work. Loaded phases submit exactly 2,048 queries on each of four named workers (8,192 queries/phase, 75% hits/25% misses) to a shared SQLiteDatabase. All workers start through a gate, each records start/end/thread CPU and must complete its quota within 45 seconds. This is fixed work: the indexed workers finish much earlier, rather than being forced to consume equal CPU by doing more queries. Initial phase order: idle/before/indexed/indexed/before/idle; confirmation: idle/indexed/before/before/indexed/idle. Workload checksums match across all eight loaded phases (65,536 queries total).

App UI is placed on HOME before measurement. This does not independently certify that all background jobs are idle. Battery temperature and thermal status are sampled before/after each phase; per-process GC counters are approximate statistics from the **benchmark process**, not the streaming app. Normal meminfo is not used. A full ScanAudioFileWorker additionally walks storage, checks file timestamps, reads/updates metadata/art and can deep-scan audio; those costs and actual scan file rates are outside this model.

## Every Android microbenchmark sample

| Sample | Schema | Wall µs/query | Thread CPU µs/query |
| --- | --- | ---: | ---: |
| initial 1 | before | 2603.848 | 2579.931 |
| initial 2 | indexed | 53.396 | 53.181 |
| initial 3 | indexed | 54.431 | 52.601 |
| initial 4 | before | 2856.941 | 2824.468 |
| initial 5 | before | 2734.399 | 2718.548 |
| initial 6 | indexed | 51.599 | 51.547 |
| confirmation 1 | indexed | 52.302 | 51.991 |
| confirmation 2 | before | 2687.950 | 2672.034 |
| confirmation 3 | before | 2692.366 | 2673.968 |
| confirmation 4 | indexed | 52.712 | 52.375 |
| confirmation 5 | indexed | 51.831 | 51.635 |
| confirmation 6 | before | 2691.209 | 2659.910 |

Across six samples/variant, median wall time is **2,691.788 → 52.507 µs/query (51.3×)**. Both native Android plans confirm table scan versus indexed search, and returned fields/checksums match. [All native plans and samples](phone-records.json). Database page counts rise from 2,974 to 3,272 at 4,096 bytes/page: **1,220,608 bytes (1.164 MiB, 10.0%)**, matching the earlier isolated host storage result. Index-maintenance/write cost is unmeasured.

## Every streaming and fixed-work phase

Each row has 48 seeks. Completion and CPU cover the four lookup workers only. Active-seek counts indicate workers were unfinished at request start; they are not causal attribution. Quantiles use interpolation at (N−1)×p.

| Phase | Lookup completion s | Worker CPU s | Seeks begun during lookups | Seek median ms | p95 ms | p99 ms | Maximum ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| initial 1: idle | 0.000 | 0.000 | 0/48 | 18.293 | 19.368 | 19.435 | 19.451 |
| initial 2: before | 7.599 | 29.367 | 48/48 | 4.918 | 5.675 | 6.240 | 6.633 |
| initial 3: indexed | 0.141 | 0.529 | 1/48 | 18.207 | 19.409 | 20.301 | 20.971 |
| initial 4: indexed | 0.131 | 0.508 | 1/48 | 17.864 | 19.127 | 19.259 | 19.295 |
| initial 5: before | 7.557 | 29.213 | 48/48 | 4.894 | 6.018 | 6.961 | 7.287 |
| initial 6: idle | 0.000 | 0.000 | 0/48 | 17.885 | 19.107 | 19.365 | 19.410 |
| confirmation 1: idle | 0.000 | 0.000 | 0/48 | 17.808 | 19.209 | 20.023 | 20.353 |
| confirmation 2: indexed | 0.145 | 0.542 | 1/48 | 17.873 | 18.834 | 19.096 | 19.227 |
| confirmation 3: before | 7.711 | 29.846 | 48/48 | 4.979 | 5.908 | 6.934 | 7.343 |
| confirmation 4: before | 9.091 | 35.262 | 48/48 | 5.049 | 7.530 | 8.830 | 9.125 |
| confirmation 5: indexed | 0.152 | 0.595 | 1/48 | 17.714 | 19.135 | 19.319 | 19.381 |
| confirmation 6: idle | 0.000 | 0.000 | 0/48 | 17.704 | 19.371 | 20.534 | 21.352 |

## Compared results

| Combined measurement | Idle | Unindexed lookup load | Indexed lookup load |
| --- | ---: | ---: | ---: |
| Seek median ms | 17.925 | 4.941 | 17.940 |
| Seek p95 ms | 19.317 | 6.737 | 19.222 |
| Seek p99 ms | 19.714 | 7.781 | 19.434 |
| Seek maximum ms | 21.352 | 9.125 | 20.971 |

There are 192 seeks per condition. Median lookup completion across four loaded phases falls **7.655 → 0.143 s (53.5×)**; median summed worker CPU falls **29.607 → 0.535 s (98.2%)**. These are measured Android query-work savings, not file-scan throughput or total app CPU savings.

Idle/indexed pooled seek median is approximately 17.9 ms, versus 4.94 ms under busy unindexed lookup load. The direction repeats after reversing load order. This is an adverse result for a claim of faster streaming after removing background work, but it does **not** isolate an index-induced server regression: all conditions run the same indexed server APK, and indexed phases resemble the idle controls. Other HEAD/header-range quantiles, raw temperatures, thermal state, GC counters and every request timestamp are preserved in [summary](phone-summary.json) and [records](phone-records.json).

Possible explanations include CPU-frequency/scheduling behavior under sustained load and the selector's existing 20 ms empty-select backoff. Neither is established by this experiment. A latency near 20 ms is insufficient to identify a sleep, and CPU work/counter readings do not provide server stack/wait attribution. Device client timing removes clock uncertainty for the model; explicit server events or matching scheduling/frequency traces are still needed.

## Retention decision and next measurement

Keep the path-index implementation as a database optimization candidate: native plans, lower lookup time/CPU, duplicate-path behavior and live data-preserving migration are proven. Do not present it as a streaming latency or throughput win. Existing lazy-FLAC and parser/copy release holds remain; the isolated contention model also needs a full real-scan follow-up and index write-cost measurement before a broad app-benefit claim.

Next profile **idle-path delays**, using device client timestamps against CPU scheduling/frequency traces and an explicit selector-backoff event. Separate producer decoding time, response queue delay and sleep/wakeup delay before changing selector/write policy. Do not retain expensive unindexed scans as a way to keep the CPU busy. No artificial CPU spin, power-mode change, Netty comparison or streaming-throughput claim was added.

## Evidence and reproduction

[Capture/measurement harness](PathIndexPhoneBenchmark.java), [migration tests](PathIndexMigrationTest.java), [historical v2 fixture](room-v2.sql), [analysis](analyze-path-index-phone.py), [raw initial instrumentation](phone-benchmark.log), [raw confirmation](phone-benchmark-confirmation.log), [all parsed measurements](phone-records.json), [computed summary](phone-summary.json), [all correctness results](TEST_RESULTS.md).

Private scratch `/private/tmp/musicmate-path-index-20261003` retains before/after APKs and DB/WAL copies. Build logs and the corrected benchmark-assets Gradle init script are archived; its hardcoded path must be adjusted for reproduction. The initial init-script build failed because project() was resolved on Gradle rather than rootProject; corrected build succeeds. Initial benchmark source is preserved; the final source adds only reversed-order selection for confirmation. No failed measurements are counted as passing results.

To rerun, supply an integrity-checked, checkpointed v2 database as scratch asset phone-v2.db, build the separate db-room Android test APK using the scratch asset override, install it, and run PathIndexPhoneBenchmark explicitly with `pathIndexBenchmark=true`, optionally `reverseOrder=true`. The current indexed app, real track and device are required. Ordinary correctness runs skip the opt-in benchmark. Reproduction never downgrades the migrated live DB or packages private data in repository sources.

Analysis only requires the two archived instrumentation logs. `manifest.json` hashes every other artifact. [Verification](verification.json) covers test counts, all complete phases/worker quotas/checksums, data/APK preservation, source scope, recomputed analysis, report links and scoped whitespace checks. Candidate APK/activity is restored after measurement. No commit or deployment was performed.
