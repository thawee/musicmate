# Streaming FLAC metadata skipping — 2026-10-03

Implemented a streaming metadata path that retains STREAMINFO and seek tables and seeks past unused payloads. **228 core/UPnP tests and all 5,632 measured phone requests pass.** Against the preserved candidate, combined idle converted-seek median falls **16.738 → 10.255 ms (−38.7%)**, p95 **19.600 → 12.248 ms (−37.5%)**, and maximum **23.012 → 16.701 ms (−27.4%)**. Improvement repeats with reversed APK order. Keep this change as a measured streaming optimization candidate; previous lazy-decoder/parser release holds remain separate.

## Concept and implementation

The [preceding trace investigation](../sonicnio-idle-seek-2026-10-03/REPORT.md) identified `FlacToWav.open → readAndHandleMetadataBlock → readFully` before response headers. The old metadata path allocates every block payload, reads it byte by byte and processes CRCs even when streaming only needs audio parameters and seek points. Large padding/artwork/tag blocks therefore consume worker CPU and memory without helping PCM output.

`FlacDecoder.readMetadataForAudio()` consumes remaining metadata, retaining the existing parsing of STREAMINFO and seek tables. Other payloads are skipped using the file input's seek operation. It checks the payload end against file length before seeking because RandomAccessFile permits seeking past EOF. Zero-length unused blocks require no seek. The final metadata position remains the audio origin for seek-table and blind seeking.

Both `FlacToWav.open` and its audio-write decoder use this path. The public block-returning API retains its behavior for tag/artwork callers, shares validation logic, and can precede the new API on the same decoder. Repeated calls after completion are harmless. Existing stream-info-first, duplicate STREAMINFO/seek-table, recognized-block length and truncation checks remain. Unknown/reserved block types retain the original decoder's acceptance behavior, including type 127; this change does not add format hardening. Audio frame CRCs remain checked after metadata seeks, as frame parsing resets CRC state at the frame boundary.

Only two production files change. [Exact before/after patch](production.patch), [baseline decoder](before-FlacDecoder.java), [candidate decoder](after-FlacDecoder.java), [candidate adapter](after-FlacToWav.java). No transport, selector, database schema or power-policy changes.

## Correctness tests and results

The complete core and UPnP JVM suites pass **185 + 43 = 228 cases**. Six new codec cases cover:

| New case | Verification | Result |
| --- | --- | --- |
| Large metadata and seek offsets | 16/24-bit full WAV equality; unaligned ranges after 1 MiB metadata, with and without a seek table; actual sequential samples | Pass |
| Mixed/repeated APIs | Legacy STREAMINFO/comment payload returned intact; streaming consumes remaining metadata; repeat/legacy completion retain position; seek sample matches | Pass |
| Truncated unused metadata | Partial headers and payloads fail with EOFException in both APIs, including one-byte-short payload | Pass |
| Duplicate/length checks | Duplicate STREAMINFO/seek tables, partial seek point, invalid STREAMINFO length retain legacy exception classes | Pass |
| STREAMINFO first | Non-STREAMINFO first block retains legacy failure | Pass |
| Audio CRC | Corrupted final audio CRC still raises `CRC-16 mismatch` after skipping 1 MiB metadata | Pass |

Existing conversion, range, workspace, protocol and server tests also pass. Debug assembly succeeds. A targeted blind-seek follow-up passes before the final full suite. [Exact test source](after-FlacToWavTest.java), [all suite counts](test-results.json), [individual XML results](test-xml/core/TEST-apincer.music.core.codec.FlacToWavTest.xml), [final suite log](final-suite.log), [successful build log](build-tests-corrected.log).

The initial CRC test expected IOException, but this decoder's DataFormatException is a RuntimeException. The test was corrected to assert the existing CRC error type and message; production CRC behavior required no fix. Initial multi-file patching failed on different comments in the two metadata loops; no partial production edit remained, and the patch was adapted after inspection. A scratch summary with literal escaped newlines failed Python syntax and was replaced by a standalone verifier. Archive source-path verification was corrected after inspecting the helper's actual output directory. Failed evidence is retained rather than counted as successful verification.

## Host allocation/time methodology

Two synthetic fixtures contain valid metadata but **no audio frames**: STREAMINFO-only (42 bytes), and STREAMINFO plus 1 MiB unused padding. The baseline is the preserved lazy-decoder source from this session, not HEAD or the eager decoder. Separate class outputs are placed first in each JVM classpath; the benchmark verifies actual decoder origins and source hashes.

