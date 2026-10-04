# WAV tags and NPE audit (2026-10-04)

- [x] Find why WAV embedded tags/cover art are not read. Not release-only (debug same). Genre: JThinkReader replaced the ID3 genre with an absent TXXX:GENRE. Cover: MediaMetadataRetriever ignores ID3 pictures in WAV.
- [x] Fix the demonstrated cause; verified on the emulator (debug DB: Classical/Folk; release: 4 genres, gold/green covers, Tech Info 100%, MP3s unchanged).
- [x] NPE audit: getPackage (WavTag, OkHttp) fixed earlier; JustDSD not shipped; track-getter dereferences reviewed (13, 2 guarded + 3 container titles); Kotlin !! (3, QR dialog race fixed); system services and listFiles/getParentFile chains reviewed.
- [x] Fixes committed; 328 JVM tests pass (no existing reader tests; verified on emulator instead).
- [ ] Release 3.23.2 after the fixes are verified on device/emulator.

# Release 3.23.1 (2026-10-04)

- [x] Version 3.23.1 / code 143; changelog and release notes.
- [x] 328 JVM tests; optimized APK on the phone: startup clean, Tech Info opens, 352/352 streaming requests with full-WAV SHA-256, UPnP description 200, no crashes.
- [x] Tag v3.23.1, push master and tag atomically, verify CI asset/notes and the shipped DEX (FFmpegKit members kept). Published; all six previously missing FFmpegKit members present in the CI APK.
- [ ] Follow-up: 3.23.1 still crashes scanning WAV files (fixed in bf56925e, unreleased); WAV embedded tags/art not read (to investigate).

# Song preview / tag editor UX fixes (2026-10-04)

Crash fix committed separately (96ea1785, FFmpegKit keep rules). Remaining review items:
- [x] 1. Save reflects pending edits on both preview and editor (editor typing lights it up; dim when nothing to save).
- [x] 2. Change Cover reads as a button (add-photo icon).
- [x] 3. Selection mode shows "N Selected" (Move/Convert/Remove/Select All moved to overflow). Correction: the long-press label was already right; the review wrongly assumed listener tap mode, but this phone uses edit mode.
- [x] 4. Editor form fills the space above the keyboard (adjustResize + overlap-only IME padding) and restores after it closes.
- [x] 5. "Year" label reads "Year / Date".
- [x] 6. Bottom panel padding/margins tightened (modest). User decision (2026-10-04): keep Organize/More in the editor.
- [x] 7. Discard and Remove-art dialogs: opaque dark panel, light Cancel, red destructive button. Remaining: a brown band over the button area is drawn above the app's views (persists with the panel painted solid); it existed before.
- [x] 8. Back from the editor returns to the preview; Discard resets text edits in place (other pending drafts keep close-and-discard).
- [x] 9. Section headings use the app's gold accent.
- [x] 10. Copy Diagnostics is a quiet text button.
- [x] 11. "Reload Tags", "Save Art", "Remove Art" (red); icons dropped so labels fit.
- [x] 12. Tech Info headings: Tag Check, Loudness (ReplayGain), Embedded Cover Art, File Location, All Embedded Tags, Audio Stream (FFmpeg).
- [x] Build, 328 unit tests, phone check of each item (test edits discarded, no tag writes), changelog, commit.

# Main screen: single Play button (2026-10-03)

- [x] Replace the Play + Shuffle pills beside the search stats with one Play pill; shuffle stays in the now-playing controls.
- [x] When shuffle mode is on, start results playback at a random track (shuffle mode keeps the starting track first).
- [x] Remove the unused shuffle parameter from onPlayResults/playCurrentResults; add a view-model test (10/10 MainViewModelTest, UiLayoutPolicyTest pass).
- [x] On-device check (optimized build signed with the phone key, data kept): one Play pill shows; shuffle off starts at the first result; shuffle on gave three different random starts. Shuffle was turned back off and playback paused.
- [x] Main-screen UX fixes, tested on the phone: opaque mini player, solid scroll-to-top button, marquee only while playing, and compact badges at large text (duration inline at 1.3x). 109 app unit tests pass.
- [x] Changelog/UI.md updated and committed.
- [x] Follow-up, tested on the phone: NEW/DL badge becomes a dot on list artwork (spoken label kept), menu mark on the logo, equaliser icon for Music Center, "Days of Music" with playtime before size, and singular "1 Track"/"1 Song".
- [x] Library size removed from the main header (kept for search results); About shows "22.9 Days of Music • 286.68 GB" and includes it in the share text. Verified on the phone.

# SonicNIO external server research (2026-10-03)

## Release 3.23.0

Objective: publish the retained changes as 3.23.0 / version code 142, push the code and tag, and verify the GitHub release asset. User confirmed retained changes only; lazy FLAC workspace allocation and the combined parser/copy experiment stay excluded.

### 1. Lock release scope and preserve evidence
- [x] Inspect the existing tag-triggered release workflow and preserve the experimental candidate.
- [x] Exclude both held experiments from active production code and their experiment-specific tests; retain archived sources/results.
- [x] Set version 3.23.0 / code 142 and draft CHANGELOG.md and RELEASE_NOTES.md.

### 2. Resolve optimized APK startup failure
- [x] Reproduce the failure in the correctly signed optimized APK without uninstalling or resetting app data.
- [x] Identify the missing reflected String constructor, datatype generic signatures and CSV generic signatures through diagnostic logs and R8 mapping.
- [x] Add targeted jUPnP consumer rules and retain warning/error logs in normal release builds.
- [x] Confirm the latest normal release build succeeds; inspect mapping/DEX for retained reflective members and generic signatures.
- [x] Install a scratch-signed copy of that exact optimized APK, start a fresh process and confirm UPnP initializes without listener shutdown.
- [x] If startup fails, capture the retained error, update this plan and fix the demonstrated cause before proceeding. (Startup passed; the CSV-signature build is runtime-clean for UPnP.)

### 2b. Fix jaudiotagger reflection in optimized builds (user approved for 3.23.0)
Found via newly retained error logs: ASF chunk readers fail `Class.newInstance()` (constructors removed) and ID3 frame bodies are looked up with `Class.forName("org.jaudiotagger.tag.id3.framebody.FrameBody" + id)`, but R8 renamed or removed them (only 26 survive, renamed). 3.22.0 shipped the same configuration.
- [x] Reproduce: ASF reader instantiation errors observed at runtime (33). ID3 confirmed statically: only 26/108 frame bodies survive, renamed. A phone MP3 tag read was not triggered (reads need a library scan or the tag editor), and the library has no WMA files, so synthetic files were not added.
- [x] Add targeted keep rules in library/jaudiotagger-android/consumer-rules.pro for every reflective lookup (frame bodies by name and copy constructor, ASF chunk readers).
- [x] Rebuild release; verify mapping/DEX keeps the original frame-body names and constructors; rerun JVM suites. (108/108 names and all constructors kept; ASF readers keep `<init>()`; 219 JVM cases pass.)
- [x] Reinstall (data preserved) and confirm no jaudiotagger reflection errors in the scenario that produced them. (Zero errors.)

### 3. Prove retained-only release correctness
- [x] Pass core, UPnP and Room JVM suites: 219 cases, zero failures/errors.
- [x] Pass all three Android Room index/migration cases.
- [x] Pass the optimized APK's full converted-audio SHA-256 check and all 352 measured HEAD/header/seek requests. (CSV-fixed APK; rerun on the final APK.)
- [x] Verify UPnP description and representative control actions, including ContentDirectory and ConnectionManager, on the optimized APK. (CSV-fixed APK; rerun on the final APK.)
- [x] Confirm the installed app version, process identity and APK provenance; preserve live app data and database version 3. (Indirect: install -r, firstInstallTime unchanged, Browse reports 8,289 tracks; run-as is unavailable on the release build.)
- [x] Repeat startup, streaming, UPnP and provenance gates on the final APK with the jaudiotagger rules. (352/352, SHA-256 OK, 9 SOAP actions 200, installed hash matches.)

### 4. Finalize documentation and review the commit
- [x] Archive successful and failed release checks, APK/source hashes and descriptive latency results in the release report.
- [x] Add the final release scope and verification results to PERFORMANCE.md; distinguish historical candidate comparisons from this release's correctness check. Claim no new throughput or Netty advantage.
- [x] Finalize changelog/release notes, including the reflection fix and held changes; verify release workflow uses the notes.
- [x] Stage only intended code, tests, documentation and curated evidence. Exclude caches, private database fixtures, signing keys and scratch APKs.
- [x] Review the staged diff against the previous release/default branch, check archive integrity and run git diff --check. (Archived CRLF CSVs left untouched because they are hash-pinned.)

### 5. Publish and verify
- [x] Create the release commit and annotated v3.23.0 tag after all runtime gates pass. (eb6fbed3; a clean worktree reproduced the tested DEX byte for byte.)
- [x] Push master and v3.23.0 atomically to origin; never force-push.
- [x] Verify GitHub Actions completes and the v3.23.0 release contains its expected APK and release notes. (Run 37135807310 succeeded. The CI DEX differs only in obfuscated names; it keeps all reflection targets, and the shipped APK passed startup, 352/352 streaming requests and the UPnP description check on the phone.)
- [x] Report the release URL, commit, version and verification totals.

Status: all runtime gates pass on the final APK (jUPnP + jaudiotagger rules). No release commit, tag or push has been made. Evidence redaction is done (device serial replaced, manifest hashes cascaded); the user chose to commit all evidence, including the large raw traces.

## Streaming metadata skipping

- [x] Preserve current sources/APK and add a streaming metadata API that skips unused payloads without weakening existing validation.
- [x] Verify malformed/truncated metadata, mixed APIs, seeking and byte-identical PCM; measure large-metadata CPU/allocation against preserved baseline.
- [x] Build and compare baseline/candidate on Android with reversed order and verified ranges; archive every result and restore candidate.

Result: [metadata-skip implementation/results](performance/sonicnio-metadata-skip-2026-10-03/REPORT.md). Two production files add/use a streaming-specific API; 228 core/UPnP tests pass. Host 1 MiB unused metadata allocation −99.17%; metadata time −96.13%, with no STREAMINFO-only timing improvement. Eight phone runs/5,632 requests pass; combined idle seeks median 16.738→10.255 ms (−38.7%), p95 19.600→12.248 ms, max 23.012→16.701 ms, direction repeats in reversed order. Candidate restored; retain this optimization candidate while earlier release holds remain.

## Idle seek attribution

- [x] Capture idle seek timestamps and scheduling/frequency traces on the current candidate, without changing server behavior.
- [x] Attribute slow requests using device monotonic clock snapshots; distinguish running, runnable and sleeping time.
- [x] Confirm the leading cause with a bounded stack/event measurement if needed, archive evidence and state the next justified change.

Result: [idle seek attribution](performance/sonicnio-idle-seek-2026-10-03/REPORT.md). Nine invocations verify 3,168 requests; four traces align 1,408 intervals with device clocks/client markers. Confirmation median 13.871 ms includes 8.818 ms worker CPU; CPU stacks identify FLAC metadata reading/CRC before headers. Traced medians differ from ~18 ms controls, so no optimization gain is claimed. Initial worker PID metadata is incomplete; validated confirmation inventories recover all eight workers. Next independently test unused metadata skipping, preserving validation/seek offsets/audio. Production unchanged, candidate restored, release holds remain.

## Room path index and Android validation

- [x] Preserve version-2 schema/APK/database evidence and implement a non-unique path index with migration 2→3.
- [x] Verify fresh creation and migration chains from versions 1/2, duplicate paths and listening-history preservation on Android.
- [x] Build/install the candidate after preservation checks; compare Android path lookup and controlled streaming contention without downgrading the live database.
- [x] Archive every measurement, validation and limitation; update the performance decision and restore the candidate.

Result: [Android index results](performance/sonicnio-path-index-2026-10-03/REPORT.md). Implemented migration/index; 226 correctness cases pass, live 8,289 tracks/704 history rows preserved exactly. Native lookup 51.3× faster, fixed-work worker CPU −98.2%, storage +1.164 MiB. All 2,112 requests pass; idle/indexed seeks ~18 ms versus busy unindexed ~5 ms, confirmed in reversed order. Retain as database candidate without streaming gain; existing release holds remain. Next profile idle/backoff delays. Candidate installed/restored.

## Busy-worker attribution

- [x] Verify device stack-capture capabilities and preserve the installed APK identity.
- [x] Capture bounded worker stacks and correlate their functions with the prior CPU trace.
- [x] Measure the identified path lookup on isolated database copies, without changing the live database.
- [x] Record evidence, instrumentation limits and the next controlled streaming measurement; verify production sources remain unchanged.

Result: [worker attribution](performance/sonicnio-worker-profile-2026-10-03/REPORT.md). 528 verified requests and three CPU-stack captures identify library scanning/SQLite path lookup. Phone DB copy has 8,289 tracks and no path index; host indexed lookup is 130–226× faster with identical results and +1.164 MiB storage. Scan completion confounds foreground/background comparison; Android benefit remains unmeasured. Next candidate: non-unique path index with preserving Room migration. Production/live schema unchanged; candidate restored; release holds remain.

## Seek-tail and memory profiling

- [x] Verify available Android tracing, GC and scheduling telemetry without changing streaming behavior.
- [x] Capture matched baseline/candidate workloads with profiling controls, preserve traces and validate bodies/APK identity.
- [x] Document attributable differences and limits, verify evidence and leave the candidate APK restored.

Result: [profile report](performance/sonicnio-seek-profile-2026-10-03/REPORT.md). All 1,408 comparative requests passed, five traces archived including pilot. Captured GC cycles 18 → 7; recorded suspension scopes ≤1.12 ms. PSS increase not consistently reproduced; unnamed pool workers dominate CPU and ±24 ms clock alignment prevents reliable request attribution. Release hold remains; production hashes unchanged, candidate APK restored.

## Lazy FLAC physical-phone comparison

- [x] Preserve baseline/candidate sources and build APKs differing only in lazy decoder allocation; verify connected device and workload.
- [x] Run interleaved, warmed-up phone comparisons with body/range verification, converted seeks, metadata-only latency and available memory/GC telemetry.
- [x] Analyze every run, preserve APK hashes/raw evidence and document limits; leave candidate source/APK restored.

