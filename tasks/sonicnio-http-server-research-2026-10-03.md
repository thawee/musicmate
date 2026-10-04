# HTTP server implementation research for SonicNIO

Research date: 2026-10-03. This is source inspection and an implementation proposal. No production code was changed and no new performance results were measured for this research.

Follow-up: the first ETag/date proposal is now implemented and measured in [PERFORMANCE.md section 11](../PERFORMANCE.md#11-metadata-encoding-follow-up), with [full methodology and evidence](performance/sonicnio-metadata-2026-10-03/REPORT.md). The remaining proposals are still research.

## Sources and reuse constraints

Reviewed shallow checkouts at these exact revisions:

| Project | Revision | Findings |
| --- | --- | --- |
| [hella-http](https://github.com/bbeaupain/hella-http/tree/3aba3f30da3d082a3d79f869587721b4a7bcd8ee) | `3aba3f30da3d082a3d79f869587721b4a7bcd8ee` | MIT license. README announces the move to Hipshot. Linux `io_uring` through `nio_uring`; README says Java 8, but the checked POM targets Java 17. |
| [yelmach/http-server](https://github.com/yelmach/http-server/tree/fa2e782bb327f5d9dd28e69e5806560eda701021) | `fa2e782bb327f5d9dd28e69e5806560eda701021` | Java NIO selector server. No LICENSE/COPYING file or explicit reuse grant was found in the reviewed tree. Implement ideas independently; establish permission before copying code. |

Hella's published benchmark uses wrk on two EC2 c5.2xlarge machines. It does not establish Android music-streaming performance or a SonicNIO/Netty comparison. Yelmach's README claims production readiness and 103 parser tests; its repository also contains a student-assignment audit. Neither claim substitutes for our own workload and protocol verification. Third-party tests were inspected, not executed during this research.

## Recommendations

| Priority | Source idea | Original SonicNIO implementation to evaluate | Expected benefit and limit |
| --- | --- | --- | --- |
| 1 | Hella pre-encodes status/header tokens; yelmach uses an immutable date formatter. | Remove avoidable ETag/date allocations first. Then evaluate a fixed table of common response tokens. | Potentially lower CPU/allocation and seek/artwork response latency. These are small per-request costs; bulk FLAC throughput may barely change. |
| 2 | Yelmach has explicit parser phases, separate size limits and multi-value headers; Hella decodes from a ByteBuffer. | Preserve and validate framing-header occurrences, then use incremental header scanning with a retained cursor if profiling supports it. | Better malformed/fragmented-request handling; fewer repeated scans/copies under browse and control traffic. Protocol work must preserve existing clients. |
| 3 | Hella reuses direct buffers. | Profile generated PCM allocations after existing batching; only consider a capped buffer pool if they remain a measured bottleneck. | Possible reduction in GC/copy cost. Ownership and global memory accounting are more important than reuse itself. |
| Already present | Yelmach uses `FileChannel.transferTo` with a per-turn allowance. | Keep SonicNIO's existing transfer path and measured 256 KiB default. | This is already implemented. Copying yelmach's 32 KiB allowance offers no established throughput gain. |

### First change: file-response metadata and header encoding

Primary sources: [Hella ResponseEncoder](https://github.com/bbeaupain/hella-http/blob/3aba3f30da3d082a3d79f869587721b4a7bcd8ee/src/main/java/sh/hella/http/codec/ResponseEncoder.java), [yelmach ResponseBuilder](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/src/http/ResponseBuilder.java), [yelmach StaticFileHandler](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/src/handlers/StaticFileHandler.java).

Current [FileResponse](../core/src/main/java/apincer/music/core/http/FileResponse.java) hashes a metadata string containing path, size and modification time. It does **not** hash the audio file contents. It then formats all 32 SHA-256 bytes with `String.format`, retaining only the first 16 hex characters. It also creates a `SimpleDateFormat` for every Last-Modified value. These are concrete avoidable costs.

Implementation sequence:

1. Replace formatted hexadecimal conversion with an original fixed-digit encoder for the first eight digest bytes. Preserve the exact existing ETag, including quotes and size suffix.
2. Use one immutable GMT formatter with explicit English locale and the existing two-digit day representation. Verify Android API/desugaring compatibility before choosing `java.time`. Do not share mutable `SimpleDateFormat` across workers.
3. Measure those changes independently. If response encoding remains significant, pre-encode a fixed set of common status lines/header-name prefixes. Keep dynamic Content-Length, Content-Range, validators and DLNA values per response. Never cache arbitrary client/application strings indefinitely.

A bounded metadata cache keyed by path/size/mtime is a separate inference, not an implementation found in either project. Consider it only after measuring repeated stat/MIME/validator work and defining replacement/invalidation behavior. Do not cache open file handles or assume a metadata validator proves file-content identity.

### Parser improvement: original implementation with focused tests

Primary sources: [yelmach RequestParser](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/src/http/RequestParser.java), [HttpHeaders](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/src/http/HttpHeaders.java), [parser tests](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/test/http/RequestParserTest.java), [Hella RequestDecoder](https://github.com/bbeaupain/hella-http/blob/3aba3f30da3d082a3d79f869587721b4a7bcd8ee/src/main/java/sh/hella/http/codec/RequestDecoder.java).

SonicNIO's HttpRequest is nested in [NioHttpServer](../core/src/main/java/apincer/music/core/http/NioHttpServer.java). Its header map overwrites earlier values with the same normalized name. Thus, duplicate framing fields lose information before validation. Preserve occurrences long enough to reject ambiguous Content-Length values and unsupported Transfer-Encoding combinations deterministically. Use locale-independent normalization. Define the repeated-identical-Content-Length policy explicitly and validate it against the HTTP specification before implementation.

SonicNIO also uses `ByteArrayOutputStream.toByteArray()` and string splitting during parsing. A retained scan cursor and explicit request-line/header/body phases can avoid repeated header scans and redundant copies. Preserve body limits, exact body length, pipelining/keep-alive behavior and handler isolation. This is a scoped improvement, not a replacement decoder.

Yelmach has 8 KiB request-line, 16 KiB header and 4 KiB URI limits, with disk spilling for large bodies. Borrow the explicit-limit and fragmentation-test concepts, not those exact limits or disk-upload functionality: MusicMate's DLNA XML/control workload differs. Its parser copies incoming bytes into an accumulation buffer and calls `toByteArray`; copying that implementation would not solve allocation cost. Its URLDecoder-based path handling also needs independent compatibility review.

### Buffer reuse: conditional experiment

Primary source: [Hella ObjectPool](https://github.com/bbeaupain/hella-http/blob/3aba3f30da3d082a3d79f869587721b4a7bcd8ee/src/main/java/sh/hella/http/util/ObjectPool.java).

Hella's pool is an unbounded concurrent queue. SonicNIO has already removed request/connection pooling after ownership problems and a lack of demonstrated benefit. Do not restore that design. If profiling warrants PCM chunk reuse, bound idle retained memory as well as checked-out memory, preserve the 8 MiB audio reservation budget, and test cancellation, partial writes, shutdown and exactly-once return. Decide how retained direct memory responds to Android memory pressure before enabling it.

## Implementations to avoid importing

- Hella's Linux `io_uring` backend is not a drop-in Android Java NIO replacement. It would require an Android native build, ABI coverage and verified syscall/policy availability on supported devices. No such compatibility validation was performed here.
- Hella's encoder puts the entire response body into a fixed-size output buffer and appends CRLF after it. That does not suit multi-megabyte audio streaming or SonicNIO's exact Content-Length framing. Its token caches are also unbounded.
- Hella's connection reaper uses wall-clock timestamps and closes connections from its scheduler. Keep SonicNIO's monotonic, progress-based deadlines and selector ownership.
- [Yelmach ClientHandler](https://github.com/yelmach/http-server/blob/fa2e782bb327f5d9dd28e69e5806560eda701021/src/core/ClientHandler.java) updates activity at entry to its write handler, before successful transmission. Keep SonicNIO's successful-byte progress accounting; writable events alone must not extend a stalled stream indefinitely.
- Larger buffers, a new backend or a smaller/larger transfer allowance have no measured advantage merely because another server uses them.

## Validation and measurement gates

Before changing production code, archive the current revision/source hashes, APK, JVM/device details and baseline measurements. Keep each optimization independently selectable or independently benchmarked to establish attribution.

| Experiment | Correctness checks | Measurements |
| --- | --- | --- |
| ETag/date encoding | Byte-identical validators and dates; dates near midnight/month/year boundaries; concurrent formatting; HEAD, conditional requests and range semantics. | Metadata/header microbenchmark with warmup and allocation measurement, then repeated artwork/seek requests. |
| Common token encoding | Byte-identical headers; dynamic values never shared across responses; exact body framing and partial-header writes. | Allocations/request, CPU/request, request rate and first-byte/seek p50/p95 under browse/artwork contention. |
| Incremental parser | Every split point for representative requests; CRLF splits; limits; duplicate/mixed-case framing fields; body splits; malformed requests; keep-alive and queued requests. | CPU and allocation for identical fragmented and unfragmented workloads; bounded memory under slow/incomplete requests. |
| PCM buffer reuse | Exact bytes; short/failing producers; zero/partial writes; cancellation/stop races; memory reservations and idle-pool caps. | Real decoder-to-WAV throughput and first byte, GC/allocation, peak memory and competing seek latency. |

Use repeated, interleaved before/after runs, matching files, clients, concurrency, cache/warmup and connection reuse. Include single and four concurrent native streams, actual FLAC/ALAC-to-WAV decoding, and seek/artwork/control contention on the phone. Record hashes, errors, CPU, allocation/GC, memory, throughput and latency percentiles. Retain changes only when gains survive repeated trials without correctness, memory or seek-latency regressions.

For Netty, prove which engine is active and use the same device, payloads, connection policy and workloads. No current apples-to-apples Netty result exists. These repositories provide implementation ideas, not evidence that SonicNIO beats Netty.

Existing measured results remain in [PERFORMANCE.md](../PERFORMANCE.md#10-throughput-follow-up): synthetic small-write PCM batching improved 6.33×, while real-phone throughput showed no substantial gain and some native-host medians regressed. None of those measurements evaluates the new proposals above.
