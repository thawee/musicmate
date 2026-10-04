# File metadata snapshot: implementation and measurements

2026-10-03. Follow-up to [the ETag/date optimization](../sonicnio-metadata-2026-10-03/REPORT.md). Retained the change for lower preparation cost: conditional-response preparation is about 4.3× faster and range preparation is 21.9% faster on the host. Bulk throughput is unchanged; end-to-end tail latency is mixed. This experiment does not compare Netty or Grizzly.

## Concept and implementation

Before this change, a normal GET constructor performed five File size/mtime lookups: one initial length, two lengths and one modification time while generating ETag, and another modification time for Last-Modified. It now obtains BasicFileAttributes once and passes its size and millisecond modification time to ETag/date generation. This replaces five metadata API lookups with one bulk attribute API call; native syscall counts were not independently traced.

There is no cross-request cache, open-file pool or retained metadata object. Each request reads current attributes. The previously implemented encoding, 256 KiB transfer allowances, PCM batching and resource limits are held constant. The opened channel's size is still read separately for actual body/range handling. Attribute lookup is not a lock or a guarantee against a file changing between lookup and opening.

HEAD also reads the attribute snapshot for length. A missing file now fails attribute lookup before stream admission, including HEAD; the existing server IOException path returns 404. Stable-file validators, dates, lengths and ranges remain unchanged. The production delta is in [metadata-only.patch](evidence/metadata-only.patch); the [baseline source](evidence/baseline/FileResponse.java) includes the earlier ETag/date encoding optimization.

## Benchmark method and integrity

Preserved the pre-edit FileResponse and APK. Compiled both variants into separate directories against the same runtime; actual microbenchmark class origin is recorded and validated. Every phone installation was SHA-256 compared with its corresponding local snapshot. The optimized APK remains installed. [Environment and all source/APK/evidence digests](evidence/manifest.json).

