# one-nio review for SonicNIO

Reviewed 2026-10-03. Recommendation: borrow its immediate-write/readiness pattern in a measured, selector-owned experiment after profiling the current Android seek tails. Keep SonicNIO's bounded streaming queues, transfer budget and progress deadlines. Do not replace the transport with one-nio for this app.

This is source research. No one-nio code was integrated, third-party tests executed, or new performance measurements taken. Expected benefits below are hypotheses.

## Reproducible source scope

Pinned upstream revision: [`fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109`](https://github.com/odnoklassniki/one-nio/tree/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109). Read-only checkout: `/private/tmp/musicmate-one-nio-research-20261003`. SonicNIO comparison uses the current working tree, including the three earlier metadata/parser experiments; HEAD is `0d28ca052df66e23032d2c0afc0e3e6ba4e979bf`.

Primary sources at that revision:

- [Session](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/net/Session.java): immediate writes, partial-write subscriptions, queue accounting and release.
- [SelectorThread](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/server/SelectorThread.java): shared read scratch buffer and ready-session counters.
- [HttpSession](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/http/HttpSession.java), [Request](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/http/Request.java), [HttpServer](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/http/HttpServer.java): parsing and request dispatch.
- [Response](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/http/Response.java): header/body encoding.
- [JavaSocket](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/net/JavaSocket.java), [NativeSocket](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/net/NativeSocket.java): file-transfer implementations.
- [Server](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/server/Server.java): selector assignment.
- [NativeLibrary](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/os/NativeLibrary.java), [Management](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/main/java/one/nio/mgt/Management.java), [build](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/build.gradle.kts), [license](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/LICENSE.txt): runtime and reuse constraints.

## What applies to Android

The project has an Apache-2.0 license. Preserve applicable notices and license requirements if copying source; the proposals here can be implemented independently.

Its native path wraps Linux socket operations, including epoll and sendfile. The native loader checks Linux/64-bit system properties, extracts one `/libonenio.so` resource, loads it, then registers a JMX bean. The build uses host gcc, Linux JNI includes, `-ldl` and `-lrt`; it does not establish an Android NDK build or APK ABI packaging. Management references `java.lang.management` and `javax.management`. Java 8 source/target compatibility does not prove ART compatibility, particularly where the library accesses desktop JDK internals. A Linux/64-bit property check alone is insufficient validation for Android bionic, ABI, APIs and library loading.

Therefore adding the dependency is a separate porting project. The pure Java ideas below are considerably smaller experiments. Android compatibility was assessed from source, not tested by launching one-nio on a phone.

## Comparison and decisions

| Idea | Upstream behavior | Current SonicNIO behavior | Decision |
| --- | --- | --- | --- |
| Attempt a write before subscribing for readiness | `Session.write(QueueItem)` attempts output immediately when its queue is empty; subscribes after a partial write. | `processResponseQueue()` attaches a response and sets OP_WRITE; the loop subsequently calls `select()` before normal `handleWrite()`. | Highest-priority optimization candidate after tracing. Attempt a bounded write on the selector thread, preserving all normal completion/error paths. |
| Avoid redundant readiness updates | `listen()` checks whether the desired events changed. | Some paths already check interest bits; pending streaming writes and response publication set them directly. | Profile interest changes and wakeups first; add cheap guards only where useful. Java Selector behavior differs from native epoll. |
| Queue and readiness telemetry | Queue stats report item count and remaining bytes; selector counts operations, ready sessions and maximum ready batch. | Diagnostics already record worker wait/depth, a selector timing maximum, first successful output and terminal outcomes. | Extend tracing with response publication delay, remaining queued PCM, wakeup/empty-select counts and actual selected-key processing duration. |
| Read directly into the final request body | Allocate exactly Content-Length bytes, copy available body bytes, then fill the same array on later reads; retain incomplete header lines separately. | Accumulate the request in a bounded byte-array stream, then copy the exact body into the request object. | Defer. Could reduce large SOAP/control body allocations; likely little effect on bodyless range GET throughput. Preserve strict framing and establish an aggregate body-memory bound first. |
| Reuse selector read scratch storage | One 64,000-byte read array per selector, plus per-session fragment/body storage. | A configurable read ByteBuffer per connection; HTTP accumulates bytes separately, WebSocket reads use compacted connection storage. | Low priority for a few streaming clients. Would require ownership-safe copies of retained fragments and careful WebSocket redesign. Do not simply share the existing compacted buffer. |
| Native file transfer | JNI sendFile; Java fallback uses FileChannel.transferTo. | FileChannel.transferTo with a measured default 256 KiB allowance per turn. | Already present in portable form. Native sendfile is not evidence of a faster Android path. |
| Multiple selectors | Choose the smaller of two randomly sampled selectors by session count. | Single selector plus bounded worker/producer execution. | Defer until measurements show selector CPU saturation. Session count alone does not represent transcoding/stream traffic cost. |

### Immediate-write experiment: adaptation, not a wholesale copy

Relevant local code: [NioHttpServer](../core/src/main/java/apincer/music/core/http/NioHttpServer.java), [FileResponse](../core/src/main/java/apincer/music/core/http/FileResponse.java), [StreamingResponse](../core/src/main/java/apincer/music/core/http/StreamingResponse.java), [StreamDiagnostics](../core/src/main/java/apincer/music/core/http/StreamDiagnostics.java).

one-nio's synchronized write can execute on the caller thread. SonicNIO should retain selector ownership of socket writes. Once a worker publishes a response, the selector can attempt one bounded write during response processing, then subscribe only if output remains. This may remove a readiness round trip for small control responses and seek headers. It does not remove the worker-to-selector wakeup and is not expected to bypass Wi-Fi limits on long file streams.

Use the existing write/completion machinery so first-byte accounting, resource release, streaming park/wakeup, errors, keep-alive, pipelined leftovers and WebSocket upgrade callbacks remain consistent. A response-queue batch also needs a total fairness allowance: a per-response allowance alone lets many newly queued responses delay other connections. Do not write from workers, drain unlimited output or double-write a response later in the same turn.

The current loop sleeps 20 ms after more than ten quick empty selections. That duration is relevant to the observed seek tails, but no trace links a sleep to those requests. The existing `selectorTurn` timing surrounds pending-task processing before `select()`; it does not measure the entire selected-key processing batch. Instrument both distinctions before attributing the regression to parsing or changing the backoff.

### Patterns to retain rather than import

- Keep global PCM memory admission, bounded chunk queues and producer cancellation. Upstream Session append walks to the linked-list tail and has no queue admission bound in the inspected Session implementation; its write loop has no explicit per-turn fairness allowance.
- Keep monotonic, progress-based streaming timeouts. Upstream uses wall-clock access timestamps, including updates after an attempted write, which do not establish byte progress.
- Keep file/PCM streaming separate from full response serialization. Upstream `Response.toBytes(includeBody)` appends the full included body to its builder; this is unsuitable as a replacement for long audio bodies.
- Keep handlers that touch files/decoders off the selector. Upstream HttpSession calls HttpServer handling from request parsing by default; an application can supply its own dispatch policy.
- Keep SonicNIO's framing checks. The inspected HttpSession reads a single Content-Length using Request's first matching header, has no Transfer-Encoding framing handling in that parsing path, and skips storing headers beyond 256. Its parser is not a replacement for our duplicate-length and TE+CL checks. Its 2,048-byte line fragment limit and default 65,536-byte body limit are workload choices, not universal performance settings.
- Defer native TCP flags, CPU affinity, off-heap pools and generated routing. These add platform/ownership complexity without a measured bottleneck in our music workload.

## Measurement plan and release gates

The previous combined parser/copy phone comparison found median per-run seek maximum **19.874 → 24.908 ms (+25.3%)**, with adverse maxima in both series despite a lower seek median. See [PERFORMANCE section 13](../PERFORMANCE.md#13-request-framing-and-copy-reduction) and [archived parser report](performance/sonicnio-parser-2026-10-03/REPORT.md). Keep the two metadata changes recommended for retention; keep the combined parser/copy change held from release until investigated. All three remain in the current working tree/test APK. This review changes none of those states.

1. Preserve source revisions, APK hashes and the current baseline. Trace receipt/header parsing, worker submission/start, metadata work, response publication, selector dequeue, first attempted/successful write, selected-key processing, GC and backoff entry. Use monotonic timestamps, bounded trace storage and no per-chunk logging. Run an instrumentation-only control to assess its overhead.
2. Isolate the earlier framing/copy variants using the same harness and phone conditions. Match slow requests to server traces; distinguish client/header measurement time from server first-write time. Do not infer a cause from the 20 ms sleep's duration alone.
3. Build one candidate containing only the bounded selector-owned immediate-write change. Compare interleaved baseline/candidate APK runs on the physical phone, with fixed workload, warmups, connection reuse, files, network mode and controlled thermal state. Record every run, including adverse results, and use enough seeks to report p95/p99 with sample counts as well as maxima.
4. Repeat native ranges, seek/artwork/control requests, generated WAV/PCM, concurrent clients and slow-reader/cancel scenarios. Report MiB/s, TTFB, median/p95/p99/max latency, CPU, allocation/GC, memory high-water marks, queue depth and cancellation/resource counts. Verify byte/range hashes and converted sample output. Run core/UPnP tests and Android framing probes; add socket regression coverage for new completion/partial-write paths.
5. Retain only with repeatable benefit in the target workload, no repeatable seek-tail regression, no fairness/CPU-spin cost and all correctness/resource checks passing. Keep body-parser and shared-buffer experiments separate. If comparing Netty, use equivalent file/PCM routes, headers, keep-alive, transfer settings, correctness checks, hardware and clients; publish raw runs and uncertainty.

No measured before/after results exist for these one-nio-inspired proposals. The inspected [HttpServerTest](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/test/java/one/nio/http/HttpServerTest.java) is an example/test server; [PerfServerTest](https://github.com/odnoklassniki/one-nio/blob/fadea4dbd7dfce2e4415e9a40f58eb9abdd8c109/src/test/java/one/nio/net/PerfServerTest.java) exercises Unix sockets, mapped memory and FD passing. Neither supplies an Android music-streaming comparison against SonicNIO or Netty. This review establishes no throughput advantage over either project.