Result: [phone report](performance/sonicnio-lazy-flac-2026-10-03/PHONE_RESULTS.md). Twelve external-only app runs plus six intrusive instrumentation-control runs, failed unpaced attempt preserved, and eighteen isolated ART samples. ART allocation −99.1%; app throughput −0.3%, median converted-seek run maxima +6.6%, sampled maximum PSS +14.1%. Pooled seek p99 improves; latency is mixed and exact app GC activity is unmeasured. Hold lazy decoder change from release pending profiling; candidate source/build/APK restored, no rollback performed.

## Lazy FLAC decoder scratch allocation

- [x] Defer FrameDecoder construction until the first audio read/seek, preserving metadata readiness and closed-state checks.
- [x] Verify metadata-only reads avoid scratch allocation, decoder reuse and byte-identical PCM/ranges with core/UPnP tests.
- [x] Measure isolated metadata allocation before/after and document results and remaining runtime limits.

Result: [PERFORMANCE section 14](../PERFORMANCE.md#14-lazy-flac-decoder-scratch-allocation), [full methods and evidence](performance/sonicnio-lazy-flac-2026-10-03/REPORT.md). Metadata probe allocation 1,057,392 → 8,752 bytes (−99.2%) in both host series; 222 core/UPnP tests pass. Audio workspace is still allocated once when decoding begins. Android follow-up is now complete in the phone report above; release recommendation is hold pending profiling.

## Streaming allocation audit

- [x] Inspect file, generated-audio and decoder allocation sites and their frequency.
- [x] Identify ownership-safe reductions and distinguish already measured savings from unmeasured candidates.
- [x] Record prioritized findings with source locations; do not change streaming behavior during this audit.

Result: [allocation audit](sonicnio-streaming-allocations-2026-10-03.md). First candidate is lazy FLAC frame-decoder initialization: metadata-only scanning currently creates two 65,536-element long buffers (1 MiB element storage). Other candidates include metadata skipping, seek workspace reuse, PCM chunk recycling and response-bound callbacks. No production changes or new performance claims.

## one-nio source review

- [x] Inspect a pinned one-nio revision, license, runtime/native requirements and benchmark scope.
- [x] Compare transfer, write queues, readiness handling and HTTP parsing with SonicNIO; identify Android-compatible ideas and measured-risk constraints.
- [x] Record source-linked recommendations and validation gates without changing production code or claiming unmeasured gains.

Result: [one-nio source review](sonicnio-one-nio-research-2026-10-03.md). Profile seek tails and selector backoff first; then independently measure a bounded selector-owned immediate-write experiment. Native transport replacement, body parser and shared buffers are deferred. No new benchmark results or production changes.

Context: encoding and metadata changes are recommended for retention; the combined parser/copy change is held from release pending investigation of higher phone seek maxima. Those changes remain in the working tree/test APK.

## Request framing and copy reduction follow-up

- [x] Preserve current source/APK baseline and establish parser benchmarks with small headers and large buffered bodies.
- [x] Remove redundant full-buffer/body copies; retain existing incremental header scanning and public parse behavior.
- [x] Validate repeated Content-Length and Transfer-Encoding combinations against RFC 9112, with fragmentation and socket regression coverage.
- [x] Measure copy-only and final variants independently, run core/UPnP tests and device checks, and document results and compatibility changes.

Policy: normalize repeated identical Content-Length values; reject conflicting/invalid lengths and TE+CL with 400 and close. Chunked remains unsupported. No broad parser replacement.

Result: [PERFORMANCE.md §13](../PERFORMANCE.md#13-request-framing-and-copy-reduction), [full report and evidence](performance/sonicnio-parser-2026-10-03/REPORT.md). 360 extraction and 24 host transfer samples; twelve interleaved phone runs after an adverse initial latency result; 219 unit/socket cases and seventeen Android framing probes pass. Staged 1 MiB allocation falls 66.8%. Bulk throughput stays essentially unchanged. Phone seek maximum rises 25.3% across both series, despite a lower median; retain correctness/copy savings without claiming an end-to-end speedup. Final APK installed and verified.

- [ ] Follow-up: profile the repeated Android seek-tail regression (read/parse, worker wait, metadata, first successful write, client/header timing and GC) before further throughput tuning. Empty-request micro timings also vary substantially; identify runtime effects instead of treating the favorable model as proof.

## Next experiment: one file-metadata read per response

- [x] Preserve the optimized-encoding baseline and add a constructor-focused comparison that works with both variants.
- [x] Read size/mtime together once per FileResponse; derive ETag and Last-Modified from that snapshot. Verify unchanged stable-file responses and fresh metadata after replacement.
- [x] Run interleaved host preparation/transfer measurements, core/UPnP regression tests, debug build and matched phone checks; retain only supported behavior and publish all results.

Scope: remove repeated filesystem metadata calls without a cross-request cache. Preserve the prior encoding optimization and streaming settings.

Result: [PERFORMANCE.md §12](../PERFORMANCE.md#12-file-metadata-snapshot-follow-up), [report and all evidence](performance/sonicnio-stat-2026-10-03/REPORT.md). Conditional/range preparation medians fall 76.7%/21.9%, with +144 allocated bytes per response. Phone seek median falls 14.679 → 9.539 ms, while the seek maximum rises 6.3%; throughput stays unchanged. Initial host tail regression prompted 18 additional confirmation samples; no consistent tail advantage is established. All 209 tests, build, body/metadata checks and installed-APK verification pass. Tested APK remains installed.

- [x] Inspect hella-http and yelmach/http-server source, project requirements and stated reuse licenses.
- [x] Compare concrete transfer, buffering, parser and event-loop implementations with current SonicNIO; identify useful patterns and compatibility/correctness constraints.
- [x] Record source-linked recommendations, implementation sketches and measurement gates; distinguish research from implemented or benchmarked gains.

Delivery: [source review and implementation proposals](sonicnio-http-server-research-2026-10-03.md), pinned to both reviewed revisions. Priorities: ETag/date/header allocation, framing-header validation and incremental scanning, then PCM buffer reuse only if profiling supports it. Research/documentation only; no new throughput or Netty advantage claimed.

# SonicNIO throughput experiments (2026-10-03)

- [x] Capture a repeatable current-code host baseline and preserve source/evidence before tuning.
- [x] Implement configurable, contention-aware file write budgets and bounded PCM batching; test bytes, partial writes, short/failing producers and cancellation ownership.
- [x] Compare repeated identical host workloads, including seek contention; retain supported defaults and run core/UPnP checks and debug build. Existing socket coverage checks HTTP overload with active WebSocket control.
- [x] Update PERFORMANCE.md with methods, measurements, decisions and device/Netty verification limits.

Result: PCM batching enabled; shared/solo file budgets retain 256 KiB defaults because 512 KiB/1 MiB experiments did not establish benefit. 99 complete host measurements, nine matched-content phone runs, exact byte/range checks and 162 core + 43 UPnP cases pass. Synthetic 4 KiB generated-body throughput median 169.054 → 1,070.370 MiB/s (6.33×); phone WAV duration 0.852291 → 0.830846 s, with no substantial throughput gain established. Some native host medians were 2.8–5.1% lower; preserve this limitation and require controlled profiling rather than claiming a native speedup. A discovered queue-space/partial-buffer race was diagnosed and regressed. Final APK installed and its SHA-256 verified. Details and artifacts: [PERFORMANCE.md §10](../PERFORMANCE.md#10-throughput-follow-up), [205 individual test results](performance/sonicnio-throughput-2026-10-03/TEST_RESULTS.md).

# SonicNIO performance documentation (2026-10-03)

- [x] Inventory design decisions, benchmark scripts, retained device measurements and individual JUnit results; identify missing evidence and comparison limits.
- [x] Create a durable performance document with concepts, implementation approach, methodology, per-test results, before/after and historical Netty comparisons, reproducible commands and outstanding measurements.
- [x] Validate totals, calculations, source links and documentation diffs; link the report from the existing streaming summary.

Delivery: [PERFORMANCE.md](../PERFORMANCE.md), [199 individual test results](performance/sonicnio-2026-10-03/TEST_RESULTS.md), four device JSON artifacts, normalized JUnit/repeat evidence, source/evidence digests and five archived session harnesses. Verified 35 suites / 199 passing cases, 40 repeat runs / 2,160 executions, before/after percentage calculations, local links and manifest digests. Documentation-only work; benchmarks were not rerun. Netty attribution, unmatched Wi-Fi and missing raw artifacts remain explicitly qualified.

# SonicNIO streaming reliability plan (2026-10-03)

Objective: improve playback continuity, seek responsiveness and bounded resource use using Grizzly's lifecycle, backpressure and monitoring patterns. Implementation and validation results: [sonicnio-streaming-2026-10-03.md](sonicnio-streaming-2026-10-03.md). Reliability/resource changes are implemented; the unexplained intermittent header failure and physical-renderer checks remain open.

Constraints: preserve ADR-036 selector ownership, serial WebSocket callbacks, exact response framing and non-blocking stop. Keep the existing file transfer path and bounded producer queue. No additional HTTP engine dependency. The earlier HiBy/MQA dropout remains an independent, unconfirmed report; these changes must not be presented as its fix without reproduction.

## Phase 1 — Establish evidence and diagnose connection closures
- [x] Add lightweight per-response lifecycle measurements: request/connection identifier, first successful write, last successful write, declared body length, body bytes sent, terminal outcome and close reason. Distinguish header bytes from body bytes and transport completion from playback completion.
- [x] Preserve producer failure causes in StreamingResponse; emit one terminal event per response and one close event per connection, including disconnect, eviction, shutdown and failure. Bound diagnostic storage and avoid per-chunk logging.
- [x] Measure selector-turn duration, worker queue depth/wait and generated-buffer occupancy. Use monotonic elapsed time for new measurements.
- [ ] Reproduce the known premature-close test failures (including midStreamDisconnect and stop_releases under suite load), capture fresh reports and fix the demonstrated cause with a regression before optimizing.
- [ ] Capture a device baseline: original FLAC/MP3, FLAC/ALAC converted to WAV, single and concurrent streams, seek during artwork/browse bursts. Record bytes received, transfer failures, first-byte/seek p50 and p95, CPU, memory and GC activity.

Acceptance: every exercised terminal path has a distinguishable outcome; diagnostics do not retain tracks or connections indefinitely; repeated lifecycle tests pass and observed failures have explained causes. A bounded repeat run is evidence, not proof that all races are eliminated.

## Phase 2 — Progress deadlines and fair generated writes
- [x] Separate header/body-read, handler, keep-alive, socket-write-stall and producer-stall states/deadlines. Update write progress only when bytes are transmitted; do not reset it merely because OP_WRITE fires.
- [x] Include parked producers in deadline evaluation without rearming OP_WRITE. Distinguish an empty producer queue from a full queue awaiting a slow reader; cancel and release resources exactly once on expiry.
- [x] Make stall limits configurable and choose defaults from baseline renderer behavior. Use monotonic elapsed time; healthy streams may run longer than any stall limit.
- [x] Apply a per-selector-turn byte budget to StreamingResponse.write(), initially matching FileResponse's 256 KB budget. Preserve pending buffer offsets, producer parking/wakeup and partial header writes.
- [x] Test a zero-progress writer, stalled producer, slow but progressing reader, producer wakeup races, long healthy stream and two concurrent streams plus a small response. Use controlled clocks/channel doubles or latches instead of timing-sensitive sleeps where feasible.

Acceptance: stalled work is reclaimed, successful slow playback survives, parked producers do not spin, response bytes remain identical, and a continuously replenished producer yields predictably.

## Phase 3 — Bound overload and protect active playback
- [x] Replace the unbounded HTTP worker queue with configurable bounded admission. Handle rejection on the selector with a self-delimiting 503 response; release request/response resources and handle shutdown races. Audit shared SerialExecutor/WebSocket submission so saturation cannot strand callbacks or suppress cleanup.
- [x] Audit artwork versus audio stream-slot admission and concurrent acquire/eviction. Prefer refusing excess new work over interrupting healthy audio; define explicit treatment for artwork and seek replacement requests, with bounded total connections/file descriptors.
- [x] Enforce an aggregate generated-audio buffer budget in addition to the existing 16 x 64 KB per-response queue. Include pending buffers and producer-held chunks; keep producer concurrency bounded and reclaim budget on all terminal paths.
- [x] Test request bursts, artwork while audio slots are occupied, concurrent admission, slow readers, overload during stop and WebSocket callback ordering/rejection. Verify excess work gets a controlled response and admitted playback continues.

Acceptance: queue, producer and buffer usage stay within configured bounds; overload cannot silently close a healthy audio stream or leak slots, descriptors or callbacks.

## Phase 4 — Measurement-gated optimizations
- [ ] Evaluate a bounded reusable pool for generated-audio chunks only if allocation/GC measurements justify it. Define ownership from producer through queue/pending write to release; test partial writes, cancellation and failure. Retain only if device comparisons show a useful improvement.
- [ ] Measure cold-file and slow-storage transferTo latency on the selector. Add a bounded file-reader fallback only if measured stalls harm other requests; workers read files, selector still writes sockets, and byte-range behavior remains exact.

Acceptance: any retained optimization improves the measured target without worsening transfer correctness, memory bounds or seek latency. Record a decision to defer when evidence does not justify added complexity.

## Verification and delivery checkpoints
- [x] After each phase, run relevant core socket/streaming/diagnostics tests; repeat only to investigate changes or unresolved races. Final coverage includes NioHttpServerTest, NioHttpServerFuzzTest, NioHttpServerSoakTest and ServerDiagnosticsTest, plus impacted UPnP tests and assembleDebug.
- [x] Review scoped diffs against main and run git diff --check. Keep existing unrelated task entries and worktree changes intact.
- [ ] Repeat the baseline device scenarios with tools/bench/stream-bench.sh and byte/hash verification for original/ranged/converted content. Exercise disconnect/reconnect, seek bursts and artwork/browse bursts; compare latency, CPU, memory, GC and failed transfers with the baseline under comparable network conditions.
- [ ] Verify playback on available real renderers; record unavailable renderer checks as outstanding. Network transfer success alone does not establish audible playback continuity.
- [x] Update DESIGN.md/ADR-036 and CHANGELOG for implemented behavior and limits; summarize results and deferred optimizations. Deliver each phase as a separately reviewable change.

Current verification: 156 core + 43 UPnP tests pass; 32-client soak passes. After an initial unexplained HTTP/1.0 failure, 20 trace-enabled and 20 ordinary socket-suite repetitions plus 5,000 focused close exchanges pass. Device original/converted bytes, FLAC/ALAC independent PCM references, MP3 ranges and artwork bursts pass. Keep the premature-close investigation open; defer pooling/storage isolation and broad renderer tuning until evidence is available.

# DLNA interruption when cover art loads; possibly MQA (2026-10-03)

- [x] Trace artwork requests and audio writes for shared blocking work or stream contention.
- [ ] Identify the affected track and verify its actual format/encoded rate (initially reported as 24/192; user now suspects MQA), then reproduce before choosing a diagnostic comparison.
- [ ] Once the cause is established, add a focused regression and implement the smallest supported fix.
- [ ] Run relevant tests/build, review scoped diffs against main, and verify runtime behavior where available.
- [ ] Record findings and any remaining physical-renderer verification.

User update: cannot locate the track now; it may have been MQA. Format and cause remain unconfirmed. Keep playback code unchanged pending reproduction; artwork appearing at the dropout is correlation, not proof of image-decoding contention.

Investigation: user confirmed HiBy renderer, started from MusicMate. `MediaServerHubImpl.createDidlLiteMetadata()` sends no albumArtURI on this route; `NioWebServerImpl.createSongResponse()` serves the original file for HiBy. Embedded-art decoding on the renderer is a hypothesis, not confirmed. `schedulePreloadNextTrack()` also immediately pre-warms up to 4 MB of the next track for HiBy; phone logs show that work at track start. Recent logs do not capture the reported dropout and show the selected HiBy unavailable. No playback code changed: need the affected track to distinguish renderer image decoding from host/network contention before choosing a fix. Browser artwork uses a separate worker for resolution/thumbnail decoding, though file writes share the selector and stream slots with audio.

# UX/UI improvements (review: tasks/ux-review-2026-10-02.md)

## Batch 1: P1 quick wins
- [x] 1. Drawer version from BuildConfig (MainScaffold.kt:220)
- [x] 2. Hide player app build number outside Diagnostics (PlayerNameUtils)
- [x] 3. Play / Play Next on song detail
- [x] 4. Play all / Shuffle for the current list and search results
- [x] 5. Track menu (Add to Playlist dropped: no single-track playlist add exists): Go to Artist, Go to Album, icon for Open in External App

- [x] Device check (S25): drawer v3.21.0, Play/Shuffle on "queen" (35 tracks, shuffled queue), Go to Artist (21 Queen tracks, Back clears), song page Play
- [x] Filter label: "Artist: Queen • 21 Tracks" (verified on device)

## Batch 2: P2 polish
- [x] 6. Music Center button icon
- [x] 7. Queue source chips: Replenish to header, fade edge, clearer counter, one count
- [x] 8. Server tab clients as rows, friendly browser name
- [x] 9. Output picker: "This phone" with live route
- [x] 10. Now Playing scrim and album line

- [x] Device check (S25): speaker icon, queue fade and no 20/20, Server CONNECTED list (Chrome browser), "Phone Speaker" in picker and mini player, Now Playing "Queen • The Works"
- [x] Scroll-to-top FAB centered (verified on device)
- [x] Screenshot references regenerated after inspecting renders (34/34 pass); found and fixed: 200% text pills, pills in selection mode, faded first letter
- [x] Volume slider matches seek bar family
- [x] Delete moved into song page More… (last, red, batch count); verified on device
- [x] Add to Playlist: track menu, song page More…, multi-select; persisted in custom_playlists.json; verified on device (create, add, duplicate skipped, survives restart)
- [x] Remove from Playlist + Delete playlist (long-press own card); verified on device, test playlist "UX Test" removed with it
- [ ] Flaky: NioHttpServerTest midStreamDisconnect / stop_releases fail under full-suite load, pass alone

## Verification
- [ ] Unit tests + assembleDebug; update screenshot references touched
- [ ] Install on device and re-capture each changed screen
- [ ] UI.md, USER_GUIDE.md, CHANGELOG Unreleased

# MQA display fixes

## Documentation and commit follow-up

- [x] Update DESIGN.md, UI.md, and CHANGELOG.md to describe the implemented MQA behavior. (DESIGN "Native MQA metadata presentation", UI.md MQA precedence, CHANGELOG MQA entries)
- [x] Stage only MQA changes and verify the staged source snapshot builds and passes app tests.
- [x] Commit the MQA fix and documentation; preserve unrelated working-tree changes. (85065968; confirmed 2026-10-02)
- [ ] Device: real MQA / MQA Studio files, track changes, compact and wide layouts, both player modes (mqa-display-fix-plan.md step 4)
- [x] Record reference images for MqaScreenshotTest: 5 of 8 recorded after inspection (audio details, badges compact/large text/wider, playback)
- [x] Fixed and recorded the last 3 (audio details 200% text, full screen default and 200% text): VU header one line, dial sized by width as well as height, dial lettering capped by dial size, FlowRow for the spec rows, shrinking output chip, fixed preview clock. All 8 MQA previews pass; the other 22 screenshot failures are the same stale baselines as before

Plan and verification: `tasks/mqa-display-fix-plan.md`. Implemented; 94 app tests and debug build pass. Eight previews visually inspected; screenshot references and remaining live-device matrix pending.

- [x] Add MQA/MQA Studio regression cases.
- [x] Share native quality labels and colors; prioritize MQA over generic PCM tiers.
- [x] Show encoded and original sample rates in player details and badge accessibility descriptions.
- [x] Verify compact/wide layouts, focused and app tests, debug build, screenshots, and scoped diffs.
- [x] Update UI/user documentation and record runtime verification limits.

# Full-Height Music Center (Eliminate Empty Black Gap Above Sheet)

## Status: 🟢 Completed & Verified

### Objective
Expand Music Center (`AudioHubSheet.kt`) to full height (filling available vertical space ~95-100% like Spotify and Apple Music), eliminating the empty black backdrop above the previous 65% clamped sheet.

### Checklist
- [x] **Task 1: Update `AudioHubSheet.kt` to Full Height**
  - [x] Remove hardcoded `sheetHeight = screenHeightDp * 0.65f` clamping.
  - [x] Set root content Column to `Modifier.fillMaxSize()`.
  - [x] Ensure `Box` container in `AudioHubContainer` uses `fillMaxSize()` for MODAL and PREVIEW presentations.
- [x] **Task 2: Verify Layout Behavior across Tabs**
  - [x] `NowPlayingPage.kt`: Verify album art flip container and controls expand gracefully with `.weight(1f)` without clipping.
  - [x] `QueuePage.kt`: Verify queue list takes full height without clipping.
  - [x] `MediaServerPage.kt`: Verify scrollable cards display properly with expanded space.
- [x] **Task 3: Unit Tests, Screenshots & Regression Testing**
  - [x] Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`: All tests pass cleanly.
  - [x] Run `./gradlew assembleDebug`: Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
  - [x] Check git diff hygiene (`git diff --check` passes with 0 warnings).

---

# Fix Music Center Play & Next Controls (Items 1 to 4)

## Status: 🟢 Completed & Verified

### Objective
Resolve the 4 identified issues in Music Center -> Playback screen and underlying playback control architecture:
1. Permit playback start from Play & Next in `NowPlayingPage.kt` via `canStartPlayback` and `UiLayoutPolicy.playControlsEnabled`.
2. Dim Next and Previous button icons when disabled (`Color(0x66FFFFFF)` vs `Color.White`).
3. Differentiate user-initiated skips from natural looping on `RepeatMode.ONE` in `QueueManager.java` and `MusicMateServiceImpl.java`.
4. Prioritize queue order (`getCurrentTrack()` / `getNextTrack()`) over random selection when starting playback from the queue in `MainActivity.java`.

### Checklist
- [x] **Task 1: Permit Playback Start in `NowPlayingPage.kt` & `UiLayoutPolicy.kt`**
  - [x] Add `playControlsEnabled` to `UiLayoutPolicy.kt` and tests in `UiLayoutPolicyTest.kt`.
  - [x] Support `canStartPlayback` parameter in `NowPlayingPage.kt` and wire it in `AudioHubSheet.kt`.
- [x] **Task 2: Correct Disabled Icon Tint for Next and Previous in `NowPlayingPage.kt`**
  - [x] Apply `if (playControlsEnabled) Color.White else Color(0x66FFFFFF)` to Next icon.
  - [x] Apply `if (controlsEnabled) Color.White else Color(0x66FFFFFF)` to Previous icon.
- [x] **Task 3: Differentiate User Skip from Natural Loop on `RepeatMode.ONE`**
  - [x] Overload `QueueManager.getNextTrack(boolean forceSkip)` to bypass `RepeatMode.ONE` on user skip.
  - [x] Call `getNextTrack(true)` for explicit skip actions in `MusicMateServiceImpl.java`, while keeping `getNextTrack(false)` for natural completion.
  - [x] Add regression tests in `QueueManagerTest.java`.
- [x] **Task 4: Preserve Queue Order on Initial Playback in `MainActivity.java`**
  - [x] In `onDockPlayPauseClicked()`, query `qm.getCurrentTrack()` or `qm.getNextTrack()` when shuffle is off, falling back to random only when shuffle is on or no track is found.
- [x] **Task 5: Verification & Polish**
  - [x] Run `:core:testDebugUnitTest` and `:app:testDebugUnitTest`: all unit tests pass cleanly.
  - [x] Check git diff hygiene: `git diff --check` passes with zero warnings.

---

# Compact Smart Queue Header & Playlist Integration

### Objective
1. **Drastically reduce vertical space footprint of Queue header:**
   - Consolidate Toolbar (40dp) and Engine HUD (32dp) into a single 34dp header bar.
   - Replace bulky 52dp 2-line cards with ultra-compact 28dp micro-capsule chips (`[🖐️ Manual]`, `[✨ New]`, `[📥 DL]`, `[🧭 Discover]`, `[⏳ Rediscover]`, `[📋 Playlist ▾]`).
   - Remove the 40dp static intelligence info box (retain only transient error micro-banners).
   - Cut total header height from ~196dp to ~60dp (~68% reduction), maximizing visible track listing inside the 65% height bottom sheet.
2. **Support Load from Playlist:**
   - **Pattern A (Action): Instant Load/Append to Queue:** Add `PlaylistPickerDialog` to directly replace ("Play All") or append ("+ Queue") all playable tracks of any playlist (Smart or Custom) into the queue.
   - **Pattern B (Smart Source): Playlist as Auto-Refill Engine:** Add `QueueManager.Source.PLAYLIST` and `activePlaylistName` to continuously auto-fill up to 20 unplayed tracks matching the selected playlist.

### Checklist
- [x] **Phase 1: Ultra-Compact Queue Header Redesign (`QueuePage.kt`)**
  - [x] Merge Top Toolbar and Engine HUD into a unified 34dp bar with title, live jewel LED dot, track count/time, inline slots badge, and compact focus/clear/playlist actions.
  - [x] Redesign `SmartSourceDeck` into sleek single-line 28dp micro-capsules without subtitles.
  - [x] Remove static `SmartSourceInfoBanner` container; keep only conditional error micro-alerts.
- [x] **Phase 2: Playlist Selection & Loading Implementation**
  - [x] Create `PlaylistPickerDialog.kt` composable to display all available playlists from `PlaylistRepository.getPlaylists()`.
  - [x] Implement instant actions: "Play All" (replaces queue) and "Add to Queue" (appends).
  - [x] Add `Source.PLAYLIST` and `activePlaylistName` to `QueueManager.java` for continuous smart auto-refill from a chosen playlist.
  - [x] Connect `QueuePage.kt` Playlist chip & toolbar icon to open picker and switch active playlist.
  - [x] Add playlist button in `QueueEmptyState`.
- [x] **Phase 3: Verification & Polish**
  - [x] Run unit tests (`:app:testDebugUnitTest`, `:core:test`): all 102 unit tests pass cleanly.
  - [x] Verify build via `./gradlew assembleDebug`: clean APK generated.
  - [x] Verified `git diff --check`: zero warnings.

---

# Smart Queue UI/UX Overhaul: Audiophile Console & Visual Discovery

## Status: 🟢 Completed & Hardened

### Objective
Elevate the Smart Queue UI/UX from a plain utility list into an immersive, audiophile-grade smart console with tactile source switching, real-time refill HUD, cover-art thumbnails, audio fidelity badges, and clear visual queue zoning (Now Playing vs. Manual Play Next vs. Smart Refill).

### Checklist
- [x] **Phase 1: Audiophile Smart Source Deck & Engine HUD (`QueuePage.kt`)**
  - Replaced generic Material dropdown menu with a horizontally scrollable obsidian segmented pill deck for the 5 sources (Manual, New, Downloads, Discover, Rediscover) with distinct accent colors and jewel borders.
  - Implemented live "Engine HUD": pulsing status LED indicator (Emerald pulse for active auto-fill, Amber for refreshing, Slate for manual), slot count badge (`SLOTS: 18/20`), and manual refill button with haptic feedback.
  - Added frosted glassmorphic intelligence banner explaining the active smart algorithm and auto-refill rules.
- [x] **Phase 2: High-Fidelity Queue Items with Cover Art & Codec Badges**
  - Added 42dp cover-art squircle thumbnails with Coil `AsyncImage` and dark background fallback.
  - Added animated vector equalizer overlay and gold border/title accent for the active playing track.
  - Integrated compact audiophile quality badge (`QualityBadge`) for Hi-Res, DSD, 24-bit, and CD quality tracks.
  - Added subtle track provenance micro-badge (`NOW PLAYING`, `PLAY NEXT`, `SMART`) to distinguish user choices from smart auto-fill.
- [x] **Phase 3: Visual Queue Zoning & Rich Empty State**
  - Grouped and sectioned upcoming tracks visually with categorized dividers (`NOW PLAYING`, `UP NEXT · MANUAL PRIORITY`, `✦ SMART REFILL · [SOURCE]`).
  - Designed an audiophile empty-state graphic with quick-start action pills (`[✨ Try Discoveries]`, `[Browse Library]`).
  - Polished swipe-to-dismiss gesture with crimson gradient and haptic resistance.
- [x] **Phase 4: Verification, Lint & Build**
  - 117 unit tests passed across `:app`, `:core`, and `:server` modules; 3 Python SQLite migration tests passed.
  - Assembled clean debug APK (`app-debug.apk`).
  - Updated `UI.md`, `CHANGELOG.md`, and recorded UI lessons in `tasks/lessons.md`.

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite tests.
- **Build:** Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
- **Hygiene:** `git diff --check` passes with zero warnings.
- **Device Limitations:** Physical device display and touch interaction remain pending runtime verification.

---

# Listening History and Discovery Sources

## Status: 🟢 Implemented & Verified (Device Review Pending)

- [x] Add durable history storage separate from queue state, with a non-destructive database migration (`listening_history` table, `MIGRATION_1_2`, RoomDbHelper, tested with SQLite script).
- [x] Implement playback accounting with regression tests for pauses, seeks, duplicate events, same-track handoffs/repeats, completion and skips (`ListeningHistoryTracker`, 8 core unit tests).
- [x] Wire service playback events and add Unplayed/Rediscover sources using the approved 90%/30-day defaults (`MusicMateServiceImpl`, `QueueManager`, `QueuePage.kt`).
- [x] Run core/app tests and debug build, update documentation, and record device verification limitations (117 unit tests pass across all modules, debug APK built, `USER_GUIDE.md` and `CHANGELOG.md` updated).

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite migration/query tests.
- **Build:** `./gradlew assembleDebug` succeeds cleanly (`app/build/outputs/apk/debug/app-debug.apk`).
- **Device Limitations:** Physical device playback telemetry, UI interaction, and audio transitions remain unverified in runtime environment due to device access constraints.

---

# Smart Queue Implementation

- [x] Implement persisted Manual/New/Downloads source state and bounded refill with session exclusions and manual priority.
- [x] Integrate background refresh and shared source selector in queue UI, with accurate category descriptions and empty states.
- [x] Add six queue regressions for stable next tracks/manual priority, removal suppression after restart, anchor restoration/manual freeze, bounded refill/new candidates, clear-during-query races, and Downloads/missing files. All 40 core and 51 app tests pass; debug APK built; diff check clean.
- Detailed approved definitions: `tasks/smart-queue-plan.md`.
- Device playback, source-menu rendering, and process-restart journeys remain unverified because device-tool instruction access was blocked earlier in this session.

---

# Preview Simplification and Save Feedback

- [x] Quiet genre, cover controls, metadata borders, and utility colors; center button content and reserve indicator space while keeping five actions.
- [x] Connect saving/success/failure feedback to both save paths and clear delayed feedback on destruction. Source review complete; device interactions remain unverified.
- [x] Build debug APK, run app tests, inspect resources/diff, and document visual verification limits. Debug assembly succeeds; 51 app tests pass; diff clean. XML checks confirm all five centered actions. Updated UI/changelog. New rendering, large-font behavior, and interactive save outcomes still require device verification; device-tool instruction access remains blocked in this session.

---

# Studio Command Dock — Implemented; Device Review Pending

## Objective
- Redesign the five tag-preview actions into one console-like control surface without removing any action.

## Checklist
- [x] Charcoal dock surface with 24dp top corners, hairline top border, and 16dp side padding.
- [x] Evenly spaced utility row with vector icons, 48dp targets, hairline separators, and neutral styling; removed Organize's filled pill.
- [x] 65/35 split between graphite Edit Song Info and Save, both 52dp with 14dp corners.
- [x] Save indicator covering clean, unsaved, saving, and saved states with accessibility state descriptions.
- [x] Brief press compression plus existing haptics; long-press shortcuts preserved.
- [x] Build, app tests, and diff checks pass; all five action IDs retained.

## Verification limit
- Device visual and interactive review remains pending; device-tool instruction access was denied earlier in this session.

---

# Compact Tag Preview — Implemented; Device Review Pending

## Objective
- Improve first-screen density after the user confirmed folder scrolling works. Detailed plan: `tasks/plan.md`.

## Checklist
- [x] Review the proposed compact layout with the user. Approved: “do it”.
- [x] Compact the centered square artwork and title using available viewport space. Java/resources compile; source checked for restoring full-width editor artwork. Device transition verification pending.
- [x] Group genre/audio badges and consolidate labeled metadata rows. Compose compilation passes; navigation callbacks preserved and source reviewed.
- [ ] Compare against the reference screenshot; target all metadata visible at default font on that phone.
- [x] Build, run app tests, check diff, and update UI/release documentation. Debug assembly and app tests passed; diff clean. Source review covers 48dp controls, 56dp metadata rows, wrapping, and viewport bounds; runtime responsive/accessibility checks remain pending.

## Verification limit
- The same-device screenshot comparison and interactive preview/editor checks remain pending because device-tool instruction access was denied earlier in this session. Do not treat the first-screen fit target as visually verified.

---

# Preview Screen Visual Fixes

## Objective
- Resolve screenshot review findings while preserving all five bottom actions and existing editing work.

## Plan and verification
- [x] Replace fixed preview header sizing with scrollable content bounded above the measured action dock; match and center artwork controls. Java/resource compilation passed.
- [x] Align labeled metadata rows, clarify track counts, unify vector icons/accent colors, and improve badge contrast and wrapping. Debug assembly and app unit tests passed.
- [x] Build debug APK, run app unit tests, inspect diff, and verify rendering on an available device if access permits. Debug assembly and 51 app tests pass; `git diff --check` passes. XML comparison confirms all five bottom action definitions are unchanged. Device rendering remains unverified because access to the required device-interaction reference was denied.

## Result
- Preview scrolls independently above the dock; editor return-to-preview requires an actual expanding gesture. Removed the obsolete pager dock margin to avoid double-reserving footer space.
- Matching 48dp artwork controls, labeled metadata rows, localized track counts, consistent navigation icons/colors, wrapping badges, and brighter preview DR colors implemented.
- Updated UI documentation and Unreleased notes. APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

# Statusline Overhaul: Single-Line HUD with Remaining Quota

## Status: 🟢 Completed & Hardened

### Objectives
1. **Compact Single-Line Statusline:**
   - Migrate quota display from a second line to the primary line directly following `Google AI Pro` (plan tier) separated by `│`, eliminating vertical clutter and the newline.
2. **Quota Metrics Format Update:**
   - Switch from used percentages to remaining percentages (`% left`).
   - Format each quota section as `<reset_time> <percentage>% left` (e.g., `4h31m 100% left` and `6d4h 93% left`).
   - Remove wide horizontal progress bars to keep single-line width within standard terminal boundaries (~100 chars).
3. **Color Inversion for Remaining Quota:**
   - Implement `color_for_remaining_pct()` so high quota (>60%) displays green, moderate (36-60%) yellow, low (16-35%) orange, and critical (<=15%) red.
   - Retain `color_for_pct()` for used metrics like context window usage (`ctx_pct`).
4. **Performance & Reliability Hardening:**
   - Single-invocation `jq` execution for all JSON data extraction and arithmetic.
   - Full ISO 8601 timezone offset parsing (`+07:00`, `-05:00`, `.123Z`, raw epoch).
   - Stale/elapsed countdown handling (suppresses stale negative timers when quota already reset).
   - Strict zero-stderr failsafe exit on invalid/empty JSON inputs.

---

### Master Checklist

- [x] **Phase 1: Statusline Script Refactor (`~/.gemini/statusline.sh`)**
  - [x] Backup current script to `~/.gemini/statusline.sh.bak3`
  - [x] Update jq extraction to calculate remaining percentages (`% left`) from `remaining_fraction`, `remaining_percentage`, or `used_percentage`
  - [x] Add `color_for_remaining_pct()` function with appropriate color bands
  - [x] Format 5h quota as `${c_muted}${q5h_rel}${reset} ${q5h_color}${q5h_pct}% left${reset}`
  - [x] Format weekly quota as `${c_muted}${qwk_rel}${reset} ${qwk_color}${qwk_pct}% left${reset}`
  - [x] Append quotas directly to `line1` after `plan_tier` separated by `${sep}` without newline
  - [x] Remove `line2` multiline output

- [x] **Phase 2: Comprehensive Test Suite & Verification**
  - [x] Test with user image scenario: Gemini 3.8 Flash, 22% context, `musicmate (master*)`, `Google AI Pro`, 5h quota (4h31m, 100% left), 7d quota (6d4h, 93% left)
  - [x] Test with Claude Code rate_limits payload
  - [x] Test with edge cases (empty JSON, missing quota, detached HEAD, missing plan tier)
  - [x] Verify ANSI 24-bit TrueColor sequences across remaining quota gradient (100% -> green, 70% -> green, 50% -> yellow, 25% -> orange, 5% -> red)

- [x] **Phase 3: Stability Hardening & Edge-Case Audit**
  - [x] RFC3339 / ISO 8601 parser in `jq` supporting UTC `Z`, subseconds, and timezone offsets (`+07:00`, `-05:00`)
  - [x] Prevent stale countdowns: when reset timestamp is in the past (>60s elapsed), suppress countdown and show pure `% left`
  - [x] Remove newline from empty/corrupt fallback (`printf "agy"`) to ensure clean TUI rendering

---

## Review & Verification

### Verification Summary
1. **Single-Line Rendering Verification:**
   - Contiguous single-line HUD:
     `Gemini 3.8 Flash (High) │ 󰍛 22% │ musicmate (master*) │ Google AI Pro │ 4h31m 100% left │ 6d4h 93% left`
   - Verified no extra newlines or trailing newline breaks in terminal TUIs.
2. **Quota Calculations & Formatting:**
   - Evaluated `remaining_fraction` (1.0 -> `100% left`, 0.93 -> `93% left`).
   - Verified fallback for Claude Code `used_percentage` (`100 - used_percentage`).
   - Relative reset times (`calc_relative_time`) accurately format hours/minutes (`4h31m`) and days/hours (`6d4h`).
3. **Adaptive Color Grading:**
   - Tested 5 quota levels: 100% (green), 70% (green), 50% (yellow), 25% (orange), 5% (red).
   - Time string is styled in `c_muted` matching the Tokyo Night theme hierarchy.
4. **Resilience & Fallbacks:**
   - Tested 5 corrupt/null input variations: zero stderr bytes, clean exits with code 0.
   - Tested non-git directories and detached HEAD states.

---

# Tag Activity Preview Refinement & Dialog Lifecycle Hardening

## Status: 🟢 Completed & Verified

### Objectives
1. **Tag Preview Screen UX:**
   - Eliminate duplicated artist and album overlay text (`panel_artist`) on cover art scrim.
   - Retain prominent song title overlay while delegating discography navigation to interactive Compose [`StudioProvenanceSection`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/AudioBadges.kt).
2. **Compose Dialog Lifecycle Resolution:**
   - Fix fatal `IllegalStateException: ViewTreeLifecycleOwner not found from androidx.compose.ui.platform.ComposeView` when launching Search & Match dialogs.
   - Standardize on `ComponentDialog` with explicit ViewTree owner bindings.
3. **Menu & Performance Hygiene:**
   - Purge obsolete commented-out actions in `tag_more_actions_menu.xml` and `TagsActivity.java`.
   - Add in-memory `LruCache` for third-party music app icons in `PlayerPickerDialog.kt`.

### Checklist
- [x] Remove `panel_artist` from `activity_tags.xml` and `TagsActivity.java`
- [x] Migrate `showSearchQueryDialog` and `showSearchResultsDialog` to `ComponentDialog`
- [x] Bind `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner`
- [x] Clean up `tag_more_actions_menu.xml` and `TagsActivity.doShowMoreActions()`
- [x] Implement `appIconCache` in `PlayerPickerDialog.kt`
- [x] Record pattern in `tasks/lessons.md`
- [x] Update `UI.md` and `CHANGELOG.md`
- [x] Run unit tests and deploy debug build to physical device (`<device-serial>`)

---

# Whole-App Functional and UI/UX Audit

## Objective
- Review the Android app end to end for reproducible functional defects and user-facing usability/accessibility issues without changing application code or overwriting existing work.

## Checklist
- [x] Map app entry points, primary flows, test coverage, and available verification environment.
- [x] Inspect representative user flows and UI resources for concrete issues; distinguish confirmed defects from hypotheses.
- [x] Run feasible build/tests; device UI testing was not performed because the connected device was in another app and the required interaction reference was inaccessible.
- [x] Report prioritized findings with file/line evidence, impact, reproduction steps, and verification limitations.

---

# Fix Whole-App Audit Findings

## Objective
- Fix all seven findings from the whole-app audit while preserving pre-existing edits in TagsActivity.java, AudioBadges.kt and activity_tags.xml.

## Ordered implementation and verification
- [x] Recheck affected code and existing tests; establish baseline of pre-existing changes.
- [x] Fix lost Search & Match and auto-tag draft warning; focused build/tests pass (activity flow needs device verification).
- [x] Fix non-paginated folders/playlists (and other full-list queries) duplicating on scroll; pagination regression test and focused build pass.
- [x] Fix empty-playlist creation affordance and stale navigation filters; build/tests pass, runtime UI check pending.
- [x] Wire empty queue Browse Library action through both hosts; build/tests pass, runtime UI check pending.
- [x] Add semantic activation to mini-player and Now Playing flip, and adjustable/keyboard controls to Studio Console seek/volume; build/tests pass, accessibility device check pending.
- [x] Persist the user's media-server stopped intent across activity recreation and cancel pending starts; app tests and debug build pass.
- [x] Run full core/app unit tests and debug build; review diff and report that device/visual verification was not performed and app lint still has 22 unrelated pre-existing errors.

---

# Document, Version, and Commit Audit Fixes

## Objective
- Update user/developer documentation and release notes to reflect the completed fixes and existing preview edits, bump the Android patch version, then commit the complete working tree requested by the user.

## Checklist
- [x] Reconcile current documentation and release conventions with the complete diff, including earlier uncommitted preview work.
- [x] Update relevant user/UI documentation and changelog with accurate behavior and verification caveats.
- [x] Bump patch version and versionCode; update stale displayed version in the drawer.
- [x] Verify Gradle tests/build, documentation consistency, staged scope, and diff hygiene (lint has known unrelated failures; device UI not run).
- [x] Commit all intended source/documentation changes; confirm clean working tree and report commit ID.

---

# Usage and Functional UX Review

## Objective
- Review current primary user journeys for usability and functional gaps, rechecking the latest implementation rather than repeating resolved audit findings.

## Checklist
- [x] Inspect navigation, discovery, playback, queue, and library-editing flows with source evidence.
- [x] Verify high-impact findings against call sites; source review only, no builds, tests, or device interaction performed.
- [x] Deliver prioritized findings with user impact and actionable recommendations.

## Review summary
- Found playback handoff ordering, immediate artwork writes despite Discard, loaded-page queue scope, search state divergence, and folder draft persistence defects.
- Identified ineffective empty-library recovery and queue-clear recovery friction.
- Rechecked current implementation rather than repeating previously resolved audit findings. Runtime playback and visual/accessibility checks remain unverified.

---

# Improve Usage and Functional UX

## Objective
- Resolve the seven reviewed issues with predictable playback, recoverable editing, and actionable library states.

## Implementation and verification
- [x] Correct playback target handoff ordering; app unit tests/compilation pass (physical handoff still needs device verification).
- [x] Stage selected artwork until Save, preserve originals on failure, and retain drafts for retry; four file-persistence tests and TagsViewModel tests pass.
- [x] Resolve the full active query for Quick Play; 1,003-track regression fails before fix and passes after fix, with MainViewModel suite passing.
- [x] Synchronize visible search with navigation and Back behavior; app compilation/tests pass.
- [x] Keep folder additions/removals in one draft until scan confirmation; app compilation/tests pass.
- [x] Offer context-specific empty-library, no-results, and error recovery actions; test distinguishes no matches from no indexed music.
- [x] Make queue clearing explicitly confirmed in the shared QueuePage used by both hosts.
- [x] Run app/core tests and debug build, check lint/diff, and document verification limits.

## Verification refinements
- Verify artwork also resolves after Save (including previously cached artwork); preserve the selected file path in saved metadata.
- Bind the folder dialog to a lifecycle-aware host so the new discovery action can safely display Compose content.
- Device interaction references are permission-blocked; device/visual journeys cannot be verified in this session.

## Results
- 51 app and 34 core tests pass (85 total), including eight new regression cases covering artwork persistence/retry, complete-result playback, failed playback lookup, and empty-library classification.
- Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- Lint reports 22 pre-existing errors in unchanged service/audio declarations (MissingSuperCall and Media3 opt-in), plus warnings/hints; no lint errors in added behavior.
- `git diff --check` passes. Updated `UI.md` and Unreleased changelog to document the new interactions.
- Device playback handoff, dialog rendering, and touch/accessibility journeys remain unverified.

---

# Main Dock Menu Safety and UX

## Status
- Tasks 1-4 implemented and source-verified; Task 5 device verification blocked by the phone lock screen. Detailed plan: [dock-menu-fix-plan.md](dock-menu-fix-plan.md).

## Checklist
- [x] Task 1: Added `LibrarySelectionModel`, which keys multi-selection by stable track identity (unique key, then container type/title, then path, then id) instead of row position, so no list replacement can retarget a batch action. Selection whose track leaves the list is dropped; reordering and re-indexing keep it. Selection also ends on library-destination change. 10 regression tests cover destination change, pagination, vanished track, same-position-different-track, reordering, re-indexing, toggle, select-all, and out-of-range input.
- [x] Task 2: Added `MainBackPolicy` (enum-based) so an open drawer consumes Back before selection or library navigation. 3 regression tests cover the precedence order. The `DrawerInterop` open/close helpers, previously unused, are now wired to Back.
- [x] Checkpoint A: 61 app+core tests pass with zero failures; `:app:assembleDebug` succeeds. Live device check still pending.
- [x] Task 3: Renamed to Similar Tracks, Audio Quality, Music Folders & Scan, and Notification Access; moved all drawer copy into `R.string.nav_*` resources. Destinations and queries unchanged.
- [x] Task 4: Drawer tiles and rows now use `Modifier.selectable` with `Role.Tab` (matching Material 3 `NavigationDrawerItem`) and a 48dp minimum height. The dock menu button was already 48dp via `IconButton`'s internal `minimumInteractiveComponentSize()`; verified against the resolved Material 3 1.4.0 source rather than assumed from the 44dp modifier.
- [ ] Checkpoint B: Screenshot and TalkBack comparison on device — blocked, phone still locked.
- [x] Task 5: Ran app+core tests (64 passing, 0 failures), debug build, and diff check; updated `UI.md` (new §3F drawer section) and `CHANGELOG.md`. An independent review of the first pass found selection was still positional and guarded at only one of seven list-replacing call sites; Task 1 was reworked to identity-keyed selection, which closes the gap at the data layer rather than per call site. The same review caught a double `onDestroyActionMode` invocation, now fixed by clearing the `actionMode` field before `finish()`.

## Boundaries and verification limits
- No new Clean Up/Inspect workflows, monetization, playback-engine changes, or selection-system refactor beyond what the safety fix required. The unrelated `AboutScreen.kt` edit is untouched.
- **Not verified on a device:** live Back behavior with the drawer open, visual drawer layout, TalkBack output, large-text rendering, and before/after screenshots. Source and unit tests only. The phone stayed locked for the whole session.
- Batch-action regression tests use fixture tracks; no destructive operation was run against the real music library.
- **Dead code found, not removed:** `MySelectionTracker.java` now has no callers after `LibrarySelectionModel` replaced it, including its duplicate position-to-track resolution. Awaiting a decision before deletion.
- **Deliberately unchanged:** whether the MusicMate logo alone reads as "menu" to a first-time user, and the fact that Back at a root destination with no selection re-opens the drawer (pre-existing `onSearchBackClicked` behavior, not a regression). Both need live comparison; the correctness fixes do not depend on them.

---

# App UX Modernization

## Objective

- Add measurable visual and accessibility quality gates, move the hybrid shell toward type-safe Navigation 3, use larger windows for a Music Center supporting pane, and complete interaction/device validation without changing playback or repository behavior.
- Detailed plan: [plan.md](plan.md).

## Task 1: Add the UI verification harness

**Description:** Add isolated Compose screenshot testing and connected Compose accessibility testing. Resolve screenshot-plugin compatibility with AGP 9.4.1 before changing production UI.

**Acceptance criteria:**
- [x] A deterministic Compose smoke preview has record and verify Gradle tasks.
- [x] A connected Compose test can run Accessibility Test Framework checks.
- [x] Test-only tooling does not alter release packaging or runtime dependencies.

**Verification:**
- [x] Run screenshot verification twice with identical output (1 preview, 0 failures, 0.0% diff on 2026-09-28).
- [x] Run the focused connected smoke test on API 36 or newer (`SM-S931B - 16`, 1 test, 0 failures).
- [x] Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug` (69 app + 52 core tests, 0 failures; debug APK assembled on 2026-09-28).

**Dependencies:** None.

**Files likely touched:** `gradle/libs.versions.toml`, `build.gradle`, `app/build.gradle`, `gradle.properties`, `app/src/screenshotTest/java/apincer/android/mmate/ui/compose/ScreenshotSmokeTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 2: Lock the main-shell and library visual baseline

**Description:** Add stable preview fixtures for the main shell and library so empty, populated, filtered, and selected states render without live services or mutable global data.

**Acceptance criteria:**
- [x] Baselines cover compact portrait, compact at 200% font scale, landscape, and expanded tablet widths.
- [x] Empty, populated, search/no-results, and selection states use deterministic track and artwork fixtures.
- [x] Tests do not read storage, network, playback, wall-clock, or device artwork.

**Verification:**
- [x] Run focused main/library screenshot verification twice (7 references, 0 failures, 0.0% diff).
- [x] Review first-generation references for clipping, unstable pixels, and fixture accuracy; approved on 2026-09-28. The captured 200% metadata clipping is retained as Task 12 evidence.
- [x] Run `./gradlew :app:testDebugUnitTest` (69 tests, 0 failures) and `:app:assembleDebug`.

**Dependencies:** Task 1.

**Files likely touched:** `MainScaffold.kt`, `MusicListScreen.kt`, `UxPreviewFixtures.kt`, `MainLibraryScreenshotTest.kt`.

**Estimated scope:** Medium (4 files).

## Task 3: Lock Music Center and Settings visual baselines

**Description:** Add deterministic fixtures and screenshots for playback, empty/populated queue, media-server states, and responsive Settings layouts.

**Acceptance criteria:**
- [x] Music Center covers no-track, playing, empty queue, populated queue, server stopped, and server running states.
- [x] Settings covers compact stacked controls and expanded two-column layout at default and 200% text.
- [x] Dynamic meters, progress, QR data, and artwork are fixed or disabled in reference renders.

**Verification:**
- [x] Run focused Music Center and Settings screenshot verification (18 total references, 0 failures on a fresh rerender).
- [x] Review references for stable animation, typography, and system-inset behavior (approved 2026-09-28).
- [x] Run `./gradlew :app:assembleDebug`.

**Dependencies:** Tasks 1-2.

**Files likely touched:** `AudioHubSheet.kt`, `SettingsScreen.kt`, `UxPreviewFixtures.kt`, `MusicCenterScreenshotTest.kt`, `SettingsScreenshotTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint A: Baseline locked

- [x] Screenshot verification runs without updating reference images.
- [x] The accessibility smoke test runs on API 36 (`Medium_Phone` emulator, 2026-09-28).
- [x] App unit tests and debug build pass.
- [x] Human review confirms the baseline represents the intended current UI (approved 2026-09-28).

## Task 4: Harden library and shell accessibility

**Description:** Correct labels, roles, state descriptions, traversal order, and target sizes in main navigation, library, search, selection, and empty/error states.

**Acceptance criteria:**
- [x] Every actionable node has one concise accessible name and the correct role/state.
- [x] Decorative children do not create duplicate TalkBack stops; reading order follows the visible hierarchy.
- [x] Automated checks pass for touch target size and contrast on primary library states.

**Verification:**
- [x] Run `MainLibraryAccessibilityTest` with accessibility checks enabled (4 primary states pass on API 36).
- [x] Run focused screenshot verification and inspect intentional diffs (18 references pass unchanged).
- [ ] Manually traverse Library with TalkBack.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `MainScaffold.kt`, `MusicListScreen.kt`, `TrackListItem.kt`, `strings.xml`, `MainLibraryAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 5: Harden Music Center accessibility

**Description:** Make playback, queue, and media-server controls understandable and operable through semantics while moving touched user-facing strings into resources.

**Acceptance criteria:**
- [x] Transport, seek, volume, tabs, queue count, server status, and destructive actions announce label, value, and state once.
- [x] Queue drag/remove actions expose accessible alternatives without requiring a gesture.
- [x] Media-server URLs, QR actions, and engine choices have meaningful semantics and traversal order.

**Verification:**
- [x] Run a connected accessibility test across no-track, playing, queue, and server states (5 tests pass on API 36).
- [x] Run focused screenshot verification and review resource-text diffs (18 references pass; 2 intentional Playback references updated for volume controls).
- [ ] Manually traverse all three Music Center tabs with TalkBack.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `AudioHubSheet.kt`, `NowPlayingPage.kt`, `QueuePage.kt`, `MediaServerPage.kt`, `strings.xml`.

**Estimated scope:** Medium (5 files).

## Task 6: Harden Settings and support-screen accessibility

**Description:** Correct semantics and large-text behavior in Settings, permission onboarding, and About while centralizing their remaining user-facing labels.

**Acceptance criteria:**
- [x] Setting rows expose one action, their current value, and the correct switch/radio role.
- [x] Permission explanations and recovery actions are read in a logical order.
- [x] About links identify their destination and action without duplicate icon announcements.

**Verification:**
- [x] Run a focused connected accessibility test for Settings, Permission, and About (3 tests pass on API 36).
- [x] Verify compact and expanded screenshots at default and 200% font scale (all 4 Settings references pass unchanged).
- [x] Run app unit tests and the debug build.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `SettingsScreen.kt`, `PermissionScreen.kt`, `AboutScreen.kt`, `strings.xml`, `SupportScreensAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint B: Accessible primary flows

- [x] Automated accessibility checks pass for Tasks 4-6 (12 connected scenarios on API 36).
- [x] Touched user-facing labels are resource-backed.
- [x] Screenshot diffs are intentional and reviewed (18 references pass).
- [x] App unit tests and debug build pass.

## Task 7: Add the Navigation 3 route foundation

**Description:** Define serializable, type-safe destinations and a saved back-stack owner while keeping the current UI and Java callback bridge in place.

**Acceptance criteria:**
- [x] Routes represent library destinations, Music Center tabs, Studio Console, Settings, About, and tag editing without resource-ID identity.
- [x] Back-stack state restores selected destination and overlay route after recreation.
- [x] Unit tests define root, overlay, selection, search, and exit Back precedence.

**Verification:**
- [x] Focused route serialization, save/restore, and Back-policy tests pass (8 tests, 0 failures on 2026-09-28).
- [x] App/core unit tests and debug APK pass (128 tests, 0 failures on 2026-09-28).
- [x] Production UI remains visually unchanged (18 screenshot references, 0 failures on 2026-09-28).

**Dependencies:** Checkpoint B.

**Files likely touched:** `gradle/libs.versions.toml`, `app/build.gradle`, `MainRoute.kt`, `MainNavigationState.kt`, `MainNavigationStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 8: Move overlay destinations to Navigation 3 scenes

**Description:** Represent Music Center and Studio Console as Navigation 3 scenes, replacing boolean overlay ownership while preserving state and callbacks.

**Acceptance criteria:**
- [x] Opening and dismissing either surface pushes/pops the correct typed route.
- [x] Music Center tab and playback state survive rotation and window resize.
- [x] Back dismisses the visible overlay before lower-priority states according to the tested policy.

**Verification:**
- [x] Run focused overlay navigation and save/restore tests (`MainNavigationStateTest` and `FullscreenStudioConsoleTest`; included in 79 app tests, 0 failures on 2026-09-28).
- [x] Verify compact screenshots match the approved baseline except for intentional transitions (18 references, 0 failures on 2026-09-28).
- [x] Exercise Music Center and Studio Console open/dismiss flows on the API 36 `emulator-5554`; Queue remained selected after rotation, Studio opened in landscape, and system Back returned to the Library. The focused connected Music Center route test also passed.

**Dependencies:** Task 7.

**Files likely touched:** `MainScaffold.kt`, `MainScaffoldState.kt`, `AudioHubSheet.kt`, `FullscreenStudioConsole.kt`, `MainNavigationState.kt`.

**Estimated scope:** Medium (5 files).

## Task 9: Move top-level destinations to Navigation 3

**Description:** Make Navigation 3 the source of truth for library destinations and Back behavior while retaining `MainActivity` service and data callbacks.

**Acceptance criteria:**
- [x] Drawer/rail selection, search, filter clearing, selection mode, and Back update one route/back stack.
- [x] Existing resource-ID callbacks are isolated in one compatibility mapping rather than stored as navigation state.
- [x] Rotation, resize, and process recreation retain route and visible content without stale selection or search.

**Verification:**
- [x] Run route, `MainBackPolicy`, `LibrarySelectionModel`, and `MainViewModel` tests (31 focused tests; full gate: 80 app + 52 core tests, 0 failures on 2026-09-28).
- [x] Run main/library screenshot verification across all four form factors (18 references, 0 failures, 0.0% diff on 2026-09-28).
- [x] Exercise each top-level destination and Back path on the API 36 `emulator-5554`: all six non-root destinations exposed Back; rotation retained Genres; Back returned to All Songs; active search was cleared before root exit. The consolidated connected class passed 7 tests, including typed drawer selection and saved-state restoration.

**Dependencies:** Tasks 7-8.

**Files likely touched:** `MainActivity.java`, `MainScaffold.kt`, `DrawerInterop.kt`, `MainRoute.kt`, `MainNavigationStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint C: Navigation compatibility

- [x] All existing app/core unit tests pass (80 app + 52 core, 0 failures on 2026-09-28).
- [x] Screenshot verification shows no unintended compact-window regression (18 references, 0 failures, 0.0% diff).
- [x] Search, selection, drawer/rail, playback callbacks, and Back precedence pass device checks on API 36.
- [x] Route state survives rotation, resize, and process recreation (live rotation plus connected saved-state restoration coverage).

## Task 10: Add the expanded Music Center supporting pane

**Description:** Extract reusable Music Center content and host it as a Material 3 Adaptive Navigation 3 supporting pane on expanded windows while retaining the sheet scene on compact windows.

**Acceptance criteria:**
- [x] Expanded windows show library content and Music Center together without duplicated state or callbacks.
- [x] Resizing keeps the selected Music Center tab, queue position, and playback state.
- [x] Pane navigation, Back, focus order, and system insets remain correct in split-screen and foldable postures.

**Verification:**
- [x] Compact, medium, expanded, and foldable open/closed Music Center screenshots pass (8 new references; 26 total, 0 failures on 2026-09-29).
- [x] Navigation save/restore tests pass across the 840dp supporting-pane boundary while preserving the Queue tab and route state.
- [x] API 36 `emulator-5554` resize checks pass from expanded to compact and back: the Library and Music Center share expanded space, compact mode returns to the sheet, Queue/playback state remains hoisted, and one system Back dismisses the pane after search focus is cleared.

**Dependencies:** Tasks 8-9 and Checkpoint C.

**Files likely touched:** `MainScaffold.kt`, `AudioHubSheet.kt`, `AudioHubContent.kt`, `MusicCenterSceneStrategy.kt`, `AdaptiveMusicCenterScreenshotTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 11: Make queue and library interactions reversible and input-adaptive

**Description:** Improve destructive/reorder feedback and add keyboard and pointer affordances for core library and queue actions.

**Acceptance criteria:**
- [ ] Queue swipe removal uses one clear direction, offers Undo, and restores the exact item position when undone.
- [ ] Reorder exposes drag-handle semantics plus accessible move actions; keyboard users can move focus and activate row actions.
- [ ] Pointer hover/focus states are visible and do not leave sticky touch hover behavior.

**Verification:**
- [ ] Run queue state tests for remove, undo, move, and playing-item identity.
- [ ] Run connected keyboard and semantics tests for library and queue rows.
- [ ] Exercise touch, mouse, keyboard, and TalkBack paths.

**Dependencies:** Tasks 4-5 and 9.

**Files likely touched:** `QueuePage.kt`, `QueueState.kt`, `MusicListScreen.kt`, `TrackListItem.kt`, `QueueInteractionStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 12: Respect reduced motion and 200% text

**Description:** Centralize motion policy, stop nonessential infinite effects when animations are disabled, and remove remaining clipping/overlap at large font scales.

**Acceptance criteria:**
- [ ] Empty-state pulses, meters, and decorative transitions settle to a meaningful static state when system animations are disabled.
- [ ] Primary screens remain readable and operable at 200% font scale without horizontal clipping or hidden actions.
- [ ] Focus, state, and content survive configuration and font-scale changes.

**Verification:**
- [ ] Run motion-policy unit tests and large-text screenshot verification.
- [ ] Run accessibility checks at 200% font scale.
- [ ] Exercise animation scale 0x and 200% text on device/emulator.

**Dependencies:** Tasks 4-6 and 10.

**Files likely touched:** `UiMotionPolicy.kt`, `MusicListScreen.kt`, `NowPlayingPage.kt`, `FullscreenStudioConsole.kt`, `UiMotionPolicyTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint D: Adaptive experience

- [ ] Compact, medium, expanded, foldable, and split-screen layouts preserve task continuity.
- [ ] Core flows complete through touch, keyboard, pointer, and TalkBack.
- [ ] No content is clipped by system bars, cutouts, IME, or pane boundaries.
- [ ] Screenshot and accessibility suites pass without updating references.

## Task 13: Add the system-access state foundation

**Description:** Define one authoritative, side-effect-free model for MusicMate's full-storage and external-player access. Replace package-wide notification-listener parsing with the public component-specific API and expose state that UI hosts can refresh without triggering a prompt or Settings intent.

**Acceptance criteria:**
- [x] Storage state is derived from `Environment.isExternalStorageManager()` and external-player state from `NotificationManager.isNotificationListenerAccessGranted()` using `MediaNotificationListener`'s explicit `ComponentName`.
- [x] Capability checks never launch UI, mutate permission state, or conflate notification-listener access with `POST_NOTIFICATIONS`.
- [x] The main Compose state can represent granted, denied, and optional/required presentation without retaining an `Activity` context.

**Verification:**
- [x] Run focused unit tests for all capability-state combinations and the drawer summary mapping.
- [x] Run existing permission/service unit tests and `:app:assembleDebug`.
- [x] Run `git diff --check`.

**Dependencies:** Task 6.

**Files likely touched:** `PermissionUtils.java`, `SystemAccessState.kt`, `MainScaffoldState.kt`, `SystemAccessStateTest.kt`.

**Estimated scope:** Medium (4 files).

## Task 14: Build the status-driven System Access screen

**Description:** Refactor the current all-or-nothing permission onboarding into a lifecycle-aware System Access screen with separate storage-management and external-player rows, clear required/optional copy, current status, rationale, and explicit user-triggered actions.

**Acceptance criteria:**
- [x] The screen refreshes both statuses in `onResume`, shows storage as required for scan/tag/file operations, and shows external-player access as optional.
- [x] Storage opens the app-specific all-files-access page with a guarded general-page fallback; external-player access opens the listener detail page when supported with a guarded general-page fallback.
- [x] The mixed `PERMISSIONS_ALL` request and its `any { granted }` success condition are removed; unrelated Bluetooth, internet, and obsolete storage permissions are not requested by this flow.

**Verification:**
- [ ] Run focused activity/UI tests for denied, partially granted, fully granted, cancel, and return-from-Settings states.
- [ ] Run permission-screen screenshot and accessibility checks at default and 200% text.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug`.

**Dependencies:** Task 13.

**Files likely touched:** `PermissionActivity.kt`, `PermissionScreen.kt`, `PermissionUtils.java`, `strings.xml`, `SupportScreensAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 15: Add contextual recovery and live external-player refresh

**Description:** Connect access recovery to the features that need it. Folder setup requests storage only after the user starts that operation, while the player picker explains optional external-player integration and can open the focused access screen. Make service-side listener registration idempotently synchronize after grants or revocations.

**Acceptance criteria:**
- [x] App launch and resume silently validate state but never automatically display a permission prompt or open Settings.
- [x] A denied folder scan opens System Access focused on storage; the player picker remains usable for local/DLNA targets and exposes an optional external-player enable action.
- [x] Returning after a listener grant registers active-session monitoring and refreshes player targets immediately; revocation unregisters/clears external targets without requiring an app or service restart.

**Verification:**
- [x] Run unit tests for feature-gating and idempotent listener register/unregister transitions.
- [ ] Run connected tests for grant, deny/cancel, revoke, activity recreation, and service-already-running scenarios.
- [ ] Manually verify folder setup and player discovery on API 36 or newer.

**Dependencies:** Tasks 13-14.

**Files likely touched:** `MainActivity.java`, `PlayerPickerDialog.kt`, `MusicMateServiceImpl.java`, `MediaNotificationListener.java`, `SystemAccessIntegrationTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 16: Consolidate and polish the drawer access destination

**Description:** Replace the two raw Settings shortcuts with one System Access drawer destination that reports a concise current summary and opens the status-driven screen, while preserving drawer selection and Back behavior.

**Acceptance criteria:**
- [x] The drawer contains one `System Access` row instead of separate Storage Access and Notification Access rows, with a truthful `Ready`, `Storage needed`, or `Optional access off` summary.
- [x] Copy says `External Player Access`, never implying that listener access controls MusicMate's own notifications; status and action semantics are announced once by TalkBack.
- [x] Drawer state updates after returning from Settings and survives rotation/resize without marking the external activity as a selected library destination.

**Verification:**
- [x] Run drawer navigation, Back-policy, semantics, and capability-summary tests.
- [ ] Run compact, expanded, and 200% text screenshot verification; obtain human approval before updating references.
- [ ] Exercise grant/revoke and drawer reopen behavior on API 36 or newer.

**Dependencies:** Tasks 13-15.

**Files likely touched:** `MainActivity.java`, `MainScaffold.kt`, `menu_ids.xml`, `strings.xml`, `MainLibraryAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint E: Contextual system access

- [x] Launch/resume validation has no automatic prompt or Settings redirect.
- [x] Storage and external-player access have independent, cancellable, contextual recovery paths.
- [ ] Grant and revocation state appears immediately in the screen and drawer.
- [ ] External-player discovery begins or stops correctly without restarting MusicMate.
- [ ] Unit, connected, accessibility, screenshot, build, and diff checks pass.

**Evidence (2026-09-29):** Focused state-policy unit tests, debug assembly, screenshot verification, and permission/drawer Compose tests passed on Android 16 (`SM-S931B`). Live special-access grant/revoke journeys were not performed. The full core test gate is blocked by the unrelated untracked `FileRepositoryCoverArtTest.java`, which references two missing methods; project lint remains red on its existing 33-error baseline.

## Task 17: Run and document the release UX matrix

**Description:** Complete automated and live-device validation, record evidence, and update user/developer documentation to match shipped interactions.

**Acceptance criteria:**
- [ ] The matrix covers launch/discovery, navigation/search, playback handoff, Music Center, queue edit/undo, tag editing, Settings, permissions, and media server.
- [ ] Phone portrait/landscape, 200% text, tablet/foldable resize, TalkBack, keyboard, and pointer results are recorded.
- [ ] Known limitations and follow-up observations are explicit; no unverified behavior is described as complete.

**Verification:**
- [ ] Run `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
- [ ] Run screenshot verification, focused connected tests, lint for changed UI files, and `git diff --check`.
- [ ] Review generated screenshot diffs before updating any golden files.

**Dependencies:** Tasks 10-16 and Checkpoints D-E.

**Files likely touched:** `UI.md`, `USER_GUIDE.md`, `CHANGELOG.md`, `tasks/todo.md`, `tasks/ux-validation.md`.

**Estimated scope:** Medium (5 files).

## Checkpoint F: Complete

- [ ] All acceptance criteria above are checked with evidence.
- [ ] Automated suites, debug build, focused lint, and diff hygiene pass.
- [ ] Human visual review approves reference-image updates.
- [ ] Device findings and remaining limits are documented.
# App functionality and interface review (2026-09-30)

- [x] Inspect current functionality, UI flows, pending changes, and differences against main.
- [x] Run relevant automated checks and inspect available device/runtime evidence.
- [x] Record confirmed defects, usability findings, and verification limits in tasks/app-review-2026-09-30.md.

Evidence: 142 unit tests and debug assembly pass. Screenshot comparisons fail for 21/26 previews; lint reports 33 errors. Five fresh previews inspected visually. No connected device; live interaction checks remain outstanding in the report.
# MQA display review (2026-09-30)

- [x] Trace MQA detection and native/Web UI display paths.
- [x] Verify display inconsistencies against source, relevant tests, and available runtime evidence.
- [x] Report confirmed findings with file references and verification limits in tasks/mqa-display-review-2026-09-30.md. Six focused tests passed; no device connected.

## Task 18: Unify DSD Audio Badge Styling (Cyan)

**Description:** Unify the DSD color styling across the app. Currently, the legacy XML views (using `TagUtils.java` and `BadgeView`) misclassify DSD as either Gold (`quality_hd`) or generic grey (`mm_label_lossless`), while the modern Compose UI uses a distinct Cyan (`#00E5FF`). We will adopt Cyan as the single source of truth, update `TagUtils.java` to support tiered badge colors, and clean up duplicate XML color definitions.

**Acceptance criteria:**
- [x] Define definitive Cyan color tokens for DSD (`badge_dsd_text`, `badge_dsd_bg`) in `colors.xml`.
- [x] Clean up abandoned/duplicate `resolution_dsd` and `quality_dsd` colors in `colors.xml`.
- [x] Upgrade `TagUtils.getCodecColor()` and `TagUtils.getCodecBgColor()` to return specific tiered colors (Cyan for DSD, Purple for MQA, Gold for Hi-Res, Blue for CD) instead of a binary lossy/lossless check.
- [x] Fix `TagUtils.getResolutionColor()` to return the Cyan DSD color.
- [x] Ensure `TagUtils.formatCodec()` formats DSD compactly (e.g., "DSD64") rather than just the file extension.

**Verification:**
- [x] Run `app` unit tests to ensure `TagUtils` logic isn't broken.
- [x] Run `:app:assembleDebug` to verify no AAPT color resource linking errors.

## Cover Art Architecture Fixes
- [x] Phase 1 (already in code, confirmed 2026-10-02): Implement `MediaMetadataRetriever` in `FileRepository` to replace `FFMpegHelper` for embedded cover extraction.
- [x] Phase 2 (already in code, confirmed 2026-10-02; scan always sets albumArtFilename and extraction writes the file the lookup re-checks): Fix Lazy Extraction bug in `FileRepository.getCoverArt()` so embedded art is properly extracted and returned to Coil when missing.
- [x] Phase 3 (already in code): Leave unmanaged file hashing as `MD5(file path)` to support heterogeneous folders (e.g., Downloads).
- [x] Phase 4 (2026-10-02): Refactor `CoverartFetcher` to use native Coil `AssetImageSource` instead of copying the default cover to the disk cache.
- [x] Device (2026-10-02): a track with no art shows the default cover (user confirmed). The cached copy did come back: MusixMateApp.initDefaultAssets() copied the whole Covers asset folder at every startup (skipping existing files). Nothing read those copies, so that startup copy was removed; after relaunch neither no_cover.png nor no_cover3.png is recreated
- [ ] Unused asset: assets/Covers/no_cover3.png (about 1.2 MB) is referenced nowhere; remove it if it is not wanted. A local, untracked assets/Covers/.DS_Store is packaged into local builds

## Data integrity fixes (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P1-11 FileOperationTask: fail item when TagWriter.writeTagToFile fails
- [x] P1-12 Search & Match: stage downloaded cover, commit only on Save
- [x] P1-10 Organize: require Save of dirty edits first
- [x] P2-6 Extract/Remove embedded art: real result, confirm Remove
- [x] P2-7 Batch ops: surface per-file failures, no finish() on total failure

## HttpCore 5.5-beta3: bytecode patch instead of vendored classes (2026-10-01, N2)
- [x] patchHttpCore: ASM-rewrite jdk/net references in the original jar (getstatic -> aconst_null, Sockets.setOption -> pops, supportedOptions -> emptySet)
- [x] Fail the build if any jdk/net reference survives
- [x] Delete vendored SingleCoreIOReactor.java and ReflectionUtils.java
- [x] Verify: build, javap scan of patched jar, unit tests (ReflectionUtilsTest), assembleDebug

## Batch completion race (2026-10-01, N1/N3/N4 from tasks/app-review-2026-10-01-delta.md)
- [x] FileOperationTask: delete/move/measureDR complete exactly once, after every item's final status (finishItem in finally)
- [x] measureDR analyses a copy, so a failed write leaves the displayed track unchanged
- [x] TagsActivity Organize: Measure DR completion runs on the UI thread
- [x] FileOperationTaskCompletionTest (200 rounds x 64 items, 8 threads); app unit tests 95/95; assembleDebug

## P1-1 Removing the playing track skips its follower (2026-10-01)
- [x] QueueManager: removing the playing track records its follower; Next/Previous continue from it until playback moves
- [x] Removing that follower too moves on to the next one; Repeat All wraps; last track ends the queue
- [x] setPlaybackTrack no longer re-enqueues the removed, still-playing track (part of P1-3)
- [x] onTrackDeleted plays the follower or stops, instead of replaying the previous track when the last one is deleted
- [x] skipUnplayable bounded so Repeat All with all-missing files cannot spin
- [x] 8 new QueueManagerTest cases (red then green); core 61/61, app 95/95, assembleDebug

## P1-5 Shuffle reshuffles on every queue edit (2026-10-01)
- [x] Shuffle order kept by track id (shuffleIds); edits merge instead of reshuffling
- [x] Play Next goes directly after the playing track; enqueued/re-added tracks slot in randomly among upcoming ones; played tracks do not return
- [x] Full reshuffle only on queue replace/load and when shuffle is turned on
- [x] Play Next / enqueue after the playing track was removed continue from the right place (P1-1 follow-up)
- [x] 5 new QueueManagerTest cases (red then green, 5 repeated runs); core 66/66, app 95/95, assembleDebug

## P1-3 Stale gapless preload after queue edits (2026-10-01)
- [x] QueueManager change listener (queue edits, Repeat, Shuffle); not fired by navigation
- [x] Service re-checks the handed-over follower on change (coalesced on the scheduler); replaces or clears it
- [x] DLNA: setNextTrack(null) clears with empty NextURI; a rejected clear keeps preload state for transition matching
- [x] Local ExoPlayer: setNextTrack is relative to the playing item (old removeMediaItem(1) removed the playing track after an auto transition); null clears
- [x] lastPreloadedTrackId reset on track start
- [x] QueueManagerTest listener case; core/app/server-jupnp tests, assembleDebug
- [ ] Device: local gapless across 3+ tracks; edit queue / toggle Repeat after preload (local + verified-gapless DLNA)

## P1-6 Sleep "end of track" (2026-10-01)
- [x] Manual Next no longer pauses; the timer stays armed for the new track
- [x] While armed, no gapless follower is handed to the player (and an already-handed one is cleared), so every track end passes the check
- [x] DLNA fallback timer honours the armed timer; disarming re-preloads when playing
- [x] app tests, assembleDebug (no service unit-test harness; logic verified by reading)
- [ ] Device: arm end-of-track on local and verified-gapless DLNA; press Next once (keeps playing), then let the track end (pauses)

## P1-2 Session Next/Previous ignore the queue on local playback (2026-10-01)
- [x] QueueAwareSessionPlayer (ForwardingSimpleBasePlayer) wraps ExoPlayer for the MediaLibrarySession
- [x] Always advertises Next/Previous; Next/Previous run the service queue skip (posted to the player looper); Previous past 3 s restarts the track
- [x] Verified Media3 1.11.1 dispatches handleSeek(-1, ...) when ExoPlayer has no next/previous item
- [x] app tests, assembleDebug (no Robolectric/Media3 test utils; no unit test)
- [ ] Device: notification, lock screen, wired/Bluetooth headset Next/Previous on local playback; queue sheet and now-playing stay in sync

## P1-4 Play after a paused target switch resumes an empty renderer (2026-10-01)
- [x] switchPlayer records PendingResume(target, track, position) when switching while paused
- [x] resumePlayer starts the track there instead: DLNA via playerPlaySong(udn, track, positionMs); local/external via playSong + seekTo
- [x] internalPlayOnDMRPlayer accepts a start position; any explicit playSong clears the pending resume
- [x] app tests, assembleDebug (service has no unit-test harness)
- [ ] Device: pause on local, switch to DLNA, Play (starts at the paused position); pause on DLNA, switch to local, Play

## P1-7/P1-8/P1-9 Media server lifecycle and status (2026-10-01)
- [x] P1-7 Hub tracks wantRunning: network recovery restarts only a server the user wants; Stop during STARTING applies when the start completes; Start during STOPPING restarts after the stop; network loss uses stopInternal
- [x] P1-7 Failed start/restart releases locks and reports ERROR
- [x] P1-8 Hub reports STARTING; the service mirrors the hub status flow (asLiveData) instead of posting RUNNING right after an async start
- [x] P1-9 Service restarts a running server when PREF_SERVER_ENGINE changes; Settings and Music Center only save the preference; a stopped server stays stopped
- [x] app/server-jupnp tests, assembleDebug (no hub/service unit-test harness)
- [ ] Device: Stop, then toggle Wi-Fi (stays stopped); Stop right after Start; start without network (ERROR); Wi-Fi off/on while running (STOPPED then RUNNING)

## P1-13/P1-14 Library query errors and paging (2026-10-01)
- [x] P1-13 TagRepository.findMusic propagates query failures (removed findMusicOrEmpty/EMPTY_LIST), so the list shows "Couldn't load music" + Retry instead of "No tracks"
- [x] P1-13 Related-tracks sheet catches the failure instead of crashing viewModelScope
- [x] P1-14 Paged TrackDao queries end with `id ASC` (16 queries), so page order is fully determined
- [x] P1-14 appendPage / loadUntilFound skip ids already shown (LazyColumn is keyed by track id)
- [x] TagRepositoryQueryErrorTest (red on old code), AppendPageTest; core 68/68, app 97/97, assembleDebug
- [ ] Device: scroll a large library while a scan runs; no crash, no duplicate rows

## P2 items (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P2-1 Sleep chip refreshes from getSleepTimerRemainingMs every second while visible; clears when the timer fires (SleepTimerFormatTest)
- [x] P2-2 Sleep fade reads the renderer volume (new MediaServerHub.playerGetVolume), fades 3/4-1/2-1/4, pauses, restores it; unknown volume pauses without fading
- [x] P2-3 Startup scan uses KEEP (never cancels a queued/running scan); user full rescan REPLACE; user incremental APPEND_OR_REPLACE
- [x] P2-4 Media server auto-start is opt-in (default false); Start/Stop still remember the choice
- [x] P2-5 Scan indicator shows "Scan waiting to start…" for ENQUEUED/BLOCKED; FAILED shows a toast; picks the active work among chained scans
- [x] P2-8 Convert keeps the embedded cover (attached_pic map); retries audio-only if the muxer rejects it
- [x] P2-9 Picked cover applies to every folder of the edited tracks (confirm when >1); ArtworkDraft supports several targets (ArtworkDraftTest)
- [x] P2-10 FastScrollbar reads latest totalItems/offset/range/label; drag accumulates from its start
- [x] P2-11 Artwork tap during selection toggles the row
- [x] P2-12 Folder Play/Queue: no premature toast; outcome (count, empty, error) reported after the work; unbound says so
- [x] P2-13 Scroll position kept per destination (musicListKey); drill-in starts at top, Back restores
- [x] app/core tests, assembleDebug
- [ ] Device: sleep chip countdown + DLNA fade/restore; startup during a pending full rescan; Convert keeps art (FLAC/MP3/ALAC/AIFF); multi-folder cover; fast scroll after paging; selection art tap; folder Play toast; drill-in/Back scroll

## Track menu (2026-10-01, open items from tasks/app-review-2026-10-01.md)
- [x] Convert Format in the track ⋮ menu (file-operations group, action_encoding_file)
- [x] Playback group hidden while no playback service is bound (MainScaffoldState.isPlaybackAvailable), per UI.md §A
- [ ] Device: open the ⋮ menu with and without a bound service; Convert Format opens the convert dialog

## P3 polish (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P3-1 Folder picker reopens after storage access is granted; PermissionActivity opened for storage closes itself once granted
- [x] P3-2 Library destination (type, keyword, filter, search) saved in onSaveInstanceState and restored before the first load
- [x] P3-3 Folder Play/Queue buttons 48dp with folder-named labels; track row click/long-press labels follow tap mode and selection (MainScaffoldState.listenerTapMode)
- [x] P3-4 Removed unreferenced MySelectionTracker.java and the now-unused action_open_track string
- [x] app tests, assembleDebug (lint not run)
- [ ] Device: folder picker → grant storage → picker reopens; rotate/theme change inside a collection, search and filter; TalkBack labels in Listener and Curator modes

## SonicNIO to top grade (2026-10-01)
Goal: prove and improve SonicNIO with evidence; then decide whether Netty can be retired.
- [x] 1a. JVM soak test (NioHttpServerSoakTest): 16 clients/8 s and 64 clients/20 s, 0 errors, counters drain to 0, no pool duplicates. Baseline on a Mac: ~700 MB/s, range TTFB p50 4.6 ms / p95 24 ms (16 clients), 18.8 / 75 ms (64 clients)
- [x] 1b. On-device (2026-10-01, SM-S931B as hotspot, Mac client, track 2021715542, 261 MB FLAC, after f1438181). Both engines are bound by the Wi-Fi link (about 8-10 MB/s); no meaningful difference:
    - SonicNIO: single 8.0-9.1 MB/s, seek TTFB p50 28-36 ms / p95 62-134 ms, parallel(4) 7.7-8.4 MB/s, app CPU 5-6%
    - Netty: single 5.0-9.2 MB/s, seek TTFB p50 32-42 ms / p95 69-108 ms, parallel(4) 8.4-10.0 MB/s, app CPU 4%
    - Earlier SonicNIO "33 MB/s parallel" was an artifact: streams were cut at 30 s by the header-deadline bug and the script counted them as complete. One unreproduced run had 4 of 4 parallel streams fail (no server log entries; hotspot tethering errors at the time)
- [x] 2. Fuzz (NioHttpServerFuzzTest): 23 hostile requests, mutated HTTP corpus (1,500 per run; 20,000 in one hunt) and random WebSocket frames (40 per run; 1,000 in one hunt). Found and fixed one leak: a protocol error after a queued reply (PING then malformed frame) left the connection with no interest ops, never closed; it now always ends with a CLOSE frame. Regression test in NioHttpServerTest
- [x] 3a. Remove object pools: soak before 648-728 MB/s, TTFB p50 5.0-5.7 ms / p95 13-27 ms; after 685-752 MB/s, 4.3-4.9 ms / 6.8-23 ms (Mac JVM, 3 runs each)
- [x] 3b. Split NioHttpServer (2,405 -> 1,165 lines): FileResponse (behind StreamSlots), WebSocketSession, NioWebSocketConnection (cross-thread requests via requestWebSocketWrite/requestClose), SerialExecutor, WebSocketHandshake, BoundedByteArrayOutputStream, FileContentTypes; accurate class Javadoc. Fixed on the way: 304 evicting a stream, onOpen after pipelined frames; removed dead metrics, resetOld, isHttpState, locks
- [x] 4. Protocol gaps: Expect: 100-continue, pipelining (in order), HTTP/1.0 closes without keep-alive, 30 s header-read deadline (slowloris), idle sweep every 1 s. A stalled reader was already closed by the idle timeout (test added). 5 tests; 3 fail on the previous commit. Soak client retries connect on host ephemeral-port exhaustion (BindException)
- [x] 5. Observability: counters (streams, evictions, 429/503, bytes/s), Android Log tags, Music Center diagnostics line
    - [x] 5a. Logging: replace System.out/err in NioHttpServer and RateLimitingHandler with java.util.logging (logcat tag NioHttpServer); per-connection idle closes at FINE, evictions and limits at INFO, errors at WARNING with the exception
    - [x] 5b. Counters: NioHttpServer.getStats() snapshot (connections, streams, requests, bytes sent, evictions, 503 rejections, header timeouts, idle closes) plus RateLimitingHandler 429 count; unit-tested over a real socket
    - [x] 5c. Diagnostics line on the Music Center Server tab (ServerDiagnostics, 5 tests; WebServer.getDiagnostics). Compiles and unit-tested; not yet seen on device (phone locked)
    - [x] Device (2026-10-02): "1 stream • 3.0 MB/s • 6 requests" while the Mac downloaded at a 3 MB/s cap; updates every 2 s; wraps between values only
- [x] 6. Decision gate: Netty retired (ADR-037); see "Retire Netty and the engine selector" below
Out of scope: HTTP/2, TLS, chunked request bodies.

## Retire Netty and the engine selector (2026-10-01)
Decision: SonicNIO matched Netty on device (step 1b); Netty never actually ran before 4bd8d78b. User chose to remove the selector too.
- [x] Delete :server-jupnp-netty (settings.gradle, app/build.gradle, libs.versions.toml, Netty packaging/proguard rules)
- [x] Replace CompositeWebServer with NioWebServerImpl in ServerModule; delete CompositeWebServer
- [x] Remove engine selector: Settings (SettingsScreen, SettingsActivity, settings.xml, arrays.xml), Music Center (MediaServerPage, MediaServerState, AudioHubSheet, DialogInterop, MainOverlayHost, MainScaffoldCallbacks, MainActivity), service restart-on-engine-change listener
- [x] Remove PREF_SERVER_ENGINE/DEFAULT_SERVER_ENGINE; migration clears the stale preference
- [x] Update tests (accessibility, screenshot) and docs (ADR-037, README, DESIGN, UI, USER_GUIDE, WEBUI, others, CHANGELOG)
- [x] Verify: 215 unit tests, debug build, R8 release minify, androidTest compile; on device the Server header is SonicNIO and the saved "netty" preference was deleted. Rendered previews checked for Settings and the Server tab. Screenshot validation: the same 30 previews fail on unchanged HEAD (stale baselines), so no new failures; baselines not regenerated here

## UPnP ContentDirectory fixes (2026-10-02)
Found by an end-to-end check against the phone (description, SCPD, Browse, GetProtocolInfo, GENA all work; SSDP untestable from the Mac, its firewall drops the replies).
- [x] Paging: in-memory browsers ignored StartingIndex/RequestedCount ("Recently Added" returned 615 for 20). Page centrally in AbstractContentBrowser.browseChildren; DB-paged folders (album, artist, genre) opt out. SourceFolderBrowser drops its own paging (it stopped at maxResults, not first+max, and never paged subfolders)
- [x] Unknown ObjectID (or missing item metadata) returns error 701 No such object instead of an empty success
- [x] UPnP port: unsupported method (HEAD) gets 405 with Allow, bad URI 400, instead of 500 with the exception text
- [x] Root Browse: 843 ms cold, 20-50 ms cached (the 2 s was during a library scan). "All Songs" page 6.0 s -> 1.07 s by paging tracks before building DIDL items. Artists TotalMatches used a different query (2505 vs 2549 listed); fixed
- [x] Speed (device, 8305 tracks): server address resolved once per request instead of 2-3 network-interface scans per track ("All Songs" whole list 15.7 s -> 4.8 s); playlist looked up once per request instead of per track and one library scan shared by page and TotalMatches (collection page 0.65-1.8 s -> 0.5-0.7 s); Sources one scan instead of two (1.1 s -> 0.6 s)
- [ ] Open (low priority): uncached root Browse 0.8-1.6 s (each child folder's count loads the library; 30 s browse cache hides repeats); whole "All Songs" list still 4.8 s
- [x] Unit tests (BrowsePagingTest 6, UpnpRequestCheckTest 3); on device: paging consistent in 8 folder types, 701 for unknown id, HEAD / -> 405

## DLNA compliance batch (2026-10-02, from the DLNA server review)
- [x] 1. Search: implement ContentDirectory.Search (UPnP criteria: and/or/parentheses; =, !=, contains, doesNotContain, derivedfrom, exists; dc:title, upnp:artist, dc:creator, upnp:album, upnp:genre, upnp:class); "*" and audioItem class return all tracks; paged; unit-tested parser
- [x] 2. res@duration in H:MM:SS.mmm (was "04:23")
- [x] 3. dc:date: valid ISO date from the year field (was "2023-06-09-01-01")
- [x] 4. Advertise only real capabilities: X_DLNACAP and Samsung ProductCap
- [x] 5. DLNA.ORG_PN only for real DLNA profiles (MP3), same in DIDL and contentFeatures header
- [x] 6. res@bitrate in bytes per second
- [x] 7. dc:creator = artist
- [x] 8. Remove artistDiscographyURI -> /hires_badge (404)
- [x] 9. restricted="1" (read-only server)
- [x] Verify: 244 unit tests (UpnpSearchTest 10, DidlValuesTest 3); device: DIDL item fields, Search (383 for "love", 10 for audio by Miles, 8305 all audio, 0 for album class, 708 for malformed), empty caps, contentFeatures without PN for FLAC
- [x] 10/11 (2026-10-02): Server header "Android/16 UPnP/1.0 MusicMate/<ver> [SonicNIO/2.2]" (ServerHeaderTest); modelName "MusicMate". Verified on device on both ports
- [x] 13 (2026-10-02, user chose thumbnails): second albumArtURI /coverart/tn_<key> tagged JPEG_TN (CoverThumbnails, 3 tests); full-size cover stays first. Device: 160x160 JPEG, 86 ms first / 25 ms cached; missing cover gives a default thumbnail
- [x] Windows/Xbox X_MS_MediaReceiverRegistrar:1 (2026-10-02): MediaReceiverRegistrarService on jUPnP's base class, bound with the Microsoft service type (jUPnP's annotation type was wrong); test 2; device: listed, IsAuthorized/IsValidated return 1. Not tried with a real Windows PC or Xbox
- [x] Samsung (2026-10-02): X_GetFeatureList (BASICVIEW audio at root "0") and getMediaInfo.sec -> MediaInfo.sec: SEC_Duration=<ms> (formats checked against UMS source); ProductCap advertises getMediaInfo.sec only; tests SamsungFeatureListTest (2) + duration; verified over the network on device. Not tried with a real Samsung TV. X_SetBookmark (video resume) not needed

## Sony / LG / Toshiba (2026-10-02, evidence: UMS renderer profiles; no Toshiba profile exists)
- [x] 1a. Root container searchable="1" (Search covers the whole library)
- [x] 1b. SearchCaps add upnp:class, dc:creator (both supported)
- [x] 1c. SortCriteria (BrowseSort, 4 tests; device: title +/-, artist+title, date): sort by dc:title, upnp:artist, upnp:album, dc:date (+/-) for Browse and Search; advertise only those
- [x] 1d. SystemUpdateID changes (library version = count:max(id):sum(fileLastModified), checked at most every 30 s; bump on a real change not yet seen on device) when the library changes (was fixed at 1)
- [x] 1e. Client detection and log (ClientRegistry, 5 tests; device: Sony/LG headers named, logcat DlnaClients): User-Agent / X-AV-Client-Info per client, logged once with headers; "clients seen" on the Server tab (gives Toshiba evidence)
- [x] 2. Time-based seeking (2026-10-02; TimeSeek 7 tests, start-offset 2 tests; device: FLAC 120 s / 300 s land on seek points with frame sync and match the file, MP3 60 s proportional on a frame, DSD/M4A/WAV/AIFF 406 with OP=01, past end 416). Not tried on a real LG/Sony TV: TimeSeekRange.dlna.org (npt) -> byte offset (FLAC SEEKTABLE, WAV exact, MP3 proportional); DLNA.ORG_OP=11 only where supported; LG 2022+ seeks only by time
- [x] 3. Formats a TV cannot play (user chose: convert FLAC/ALAC to PCM, hide DSD). Not yet tried on a real Sony/LG/Toshiba TV
    - [x] 3a. ClientFormatProfile (5 tests; named so it does not clash with the existing, unused ProfileManager/ClientProfile) from User-Agent / X-AV-Client-Info: Sony TV (plays FLAC; convert ALAC), LG TV (FLAC only on 2022+ and the UA cannot tell the year: convert FLAC and ALAC), Toshiba TV (no evidence yet: hide DSD only); other clients unchanged
    - [x] 3b. Hide DSD for TV profiles (device: LG UA sees 8299 of 8305 in All Songs and Search, 0 DSD; BubbleUPnP all 8305; browse cache keyed by profile; container childCount still counts DSD) in Browse and Search, with paging and TotalMatches consistent (RemoteClientInfo injected into the actions)
    - [x] 3c. NioHttpServer streaming body (StreamingResponse; 4 tests incl. a no-busy-loop check that fails with parking removed; ADR-036 rule 7) (producer thread, bounded buffer, write interest paused while empty, no busy loop), tested
    - [x] 3d. FLAC -> WAV (FlacToWavTest 3; device: 2 FLACs with an MD5 (16- and 24-bit) MATCH the STREAMINFO MD5, 24/96 at 6.3x real time, first byte 75 ms, Range and time seek exact) (native bit depth and rate) with the pure-Java FlacDecoder; exact Content-Length from STREAMINFO; byte Range and time seek by sample-accurate seek; proof: MD5 of the WAV data equals the FLAC STREAMINFO MD5
    - [ ] Known flaky (2026-10-02, predates 3c): NioHttpServerTest fails about 1 run in 16, a different test each time (Broken pipe on POST, connection closed before headers, missing Content-Length): the server closes a connection early. Not reproduced in 15 targeted runs; capture with the loop script and fix
    - [x] 3e. ALAC -> WAV via FFmpegKit (MediaExtractor reports ALAC as audio/unknown on this Samsung, so MediaCodec cannot be used); device: 16- and 24-bit ALAC identical to Apple afconvert, exact length, Range and time seek match
    - [x] 3f (FLAC; ALAC with 3e). DIDL for converted tracks (device: LG sees audio/wav .wav CI=1, BubbleUPnP the original; size is an estimate from the duration) advertises audio/wav, PCM size and OP for that client

## Playback control vs monitor fixes (2026-10-02, from the playback review)
- [x] 1. isControllable: only a DLNA renderer (DMRPlayer) that MusicMate was asked to drive (switchPlayer(..., controlled=true)); no side effect. Passive streams (browser, BubbleUPnP-driven renderer) are followed, never driven
- [x] 2. Transport commands (next/previous/pause/resume/seek/stop) on a monitored stream do nothing instead of reaching the Android MediaController path
- [x] 3. External apps (UAPP, HiBy, Poweramp): when MusicMate sent the track and the app stops at its end, play the next queued track (ExternalTrackEnd: STOPPED within 3 s / PAUSED within 1 s of the end; 3 s grace after sending a track; tests 4). Device test blocked: HiBy was not running, MusicMate fell back to ACTION_VIEW, HiBy launched but did not play (no media session)
- [x] 4. Remove the unused RUNNING_MODE field; fix the stale "NO internal ExoPlayer" comment
- [x] Verified on device: passive HTTP requests now produce nowPlaying/playbackState broadcasts (none before); pause on a monitored stream is ignored. Controlled DMR path not exercised (no renderer on this network)
- [ ] Verify external continuation (fix 3) with HiBy/Poweramp on the phone
- [x] Decided (player priority, below): automatic switches never interrupt what is playing

## Player priority (2026-10-02, user: DLNA stream > DLNA renderer > local > external; UX rules agreed)
- [x] Explicit choice in MusicMate (switchPlayer controlled=true) always wins
- [x] Automatic switches (a TV/browser stream, an external app starting) never interrupt a target that is playing; when it is idle, the higher-ranked source wins: DLNA stream 4 > chosen DLNA renderer 3 > local 2 > external app 1
- [x] A passive stream counts as playing for the track's length after its request (it reports no state)
- [x] Now Playing follows a stream only if that stream is the current target (not a stream that lost to music already playing)
- [x] A controlled renderer's own pre-fetch (same device) is never a new stream
- [x] Startup / fallback default: last chosen player, else DLNA renderer > local > external
- [x] Tests (PlayerPriorityTest 4); device: idle + browser stream -> followed; local playing + stream -> local keeps priority (logged); local paused + stream -> stream takes over
- [x] Activity beats idleness (user "ok" after Poweramp was not followed): an idle target never blocks one that starts; rank only decides between two starting within 5 s (automaticSwitchAt). Device: Poweramp song followed while an idle renderer was selected
- [x] "Last player" saved only for the listener's choice, not automatic switches
- [x] Now Playing trackId 0 for an external app's song: TrackEntity.copy() did not copy the id (findMusic returns a copy). Fixed; TrackEntityTest fails without the fix
- [x] Device check: Now Playing shows the library id for an external app's song (trackId 2873483536, was 0)
- [x] A paused external app that resumes was not followed (no active-session change): per-session state watchers. Device: Poweramp paused, MusicMate started (local), Poweramp resumed -> followed
# SonicNIO metadata optimization and measurements (2026-10-03)

- [x] Preserve current FileResponse/runtime/APK baseline and add reproducible metadata and request benchmarks.
- [x] Replace unnecessary ETag hexadecimal formatting and per-response date formatter construction; verify byte identity, concurrency and existing HTTP behavior.
- [x] Run interleaved before/after host measurements and available phone checks; record all samples, allocation, throughput and latency without implying a Netty comparison.
- [x] Run core/UPnP tests and debug build, inspect the incremental diff and publish results in PERFORMANCE.md with evidence links.

Scope: implement the research report's first metadata optimization. Header-token caching, parser changes and buffer pooling remain separate experiments so measurements can be attributed to this change.

Result: [PERFORMANCE.md §11](../PERFORMANCE.md#11-metadata-encoding-follow-up), [complete report/evidence](performance/sonicnio-metadata-2026-10-03/REPORT.md). ETag/date allocation medians fall 94.3%/86.8%; 304/206 preparation times fall 13.1%/5.5%. Six interleaved phone samples show seek median 16.282 → 7.804 ms with essentially unchanged 39.43 MiB/s four-stream throughput. Host bulk throughput is unchanged. All 207 core/UPnP cases and debug build pass; bytes, metadata and installed APK identity verified. Optimized APK remains installed. Small-sample latency gains and absent Netty comparison remain explicit.