For each fixture, six fresh JVMs run `before, after, after, before, before, after`. Each performs one preflight, 64 warmups and three measured samples of 128 scans. In total: **12 JVMs, 36 samples, 4,608 measured scans**. ThreadMXBean measures allocated bytes; nanoTime measures elapsed time. Every scan checks all STREAMINFO fields, metadata-end/input position, byte alignment and physical EOF; preflight also checks repeated completion. Results aggregate by median within each process, then median across the three processes per variant.

| Fixture | Before bytes/scan | After bytes/scan | Before µs/scan | After µs/scan |
| --- | ---: | ---: | ---: | ---: |
| STREAMINFO only | 8,784 | 8,744 | 130.616 | 132.320 |
| 1 MiB unused padding | 1,057,416 | 8,744 | 4,882.986 | 189.115 |

Padding allocation falls **99.17%**, avoiding 1,048,672 bytes/scan; measured time falls **96.13%**. STREAMINFO-only timing shows no improvement. These host values include cached filesystem access, reflection and validation overhead; they are not ART allocation, audio throughput or network measurements. Candidate padding process medians vary from 180 to 550 µs. No confidence interval or fixed regression threshold is inferred.

Initial preflight found that the existing low-level input's EOF refill can change its reported position after returning −1. The benchmark now checks exact logical completion/idempotence before the terminal physical-EOF probe. That pre-existing behavior is not modified. [All 36 samples](micro/results.jsonl), [every process/sample summary](micro/summary.json), [commands and origins](micro/commands.json), [benchmark](FlacMetadataSkipBenchmark.java), [runner](run-metadata-skip-comparison.py). Initial failed preflight evidence is in `micro-initial-preflight`.

## Phone comparison approach and methodology

Samsung SM-S931B, Android 16/API 36, arm64, <device-serial>. Both APKs retain the existing version-3 indexed database and settings. No live downgrade, new migration or private-database replacement occurs. Only classes11.dex and classes13.dex differ in uncompressed APK contents; manifest/resources/libraries match. [APK comparison](apk-comparison.json).

- Baseline SHA-256: `4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16`.
- Candidate SHA-256: `71ba2ccd4fd269db33d15adc6bd5728af569ab1394800a7c88172097435e0e78`.
- Initial series installs **baseline, candidate, candidate, baseline**. Confirmation installs **candidate, baseline, baseline, candidate**. Every run installs and launches its variant, waits three seconds, puts the UI on HOME, checks installed hash/PID and runs the separate Android benchmark APK.
- Initial runs use six measured phases: two idle, two unindexed-copy lookup loads, two indexed-copy lookup loads. Load-phase order reverses on alternating runs. Confirmation uses two idle phases/run. This gives **32 phases, eight invocations and 5,632 measured requests**.
- Each phase interleaves 64 converted HEADs, 64 GETs of the 44-byte WAV header and 48 converted 64 KiB seeks, paced at 20 requests/s outside the timer. Requests use device-local HTTP/9000, Connection close and the same real FLAC track. The benchmark first prepares private native SQLite copies, performs lookup samples, fetches a full reference and warms up with 64 HEADs/16 seeks. This setup is identical between variants.
- All statuses, Content-Length values, WAV headers, full reference hash and range bytes are checked. Reference SHA-256 remains `edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676`. Instrumentation must report `OK (1 test)` and successful completion; shell exit status alone is insufficient.
- Latency is device nanoTime from before connection/response retrieval until headers are returned, including client parsing. No Perfetto, Simpleperf, client trace sections or ordinary meminfo runs during these comparisons.
- Unindexed/indexed contention uses four workers executing 8,192 fixed queries/phase on private copies of the 8,289-track database, not the live app database or a complete library scan. Indexed work usually finishes early; per-request active-worker counters preserve actual overlap. See [all phase distributions/environments](phone-summary.json).
- Boundary battery temperatures range **29.2–35.6 °C**, thermal status is 0 throughout. Recorded GC counters describe the benchmark process, not server allocation/GC. Server frequency, PSS and power are not measured here.

## Every phone invocation

