# SonicNIO metadata optimization: implementation and measured results

2026-10-03. Implemented the first proposal from [the source review](../../sonicnio-http-server-research-2026-10-03.md). Retained the change: it substantially reduces response-preparation allocation, preserves validators and dates, and showed lower seek latency in this phone sample. Bulk throughput was unchanged. There is no new Netty comparison.

## Concept and implementation

Hella's pre-encoded response tokens suggested avoiding repeated encoding, while yelmach's immutable date formatter suggested avoiding per-response formatter creation. Inspection of our own FileResponse identified two smaller opportunities before changing the general header encoder:

1. ETag generation hashed a metadata string, formatted all 32 digest bytes through `String.format`, then discarded all but the first 16 hex characters. An original fixed-digit encoder now emits only those eight bytes. The hash algorithm, path/size/mtime inputs, size suffix, lowercase digits and quoted ETag stay the same. This is not a file-content hash.
2. Last-Modified formatting created a mutable formatter and GMT timezone on every file request. A shared immutable English/GMT formatter now handles dates from the Gregorian cutover through year 9999. Unusual timestamps retain the old formatter, preserving its historical-calendar and unsigned large-year output. Minimum supported Android API is 36; `java.time` is available.

File transfer budgets, PCM batching, admission limits, parser and general header encoder were held constant. No third-party implementation was copied. See [the exact production delta](evidence/metadata-only.patch) and [preserved pre-edit source](evidence/baseline/FileResponse.java).

## Baseline integrity

The baseline is the immediately preceding SonicNIO implementation, including the PCM batching work in PERFORMANCE.md section 10. It is not the earlier Grizzly-inspired baseline or Netty.

Before editing, preserved FileResponse.java, the current APK and classpath. Baseline source SHA-256 is `1a14c69759942082f3b387b7baf388b4263b3766cdf3236368b14df684a85a39`. Both variants were compiled into isolated directories against the same runtime. Only FileResponse differs. The microbenchmark records the actual class origin on every sample and the runner validates it. Source, harness and APK digests are recorded in [the manifest](evidence/manifest.json).

The unchanged phone APK was independently checked against its installed SHA-256 before measurements. Every subsequent installation was checked against its preserved local APK. The optimized APK remains installed and launched. The workspace already contained earlier uncommitted changes; the metadata-only patch establishes this experiment's scope. The diff against main was inspected; the existing branch contains extensive unrelated differences, which were left intact.

## Host preparation/allocation methodology

[MetadataBenchmark.java](../../../tools/bench/MetadataBenchmark.java) executes four workloads: private ETag generation; private date formatting; complete 304 response construction/header encoding; complete 206 range response construction/header encoding, including opening/closing the file. Reflection overhead is included in the two private-method measurements. File metadata system calls and normal constructor work remain included.

Uses a sparse 16 MiB temporary `.flac` file, a pre-parsed request per constructor mode, varying modern timestamps for dates, and checksums consumed through a volatile field. Each process runs one unreported 10,000-operation warmup and five reported 10,000-operation samples for each mode. The 304 and 206 statuses are checked on every construction. No body is transmitted in this benchmark.

Ran six separate JVMs in before/after/after/before/before/after order with identical `-Xms256m -Xmx256m` settings. There are 15 samples per variant per mode, 120 reported samples total. Allocation is current-thread allocated bytes from ThreadMXBean, not retained heap, device allocation or GC pause duration. The host is macOS ARM64 with Temurin 25.0.4; full environment is in the manifest. Results below are medians across samples, not statistical confidence intervals. No process or sample was dropped.

| Workload | Before µs/op | After µs/op | Time change | Before bytes/op | After bytes/op | Allocation change |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ETag | 24.519 | 19.879 | −18.9% | 13,576 | 776 | −94.3% |
| Date | 1.108 | 0.347 | −68.7% | 2,920 | 384 | −86.8% |
| 304 response + headers | 38.917 | 33.811 | −13.1% | 18,000 | 2,744 | −84.8% |
| 206 response + headers | 126.173 | 119.261 | −5.5% | 18,960 | 3,712 | −80.4% |

Allocation reduction is clear; elapsed costs include file system calls and runtime noise. For example, individual after-range samples span 116.900–198.451 µs, versus 124.141–170.462 µs before. Reported medians must not conceal those overlapping tails. [All samples](evidence/micro.jsonl), [min/max and calculations](evidence/summary.json).

## Host transfer methodology and results

Used the existing [ThroughputBenchmark](../../../tools/bench/ThroughputBenchmark.java) with deterministic 16 MiB bodies, 256 KiB shared/solo turns and PCM batching enabled for both variants. Measured one full-file client, then four full-file clients with a fifth connection sending exact 64 KiB ranges every 10 ms. Persistent connections, client hashing/allocation and loopback overhead are included. Every full body is SHA-256 checked, every range is compared byte-for-byte, and connections, slots and audio reservations must drain to zero.

Four process groups ran before/after/after/before. Each mode has one unreported two-second warmup and three reported two-second periods per group; clients complete their final request after the period. There are six samples per variant per mode, 24 reported samples total. Host benchmarks completed before the controlled phone comparison.

| Metric | Before median | After median | Change |
| --- | ---: | ---: | ---: |
| One file stream | 685.138 MiB/s | 685.595 MiB/s | +0.07% |
| Four streams with seeks | 1,447.468 MiB/s | 1,453.167 MiB/s | +0.39% |
| Per-run seek p95, four-stream workload | 3.272 ms | 2.875 ms | −12.1% |

