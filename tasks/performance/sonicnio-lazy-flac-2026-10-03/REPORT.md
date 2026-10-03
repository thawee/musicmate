# Lazy FLAC decoder scratch allocation

Physical-phone follow-up is complete: [full Android methodology/results](PHONE_RESULTS.md). ART metadata allocation falls 99.1%, but app PSS and per-run seek maxima are higher; **hold from release pending profiling**. The host-only recommendation below is superseded by those results.

## Change and scope

Metadata-only FLAC callers previously allocated a FrameDecoder when the last metadata block was consumed. Its two long[65536] workspaces contain 1 MiB of sample storage. FlacToWav.open reads metadata and immediately closes that decoder; the streaming body subsequently opens its own decoder. HEAD and header-only WAV requests also performed the discarded allocation.

FrameDecoder is now created on the first audio read or seek, then reused. Metadata completion is checked using metadataEndPos, independently of frameDec. Both audio entry points still reject calls before metadata completion and after close. Closing a metadata-only decoder does not create a workspace. No pooling, metadata skipping or streaming callback changes were included.

The actual audio decoder still allocates the same workspaces, now at first read/seek. This removes the metadata probe's discarded allocation, not the active decoder's required memory, and moves allocation timing within body startup. Concurrency/thread-safety guarantees remain unchanged.

## Isolated host measurement

Host: macOS 26.6.2 arm64, Temurin OpenJDK 25.0.4+7. Baseline decoder source is preserved from HEAD `0d28ca052df66e23032d2c0afc0e3e6ba4e979bf`. Before/after FlacDecoder classes were compiled separately against the same exported core unit-test runtime and placed first on the classpath. The final harness asserts the loaded decoder's code-source directory in every process.

The fixture is a 42-byte FLAC magic + final STREAMINFO block: stereo, 24-bit, 96 kHz, declared 96,000 samples. It intentionally contains no audio frames: this is a metadata-only allocation test, not a complete playback fixture. Each operation opens a decoder, consumes metadata, validates sample count/depth and closes it. Fixture creation is outside measurement. Allocation uses ThreadMXBean current-thread counters; time includes file open/read/close. It excludes native allocations and other threads.

Six fresh JVMs run in before/after/after/before/before/after order, each with fixed `-Xms128m -Xmx128m`, 64 warmup operations and three measured samples of 128 operations. Nine samples per variant represent three independent processes, not nine independent JVMs. The table uses sample medians. Warmups and measured operations total 2,688 probes per comparison series.

| Final comparison | Before | After | Change |
| --- | ---: | ---: | ---: |
| Allocated bytes/probe | 1,057,392 | 8,752 | −1,048,640 bytes; **−99.2%** |
| Metadata probe time | 200.575 µs | 128.376 µs | −36.0%; host fixture only |

The allocation difference includes array/object overhead beyond the 1,048,576 bytes of long elements. An initial series before adding explicit class-origin assertions produced the same allocation medians, with timing 171.017 → 130.142 µs. Both series remain archived; variable timing reinforces that allocation reduction is the stronger result. This host stage did not measure Android GC/latency or full converted-stream throughput; those are addressed with their limits in [the phone follow-up](PHONE_RESULTS.md). Netty comparison and real renderer playback remain unmeasured.

## Correctness and runtime verification

Command: `./gradlew :core:testDebugUnitTest :server-jupnp:testDebugUnitTest :core:writeStreamingBenchmarkClasspath -I tools/bench/test-classpath.gradle --console=plain`.

222 cases passed: 179 core and 43 UPnP, zero failures/errors/skips. The six FlacToWav tests include:

| Test | Verified behavior |
| --- | --- |
| metadataOnly_doesNotAllocateAudioWorkspace_andAudioReadsReuseIt | No frame decoder through metadata completion; first read creates it; subsequent read and seek reuse it; decoded samples equal the encoded source. |
| audioReadAndSeek_requireCompletedMetadata_andRejectClosedDecoder | Both entry points reject incomplete metadata and closed metadata-only decoders. |
| seekAsFirstAudioOperation_initializesWorkspace_andMatchesSamples | First operation can be a mid-frame seek with nonzero destination offset; both channels match source samples. |
| wholeFile_24bit_isAWavWhoseDataMatchesTheFlacMd5 | WAV format fields, native sample rate/depth and PCM MD5 match. |
| wholeFile_16bit | Full PCM MD5 matches. |
| ranges_matchTheSameBytesOfTheWholeFile | Mid-frame, header-crossing, end-of-file and later seeks match whole-file slices byte for byte. |

SonicNIO socket, cancellation, admission and framing regression suites ran with the same core/UPnP invocation. This host validation preceded the separate APK/phone comparison documented in the follow-up. An initial sandbox Gradle invocation could not access the wrapper cache; it was rerun with the permitted Gradle escalation and succeeded. Diff review used HEAD for the isolated change; comparison to main confirmed these decoder/test files are absent there, so main is not a useful before/after source baseline.

## Reproduction and evidence

Export the classpath with the Gradle command above, then run:

```sh
python3 tools/bench/run-flac-metadata-comparison.py /private/tmp/musicmate-lazy-flac-reproduction
```

The script obtains its baseline from HEAD. If this change is later committed, provide the archived baseline source or adjust the baseline revision before reproducing an actual before/after comparison.

- [Final individual samples](SAMPLES.md), [raw final results](results.jsonl), [summary](summary.json).
- [Initial raw results](initial-results.jsonl), [initial summary](initial-summary.json).
- [Preserved baseline](baseline/FlacDecoder.java), [tested after source](after/FlacDecoder.java).
- [Benchmark harness](FlacMetadataBenchmark.java), [runner](run-flac-metadata-comparison.py), [test source](FlacToWavTest.java).
- [Gradle log](gradle-tests.log), XML results under `tests/core` and `tests/server-jupnp`, [SHA-256 manifest](manifest.json).

Host-only recommendation was to retain the isolated allocation reduction pending device profiling. The completed [phone follow-up](PHONE_RESULTS.md) now recommends holding it from release due to adverse app PSS and per-run seek maxima. Earlier parser/copy release concerns remain separate and unresolved. No throughput superiority is claimed.