Each row aggregates its two idle phases: 128 HEADs, 128 header GETs and 96 seeks. Initial invocations additionally verify 704 requests under lookup contention; confirmation verifies idle only. Percentiles interpolate sorted observations. [Every phase/kind statistic](every-phase.csv), [initial raw records](initial-results.json), [confirmation raw records](confirmation-results.json), and the matching eight `.log` files preserve all samples and checks.

| Run | Idle HEAD median, ms | Idle header median, ms | Idle seek median, ms |
| --- | ---: | ---: | ---: |
| initial-0-before | 8.236 | 17.810 | 17.674 |
| initial-1-after | 6.963 | 10.079 | 10.263 |
| initial-2-after | 6.753 | 9.983 | 10.311 |
| initial-3-before | 8.156 | 18.056 | 17.992 |
| confirmation-0-after | 5.235 | 9.440 | 9.708 |
| confirmation-1-before | 8.054 | 15.982 | 16.809 |
| confirmation-2-before | 8.280 | 15.803 | 15.200 |
| confirmation-3-after | 7.497 | 10.420 | 10.539 |

| Pooled converted seeks | n/variant | Before median | After median | Before p95 | After p95 | Before max | After max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Initial idle | 192 | 17.819 | 10.298 | 19.688 | 13.049 | 20.399 | 16.701 |
| Confirmation idle | 192 | 15.742 | 10.227 | 19.371 | 11.647 | 23.012 | 13.314 |
| Combined idle | 384 | 16.738 | 10.255 | 19.600 | 12.248 | 23.012 | 16.701 |
| Initial unindexed work | 192 | 5.068 | 3.748 | 7.199 | 5.051 | 8.071 | 7.880 |
| Initial indexed work | 192 | 17.865 | 10.084 | 19.436 | 11.340 | 20.285 | 13.128 |

All values are milliseconds. Combined idle seek p99 falls **20.309 → 14.514 ms (−28.5%)**. Combined idle HEAD median falls **8.136 → 6.262 ms**, header GET **17.591 → 10.059 ms** (512 observations/kind/variant). All distributions, including p99, are available in the computed JSON/CSV. Initial idle median improves 42.2%; confirmation improves 35.0%. Baseline latency changes between series, but the improvement direction repeats; pooled samples do not establish independent statistical trials.

The metadata change improves this fixture's latency under all three tested conditions. Sustained unindexed work still produces lower latencies than idle, so this does not settle the prior runtime/frequency question. Install/startup work, cache state, thermal drift and background jobs remain imperfect controls even with reversed order. Results concern one FLAC track and device-local requests, not diverse metadata layouts, Wi-Fi playback, sustained bulk throughput or a current Netty comparison. Host allocation savings must not be presented as measured server ART allocation or lower PSS.

## Verification and decision

The main agent reviewed the delegated host benchmark and recomputed phone distributions from raw instrumentation records. Prior recorded hashes verify **884 other files unchanged**; the two expected production files are captured before/after. The main diff was inspected but main lacks these current classes, so [production.patch](production.patch) and preserved hashes define this step's scope. Unit-test changes, benchmark tooling and documentation are separate. Python syntax, local report links, scoped whitespace and artifact digests are checked. [Verification](verification.json), [source hashes](source-manifest.json), [unchanged hashes](unchanged-source-manifest.json), [final installed state](final-device-state.json).

Keep the streaming metadata-skip implementation: large unused-block allocation savings, valid audio/seek/CRC behavior and repeated phone latency improvement are demonstrated. Existing lazy-FLAC/parser release holds are not resolved by a comparison where both APKs contain those changes. Next validate a broader FLAC metadata corpus and long-running streaming before release decisions; measure native/converted throughput separately if pursuing a Netty comparison. Remaining ~10 ms idle seeks are not evidence to remove selector backoff.

Reproduction: preserve the same baseline sources/APK; export the core test classpath, then run `run-metadata-skip-comparison.py FRESH_MICRO_SCRATCH PRESERVED_DECODER`. This runner refuses to overwrite existing evidence. Supply the private database asset and separate test APK using the preceding path-index procedure. With before.apk/after.apk in scratch, run `run-metadata-skip-phone.py SCRATCH ADB SERIAL initial` then `confirmation`, followed by `analyze-metadata-skip-phone.py SCRATCH`. The runner checks hashes and restores the candidate in finally. Both APKs must retain the migrated database schema. Private audio/database contents and APK binaries stay in local scratch; only synthetic fixtures, code and measured evidence are archived here.
