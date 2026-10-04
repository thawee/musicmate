# Request framing and copy reduction: implementation and results

2026-10-03. Retained the changes for framing correctness and lower request-extraction allocation. They do **not** establish a music-streaming throughput improvement. Repeated phone measurements showed higher seek maxima, despite lower median seeks; that adverse result remains open for profiling.

## Approach and compatibility

Header-end scanning was already incremental; it was preserved. The selector now copies only headers for parsing, then one exact Content-Length body slice on completion. Pipelined leftovers and bytes following a WebSocket upgrade are copied directly from their ranges rather than first cloning the entire accumulation buffer. Public HttpRequest.parse still returns an owned copy of the trailing body bytes for existing callers; the selector uses parseHeaders separately.

Framing policy is informed by [RFC 9112 sections 5–6](https://www.rfc-editor.org/rfc/rfc9112.html#section-6.3):

- Repeated/comma-separated Content-Length values must contain valid unsigned decimal values and all be numerically identical. Repeated identical values are normalized for handlers; conflicts, signs, empty values/elements and numeric overflow return 400 and close.
- Content-Length with Transfer-Encoding returns 400 and closes. Duplicate Transfer-Encoding fields are rejected. A single field ending in chunked, without earlier repeated chunked/empty codings, remains unsupported and returns 501; invalid final framing and HTTP/1.0 transfer coding return 400.
- Invalid header names, whitespace before the colon, folded fields and missing colons return 400. Header-name lookup uses Locale.ROOT. Other repeated ordinary headers retain the existing last-value behavior.
- A declared body exceeding the remaining total request-buffer limit returns 413 before reading its body. This avoids integer addition overflow and follows the existing 2 MiB total buffer capacity.

This is a scoped correction, not a complete HTTP conformance rewrite, chunked decoder or new header-size policy. Lengths are compared as each field arrives, retaining a scalar value/error state instead of concatenating an ever-growing duplicate string. A regression covers 2,001 identical fields. Parser reuse clears previous framing state and headers.

Three variants were preserved and compiled against the same runtime: preceding metadata-optimized baseline, copy-only intermediate, and final copy-plus-framing implementation. Actual extraction class origins were recorded and checked. [Baseline sources](evidence/baseline/NioHttpServer.java), [copy-only sources](evidence/copy-only/NioHttpServer.java), [copy delta](evidence/copy-only.patch), [framing delta](evidence/framing.patch), [complete delta](evidence/complete.patch), [environment/source/APK digests](evidence/manifest.json).

## Extraction benchmark methodology

[ParserBenchmark](../../../tools/bench/ParserBenchmark.java) models selector extraction of a buffered request and a following 34-byte GET. Workloads have zero, 4 KiB, 256 KiB or 1 MiB bodies. Body bytes are deterministic; lengths, endpoints, tail length/prefix and consumed checksums are checked. Unit/socket tests verify complete slices and real request behavior separately. There is no socket, XML parsing, handler, decoder or audio transfer in this benchmark.

Two models are reported: fully buffered header/body/tail at header parsing, and staged arrival with at most 8 KiB available when headers are first parsed, matching the default read-buffer size. The completed accumulation buffer is prepared outside the timing loop in both models; socket reads, buffer growth and initial buffering allocation are excluded. Fully buffered large-body results are a capacity/coalescing scenario, not the normal default-read path.

Both models run baseline/copy-only/final/final/copy-only/baseline/baseline/copy-only/final JVM order on macOS ARM64 / Temurin 25.0.4, with identical 256 MiB heaps. Each mode has one unreported warmup and five reported samples per process: 20,000 operations for zero-body, 5,000 for 4 KiB, 500 for 256 KiB, 125 for 1 MiB. Each model produces 180 samples, 15 per variant/workload, 360 total. Allocation is current-thread allocated bytes, not retained heap or Android GC. No sample was removed.

### Staged arrival, primary model

| Body | Baseline µs/op | Copy-only µs/op | Final µs/op | Baseline bytes/op | Copy-only bytes/op | Final bytes/op |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Zero | 1.332 | 1.255 | 1.728 | 2,712 | 2,368 | 2,384 |
| 4 KiB | 1.268 | 0.801 | 0.812 | 23,216 | 6,464 | 6,480 |
| 256 KiB | 23.509 | 7.512 | 7.636 | 805,320 | 264,528 | 264,544 |
| 1 MiB | 84.775 | 29.680 | 30.824 | 3,164,616 | 1,050,960 | 1,050,976 |

For staged 1 MiB bodies, final allocation is 66.8% lower and time 63.6% lower than baseline. The copy-only comparison establishes where the main savings occur. The final request object adds 16 measured bytes compared with copy-only.

### Fully buffered arrival

| Body | Baseline µs/op | Copy-only µs/op | Final µs/op | Baseline bytes/op | Copy-only bytes/op | Final bytes/op |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Zero | 1.278 | 1.161 | 0.955 | 2,712 | 2,368 | 2,384 |
| 4 KiB | 1.193 | 0.775 | 0.780 | 23,216 | 6,464 | 6,480 |
| 256 KiB | 34.614 | 7.584 | 7.450 | 1,313,464 | 264,528 | 264,544 |
| 1 MiB | 139.407 | 29.888 | 29.378 | 5,245,624 | 1,050,960 | 1,050,976 |

Empty-request timing is inconsistent across models/processes: staged final process medians range from 0.796 to 2.834 µs. There is no reliable GET-speedup claim. Large-body allocation savings are structural; their absolute byte counts are also visible independently of timing/JIT variation. [All staged samples](evidence/staged.jsonl), [fully buffered samples](evidence/micro.jsonl), [all medians/minima/maxima](evidence/summary.json).

## Native host streaming

Used unchanged ThroughputBenchmark methods and streaming settings from [the preceding phase](../sonicnio-stat-2026-10-03/REPORT.md#host-streaming-and-regression-follow-up): 16 MiB deterministic bodies, one or four persistent full-file clients, a fifth 64 KiB range client in contention, one unreported two-second warmup and three reported two-second periods per mode/group. Before/final/final/before groups produce 24 samples, six per variant/mode. Every body/range and resource drain passes, with zero errors.

| Metric | Baseline median | Final median |
| --- | ---: | ---: |
| One native stream | 693.005 MiB/s | 684.666 MiB/s |
| Four native streams with seeks | 1,462.796 MiB/s | 1,466.103 MiB/s |
| Per-run seek p95 median | 3.244 ms | 3.423 ms |

Single-stream rate was about 1.2% lower; four-stream rate about 0.2% higher; seek p95 about 5.5% higher. No consistent host throughput/latency gain is established. This is predominantly a GET workload, not large POST-body extraction. [All native samples](evidence/throughput.jsonl), [diagnostics](evidence/throughput.stderr).

## Phone runs and adverse latency repeat

Same Galaxy S25 / SM-S931B, USB ADB forwarding 19000 to 9000, 9,940,667-byte native FLAC and 29,529,404-byte converted WAV. This is not Wi-Fi or audible renderer playback. Used the same full-original, twelve original ranges, four concurrent originals, converted WAV and four converted-range workload as previous phases. After/before/before/after/before/after installation order, SHA-256 verification on every install, full unreported workload warmup before each retained sample. Response-start proxy is after urllib receives headers; twelve-range p95 selects the maximum. Connections are newly opened by urllib.

Initial phone tail/start measurements were worse, so ran a separate identical six-install confirmation after the original set. All twelve retained runs and twelve warmups are preserved; no series or slower sample is excluded. Values below are medians of per-run measurements.

| Series | Seek median before → final | Seek maximum before → final | Four-stream MiB/s before → final |
| --- | ---: | ---: | ---: |
| Initial, 3 runs/variant | 16.135 → 13.512 ms | 19.677 → 23.985 ms | 39.1987 → 39.5671 |
| Confirmation, 3 runs/variant | 15.218 → 11.821 ms | 20.072 → 25.832 ms | 39.3437 → 39.6643 |
| Combined, 6 runs/variant | 15.485 → 12.743 ms | 19.874 → 24.908 ms | 39.2812 → 39.6157 |

Combined seek medians improved 17.7%, but the median per-run maximum worsened **25.3%**, consistently higher in both series. Combined original duration was 0.267333 → 0.261191 s, original response-start proxy 41.256 → 39.046 ms, WAV duration 0.887347 → 0.827581 s and WAV response-start 18.668 → 13.284 ms. Original response-start direction reversed between initial and confirmation; WAV/latency observations cannot be treated as established parser-caused speedups.

Native throughput is effectively unchanged. The tail-latency regression must remain visible and be profiled; these six-per-variant observations do not prove its cause. Device scheduling, JIT/cache state, USB/client activity and warmup effects remain possible influences. APK identity and hashes were controlled, but Android allocation/GC/CPU and thermal state were not measured.

Twelve retained runs passed **192 exact original/converted ranges, 48 concurrent-original hashes and twelve one-byte metadata probes**. Original/WAV hashes and ETag/Last-Modified/Content-Range/Content-Type match across builds. Warmups passed another 192 ranges and 48 concurrent hashes. [Initial runs](evidence/phone.json), [confirmation](evidence/phone-confirmation.json), [all warmups](evidence/phone-warmups.json).

## Protocol and regression verification

| Added case | Method and result |
| --- | --- |
| identicalLengths_areNormalizedAcrossLinesAndLists | Single, comma-separated and mixed-case repeated lines accept numeric five and preserve public parse body. Pass. |
| invalidOrConflictingLengths_areRejected | Empty, signed, conflicting, empty-list-element, nondecimal and overflow fields reject. Pass. |
| manyIdenticalLengths_remainOneCanonicalValue | 2,001 repeated fields retain one canonical value five. Pass. |
| reusedRequest_doesNotKeepPriorFramingOrBody | New header parse clears old Content-Length/body and changes path. Pass. |
| rangeCopy_isExactIndependentAndBoundedByWrittenBytes | Exact owned slice, no backing-array alias, no copying past written count. Pass. |
| everySplitPoint_preservesHeaderBoundaryAndExactBodySlice | Every split point across headers/body/pipelined tail preserves CRLFCRLF boundary and exact five-byte body. Pass. |
| duplicateIdenticalLengths_preserveBodyAndPipelinedNextRequest | Real socket POST dispatches hello once, then pipelined range returns 206. Pass. |
| ambiguousOrInvalidFraming_returns400ClosesAndNeverDispatches | Twelve invalid/ambiguous forms, including whitespace/folding, return 400, EOF and no POST handler call; pipelined bytes are not dispatched. Pass. |
| validChunkedFraming_isUnsupportedAndCloses | chunked and gzip/chunked return 501 and EOF. Pass. |
| oversizedLength_returns413BeforeBodyIsSent | Valid huge decimal length gets 413 and EOF without a body. Pass. |

The complete suite passed **176 core + 43 UPnP = 219 cases**, no failures/errors/skips; debug build and classpath export pass. Existing cases also cover phased body reads, Expect: 100-continue, keep-alive, WebSocket upgrades and streaming lifecycle. An initial missing Locale import caused compilation failure; it was corrected before successful validation. Bounded duplicate handling was subsequently finalized and all tests/build rerun before measurements. Both failed and successful logs are preserved. [Every individual test](TEST_RESULTS.md), [JUnit JSON](evidence/junit-results.json), [final validation](evidence/validation-final.log).

On the installed final Android APK, **17 raw-socket framing probes** independently passed: 12 bad/ambiguous forms return 400, two valid chunked forms return 501, oversized length returns 413, and identical repeated/comma-separated lengths return the exact one-byte native range. Every probe verifies connection closure. These target an unknown route for rejected requests and a read-only music range for accepted cases. [All phone framing results](evidence/framing-phone.json).

## Decision and reproduction

Retain framing correctness and exact-range copy reduction. Do not describe this as a native music throughput upgrade or hide the repeated phone tail regression. Real-device tail profiling is the next performance gate. No Netty/Grizzly comparison or physical-renderer continuity result was produced.

Preserve baseline and copy-only sources under their respective `SCRATCH/baseline` and `SCRATCH/copy-only` directories. Export the Gradle test runtime, then use [run-parser-comparison.py](../../../tools/bench/run-parser-comparison.py) with `compile`, `micro`, `staged`, and `throughput`. Store before/final APKs and use [the phone runner](../../../tools/bench/run-metadata-phone.py); use a separate scratch directory for the independent repeat so outputs are not overwritten. Run [check-phone-framing.py](../../../tools/bench/check-phone-framing.py) only against the final installed APK. [Archival/calculation script](archive-results.py) validates counts, source/APK/content identity and outcomes before writing evidence and summary.
