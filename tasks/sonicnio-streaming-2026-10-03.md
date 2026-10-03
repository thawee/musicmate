# SonicNIO streaming changes — 2026-10-03

Implemented the reliability and bounded-resource changes from the Grizzly comparison, preserving selector ownership and the existing file-transfer path. No Grizzly dependency was added.

Detailed concepts, implementation approach, measurement definitions, per-scenario methodology/results, before/after and historical engine comparisons, reproduction instructions and archived evidence: [PERFORMANCE.md](../PERFORMANCE.md). The [individual automated results](performance/sonicnio-2026-10-03/TEST_RESULTS.md) list all 199 retained core/UPnP test cases.

Subsequent throughput experiments and current verification are recorded in [PERFORMANCE.md §10](../PERFORMANCE.md#10-throughput-follow-up): bounded PCM batching, configurable file budgets retaining 256 KiB defaults, synthetic/device comparisons and [205 passing test cases](performance/sonicnio-throughput-2026-10-03/TEST_RESULTS.md). The measurements below preserve the earlier reliability-change baseline.

## Behavior

- Each connection/request has a diagnostic id. The last 64 response outcomes and 64 connection closes record expected/actual body bytes, first-byte latency, duration, close reason and bounded failure text. Totals and maximum worker/selector delays remain available after history rolls over. Transport completion does not mean renderer playback completion.
- Header/body-read deadlines, handler execution, keep-alive idleness, socket-write stalls and producer stalls are separate. Elapsed time is monotonic; only successfully transmitted bytes refresh write progress. Parked producers are swept too. Handler/write/producer limits default to 120 seconds and are configurable.
- Generated audio yields after 256 KiB per selector turn, retaining partially written buffers. Producer wakeups carry a response identity so a completed response cannot rearm a later request. Stop rejects late publication and releases responses created after shutdown.
- Media admission uses atomic slots and returns 503 when full. It never evicts admitted audio. Artwork/WebUI files have 16 separate transfer slots. Same-connection seeks reuse their connection; new-connection seeks at capacity receive 503 until an old transfer releases a slot.
- HTTP handlers have 128 pending queue entries. Conversion has at most 8 producer threads and no pending producer queue. The shared 8 MiB audio allocation budget includes queued, pending and producer-held chunks. Cancellation/failure releases reservations exactly once.
- WebSocket callbacks have an independent two-thread pool, ordered per-session drains and 256 pending callbacks per session. Overflow closes the session and reserves its terminal callback after admitted messages.

## Validation

- Full core suite: 156 tests passed. UPnP suite: 43 tests passed. Debug APK builds and was installed on the connected Galaxy S25.
- A 32-client, 16-second socket soak completed 14,318 operations with zero errors; payload checks and connection/slot cleanup passed. Host-loopback throughput is not a phone Wi-Fi measurement.
- After adding failure context, the 54-test socket suite passed 20 repetitions with FINE trace logging and 20 without trace logging: 2,160 successful test executions. A separate bounded stress check passed 5,000 HTTP/1.0 close-response exchanges.
- New regressions cover exact diagnostic body counts, parked-producer deadlines, separation of producer/write/handler phases, fair writes with partial buffers, aggregate buffer cancellation, HTTP overload with active WebSocket control, producer saturation, concurrent media admission, artwork at full media capacity and late responses after stop. The long healthy-file regression runs beyond both its header and write-stall durations while continuing to make progress.
- Device: original FLAC SHA-256 and converted WAV SHA-256 match the before-build outputs; 16 original/converted range checks and four concurrent original transfers passed. Both FLAC and ALAC PCM match Apple afconvert (16-bit stereo, 44.1 kHz). The selected FLAC's STREAMINFO MD5 is all zero, so it cannot serve as a checksum reference.
- Device: four audio transfers alongside 32 artwork requests completed with correct hashes; three MP3 ranges match the whole-file bytes. Recent checked logcat entries contain no streaming-stall report or fatal exception.

USB comparison, same 9,940,667-byte FLAC; one sample per build with 12 seek requests:

| Measurement | Before | After |
|---|---:|---:|
| Whole-file duration | 0.274 s | 0.276 s |
| Seek first-byte p50 | 19.6 ms | 8.7 ms |
| Seek first-byte worst of 12 | 21.6 ms | 15.3 ms |
| Four-transfer aggregate rate | 39.9 MiB/s | 40.6 MiB/s |
| Converted WAV duration | 2.593 s | 1.251 s |

These samples verify behavior and give an initial comparison; cache/JIT/network variation prevents attributing the timing differences solely to these changes. A 261 MiB Wi-Fi baseline failed to finish its first transfer after 11 minutes and was stopped. A smaller original FLAC Wi-Fi transfer completed in 7.315 s before installation. USB results do not establish a Wi-Fi improvement.

## Outstanding and deferred

- The first repetition batch observed one HTTP/1.0 test failure on run 4: its parsed response had no Connection header. Raw-header failure context was added afterwards. Subsequent 40 full socket-suite runs and 5,000 focused exchanges did not reproduce it. Its cause remains unknown; do not claim the pre-existing intermittent failures are fixed.
- Physical HiBy/LG/Sony renderer playback, pause/resume behavior and audible continuity remain unverified. Software LG user-agent conversion checks are not physical-TV playback checks. The earlier possible MQA dropout has not been reproduced and is not established as fixed.
- Keep the initial timeout/queue/buffer defaults configurable; broader renderer measurements may justify tuning them.
- Buffer pooling and file-reader isolation are deferred: current checks do not establish allocation/GC or cold-storage selector stalls as a playback bottleneck. Idle PSS snapshots (317,194 KiB before and 270,362 KiB after) are not controlled allocation/GC measurements and do not demonstrate a memory improvement. Evaluate these optimizations only with repeatable device evidence.
- A broad main comparison includes earlier branch work; incremental changes were reviewed against HEAD and whitespace checked separately. Existing unrelated task/lesson changes were preserved.