Bulk throughput is effectively unchanged. Seek p95 is the median of each run's p95, not a pooled percentile. All runs have zero errors and passed content/resource checks. [All samples](evidence/throughput.jsonl), [diagnostics](evidence/throughput.stderr).

## Phone methodology and results

Samsung Galaxy S25 / SM-S931B, serial <device-serial>. USB ADB forwarding maps host port 19000 to the phone's HTTP server on port 9000. This is not a Wi-Fi or physical-renderer benchmark.

The archived phone workload uses track 2122216336: 9,940,667 original FLAC bytes and 29,529,404 converted WAV bytes. It fetches the full original, twelve original 256 KiB ranges, four concurrent originals, a full converted WAV using the LG webOS user agent, and four converted 64 KiB ranges. WAV framing, declared lengths, exact range slices and concurrent hashes are checked. Request timing ends after reading the body and starts before urllib opens the request; the response-start proxy is measured after urllib receives headers, not at the first body byte. Connections are newly opened by urllib for each fetch. The twelve-range p95 implementation selects the maximum.

Three initial baseline runs are archived as exploratory results and excluded from this controlled table. The controlled runner installs in after/before/before/after/before/after order. Every install verifies the APK SHA-256, launches the activity with `am start -W`, and runs one complete unreported warmup workload before one retained workload. A separate one-byte range probe compares ETag, Last-Modified, Content-Range and Content-Type across all APKs. Final installation is the optimized build.

| Metric | Before median, 3 runs | After median, 3 runs | Change |
| --- | ---: | ---: | ---: |
| Original full-file duration | 0.263828 s | 0.261645 s | −0.8% |
| Original response-start proxy | 55.396 ms | 29.409 ms | −46.9% |
| Original seek median | 16.282 ms | 7.804 ms | −52.1% |
| Original seek maximum | 23.624 ms | 14.967 ms | −36.6% |
| Four-original aggregate throughput | 39.4365 MiB/s | 39.4293 MiB/s | −0.02% |
| Converted-WAV duration | 0.855262 s | 0.840616 s | −1.7% |
| Converted response-start proxy | 19.302 ms | 12.212 ms | −36.7% |

Latency is promising but based on three retained runs per APK. After seek medians range from 7.073 to 15.042 ms; before ranges from 14.466 to 17.107 ms. Installation, runtime warmup, cache state, device scheduling, USB and client noise remain possible influences even with interleaving and a warmup. No significance claim or universal latency guarantee is made. Native and converted throughput show no substantial gain.

Measured runs passed **96 exact original/converted ranges, 24 concurrent-original hashes and six one-byte metadata probes**. Original and WAV hashes and all four probed metadata fields were identical across APKs. Six warmups also passed their 96 ranges and 24 concurrent hashes. [Six retained runs](evidence/phone.json), [six warmups](evidence/phone-warmups.json), [three excluded exploratory runs](evidence/phone-exploratory-before.json).

## Regression tests and decision

| Added test | Method and result |
| --- | --- |
| metadataEncoding_preservesLegacyValidatorsAndDates | Compare ETag output with the original SHA-256/String.format algorithm for five file sizes; compare date output with original GMT SimpleDateFormat for 13 timestamps including both bounds, extreme longs, Gregorian cutover, epoch, leap-day and year transitions. All identical. |
| metadataDateFormatter_isSafeAcrossWorkers | Eight workers each repeat all 13 timestamp cases 50 times against independently constructed legacy formatters, sharing one response instance. 5,200 comparisons pass with bounded completion. |

The full **164 core + 43 UPnP = 207 cases** passed, zero failures/errors/skips; debug APK assembly and benchmark classpath export passed. Existing socket tests cover file/range/conditional/HEAD behavior and streaming lifecycle. [Every individual case](TEST_RESULTS.md), [JUnit JSON](evidence/junit-results.json), [build log](evidence/validation.log).

Retain the metadata optimization for its allocation reduction and byte compatibility. Host/phone throughput is unchanged, and improved latency needs more device repetitions before making broad claims. No parser, arbitrary header cache or direct-buffer pool was introduced. Repeated metadata system calls remain a candidate for a separately measured follow-up. Android allocation/GC/CPU/battery, matched Wi-Fi and renderer listening were not measured; no Netty/Grizzly implementation was benchmarked.

## Reproduction

Preserve pre-edit source in `SCRATCH/baseline/FileResponse.java`, then export the host runtime with `./gradlew -I tools/bench/test-classpath.gradle :core:writeStreamingBenchmarkClasspath`. The archived baseline source can be copied into that directory. Use the same current runtime for both isolated variants:

```sh
python3 tools/bench/run-metadata-comparison.py SCRATCH compile
python3 tools/bench/run-metadata-comparison.py SCRATCH micro
python3 tools/bench/run-metadata-comparison.py SCRATCH throughput
```

For the phone, save the two corresponding APKs as `SCRATCH/before.apk` and `SCRATCH/after.apk`, preserve the app's track/database and server settings, and run `python3 tools/bench/run-metadata-phone.py SCRATCH ADB SERIAL`. This installs both builds repeatedly; execute on the intended test device. Keep all raw outputs, verify class/APK identity and include slower samples. [Archival/calculation script](archive-results.py) validates counts, hashes, metadata and test outcomes before producing the evidence and summary.