Used the [previous experiment's methods](../sonicnio-metadata-2026-10-03/REPORT.md#host-preparationallocation-methodology): macOS ARM64/Temurin 25.0.4, fixed 256 MiB JVM heap, 16 MiB temporary FLAC, 10,000-operation warmup and five 10,000-operation samples per mode per process. Six processes alternate before/after/after/before/before/after, producing 120 reported samples, 15 per variant per mode. Allocation is current-thread allocated bytes, not retained memory or Android GC behavior.

The private ETag signature now accepts the snapshot values. Its benchmark explicitly includes obtaining attributes before invoking the new signature, whereas the old signature performs its File lookups internally. Reflection/boxing overhead is included; complete constructor/header modes are the primary comparison. Date formatting is unchanged and serves as a control.

| Workload | Before µs/op | After µs/op | Time change | Before bytes/op | After bytes/op |
| --- | ---: | ---: | ---: | ---: | ---: |
| ETag including metadata | 19.904 | 7.464 | −62.5% | 776 | 1,040 |
| Date, unchanged control | 0.330 | 0.318 | −3.6% | 384 | 384 |
| Complete 304 + headers | 33.536 | 7.828 | −76.7% | 2,752 | 2,896 |
| Complete 206 + headers | 119.395 | 93.296 | −21.9% | 3,712 | 3,856 |

The attribute object adds 144 allocated bytes per conditional/range response, respectively +5.2%/+3.9%. The isolated ETag mode also includes reflection argument/boxing costs. No cache grows with request count. [All preparation samples](evidence/micro.jsonl), [medians, extrema and exact calculations](evidence/summary.json).

## Host streaming and regression follow-up

Used the unchanged [ThroughputBenchmark](../../../tools/bench/ThroughputBenchmark.java): deterministic 16 MiB bodies, one or four persistent full-file clients, with a fifth range client in the four-stream case. Full bodies are SHA-256 checked, ranges byte-compared, and connections/slots/audio reservations must drain. One unreported two-second warmup plus three reported two-second periods per mode/group; transfers finish after the period. Initial before/after/after/before groups produced 24 reported samples.

Initial four-stream seek p95 was higher after the change, so ran a separate six-group before/after/after/before/before/after confirmation for that mode, adding 18 samples. Phone measurements finished before this confirmation to avoid running both simultaneously. All 42 transfer samples are retained; no slower sample was removed.

| Series / metric | Before median | After median | Change |
| --- | ---: | ---: | ---: |
| Initial single-stream throughput, 6 samples/variant | 685.713 MiB/s | 688.151 MiB/s | +0.36% |
| Initial four-stream throughput, 6 samples/variant | 1,460.870 MiB/s | 1,446.135 MiB/s | −1.01% |
| Initial four-stream seek p95 | 2.865 ms | 3.356 ms | +17.1% |
| Confirmation four-stream throughput, 9 samples/variant | 1,462.969 MiB/s | 1,465.428 MiB/s | +0.17% |
| Confirmation four-stream seek p95 | 3.720 ms | 3.407 ms | −8.4% |
| Combined four-stream throughput, 15 samples/variant | 1,462.043 MiB/s | 1,456.711 MiB/s | −0.36% |
| Combined four-stream seek p95 | 3.562 ms | 3.407 ms | −4.4% |

There is no consistent throughput or tail-latency improvement. These seek values are medians of per-run p95, not pooled percentiles. In confirmation, the slowest after-run p95 was 10.284 ms versus 6.610 ms before, despite a lower after median. Keep that adverse result visible. All runs have zero errors and pass content/resource checks. [Initial samples](evidence/throughput.jsonl), [confirmation samples](evidence/throughput-confirmation.jsonl), [initial diagnostics](evidence/throughput.stderr), [confirmation diagnostics](evidence/throughput-confirmation.stderr).

## Phone measurements

Same Galaxy S25/SM-S931B and USB ADB forwarding as before, with the same 9,940,667-byte FLAC and 29,529,404-byte converted WAV. This is not Wi-Fi or a listening test. Reused the archived workload: full original, twelve 256 KiB original ranges, four concurrent originals, converted WAV and four 64 KiB converted ranges. Body lengths, WAV framing, ranges and hashes are verified.

Six installs alternate after/before/before/after/before/after. Each has one complete unreported warmup then one retained workload. A one-byte probe compares ETag, Last-Modified, Content-Range and Content-Type across every build. Response-start timing is after urllib receives headers, not first body byte. The twelve-range p95 selects the maximum. Connections are newly opened by urllib.

| Metric | Before median, 3 runs | After median, 3 runs | Change |
| --- | ---: | ---: | ---: |
| Original full duration | 0.259691 s | 0.263111 s | +1.3% |
| Original response-start proxy | 49.459 ms | 29.180 ms | −41.0% |
| Original seek median | 14.679 ms | 9.539 ms | −35.0% |
| Original seek maximum | 18.030 ms | 19.165 ms | +6.3% |
| Four-original throughput | 39.7009 MiB/s | 39.6432 MiB/s | −0.15% |
| Converted WAV duration | 0.868362 s | 0.852574 s | −1.8% |
| Converted response-start proxy | 14.902 ms | 9.079 ms | −39.1% |

The median latency observation is promising but tail latency did not improve. Seek medians span 13.252–15.720 ms before and 8.483–15.514 ms after, with overlapping ranges. Three samples per APK, cache/JIT/device scheduling and USB/client effects limit causal claims. Throughput is essentially unchanged. Do not combine these numbers with the previous phase's latency percentages as a cumulative improvement.

All measured runs passed **96 exact original/converted ranges, 24 concurrent-original hashes and six metadata probes**. WAV/original hashes and metadata matched across builds. The six warmups passed another 96 ranges and 24 concurrent hashes. [Retained phone runs](evidence/phone.json), [warmups](evidence/phone-warmups.json).

## Tests, decision and reproduction

| Regression coverage | Method/result |
| --- | --- |
| Existing encoding identity test, extended | Five file sizes now also compare actual constructor ETag/Last-Modified with legacy generation, alongside 13 date boundary/extreme cases. Pass. |
| replacedFile_hasFreshValidatorsAndRangeLengthWithoutCachedMetadata | Change three-byte file to seven bytes with known mtime; old conditional validator returns 200 with fresh metadata, new validator returns 304, and bytes 1–4 return 206 with length four and total seven. Pass. |
| missingMetadata_doesNotAcquireOrReleaseAStreamSlot | Missing path throws IOException before any admission/release call. Pass. |

**166 core + 43 UPnP = 209 cases pass**, zero failures/errors/skips. Debug assembly and classpath export pass. Existing socket cases cover HEAD, validators, range bytes and streaming lifecycle. [All individual tests](TEST_RESULTS.md), [JUnit JSON](evidence/junit-results.json), [build log](evidence/validation.log).

Retain the bounded per-request snapshot for lower preparation time, accepting its small allocation increase. End-to-end throughput is unchanged and tail behavior is mixed. Parser work, arbitrary header caching and buffer pools remain separate proposals. Android allocation/GC/CPU/battery and physical renderer continuity were not measured. No verified Netty comparison exists.

Reproduce with [run-metadata-comparison.py](../../../tools/bench/run-metadata-comparison.py): preserve baseline source in `SCRATCH/baseline/FileResponse.java`, export the Gradle classpath, run `compile`, `micro`, then `throughput`. Add `throughput file-four` to run the separately stored confirmation series. Use preserved before/after APKs and [run-metadata-phone.py](../../../tools/bench/run-metadata-phone.py) for the phone. [The archival script](archive-results.py) checks sample/test counts, class/APK/source hashes and response identity and records all calculations.
