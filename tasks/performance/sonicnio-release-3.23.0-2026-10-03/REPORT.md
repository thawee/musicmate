# Retained-only release 3.23.0

## Scope and interpretation

Version code 142 retains bounded streaming/admission, traffic isolation, progress deadlines, diagnostics, PCM batching, file metadata/ETag preparation, Room path indexing and streaming FLAC metadata skipping. It excludes lazy FLAC workspace creation and the combined parser/copy experiment. Their original sources and measured results remain archived as historical experiments; they are not active release code.

The metadata-skip comparison used candidate APKs containing both held experiments. Its 38.7% idle-seek reduction cannot be attributed to this retained-only combination. This release check verifies correctness and records descriptive latency, without a matched pre-release comparison or a Netty throughput claim.

## Method

Run core, UPnP and Room JVM suites, build the normal R8-optimized release, then sign a scratch copy with the installed phone's test certificate. Do not uninstall the app or downgrade its Room database. Check fresh/indexed database creation and migration chains in the separate Android test package.

On the SM-S931B, Android 16/API 36, run the existing opt-in `PathIndexPhoneBenchmark` in idle-only mode. It verifies a full converted WAV against SHA-256 `edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676`, warms 64 HEAD and 16 seek requests, then measures two 176-request phases. Each phase verifies status, WAV header and range bytes. These device-local requests do not measure Wi-Fi capacity or simultaneous-stream throughput. The benchmark also performs lookup checks on isolated copies of its database fixture.

## Initial checks and failure investigation

The retained-only debug build passed 219 JVM cases and 352 measured streaming requests. Three Android Room migration cases passed. An initial release-install attempt used a different test certificate and was rejected without replacing the app; its apparent streaming pass belonged to the debug APK and is identified as such.

The correctly signed optimized APK failed its reference fetch, first with a truncated body and then with EOF before headers. Both failures coincided with orderly listener shutdown. A diagnostic R8 build retaining Android logs exposed `AnnotationLocalServiceBinder` rejecting `ProtocolInfos` because its public single-String constructor was removed. Mapping confirmed the missing constructor. The diagnostic build is evidence only and is excluded from publication.

After retaining the constructors and named output getters, the normal optimized APK still failed the reference fetch. A second diagnostic build exposed a `ClassCastException` in `AbstractDatatype`: reflective generic-superclass lookup returned a raw Class instead of a ParameterizedType. This requires preserving the datatype generic signatures as well as the reflected members. Both failed attempts remain recorded; a successful build alone is not runtime proof.

The next optimized run reached service construction but failed CSV datatype inference (`No built-in UPnP datatype for Java type of CSV: null`). Retained release error logs identified `CSVString` construction in ContentDirectory, requiring its generic hierarchy to be preserved as well. Warning/error logs are now retained in the normal release configuration; verbose/debug/info stripping remains enabled.

## CSV-signature APK checks

The rebuilt APK (r8-map-id `b763946b…`) was checked with `dexdump -a`. Class-level Signature annotations are present for `CSV` (`<T:Object>ArrayList<T>`), `CSVString` (`CSV<String>`), `CSVUnsignedIntegerFourBytes` and `AbstractDatatype`, and `ProtocolInfos.<init>(String)` is in the mapping. A copy signed with the phone's existing test certificate was installed with `install -r` and started as a fresh process. `MediaServerHub` started; the app owned UDP 1900 and TCP 49152/9000. The benchmark passed 352/352 requests with the full-WAV SHA-256 assertion, and the description, all three SCPDs and nine SOAP actions returned 200. The first `IsAuthorized` call returned UPnP error 401 because the request used the wrong service URN (`urn:microsoft.com`); the corrected `urn:schemas-microsoft-com` request returned 200. Evidence: [evidence/csv-fixed](evidence/csv-fixed/).

Its retained error logs then exposed an older defect that 3.22.0 shipped with the same configuration. jaudiotagger creates ASF chunk readers with `Class.newInstance()` (33 "cannot be instantiated" errors once tag reading initialized), and it resolves ID3 frame bodies with `Class.forName("org.jaudiotagger.tag.id3.framebody.FrameBody" + id)`. Only 26 of 108 frame-body classes survived R8, all renamed, so optimized builds could not create most ID3 frame bodies by name. Every tag read, including MP3, goes through jaudiotagger. The phone library contains 34 MP3s and no WMA files.

## Final APK verification

`library/jaudiotagger-android/consumer-rules.pro` now keeps the frame-body names and constructors, ID3/datatype copy constructors used by `ID3Tags.copyObject()` and ASF chunk-reader no-arg constructors. In the final APK (r8-map-id `c7840a79…`, 48 KiB larger), all 108 `FrameBody*` classes keep their original names with `()`, `(ByteBuffer, int)` and copy constructors, the ASF readers keep `<init>()`, and the CSV/datatype signatures remain. The 219 core, UPnP and Room JVM cases pass with zero failures/errors.

| Check | Result |
|---|---|
| Unsigned APK SHA-256 | `a78b79e1c28c45cf169039008cef3c3514e691bd102636908d5e0db11bf4e756` |
| Test-signed APK SHA-256 (installed copy matches) | `204049ad15bc6bbb82e0f596fbe7c6cd6b3ece98ef5d47b51ef4c9efd9ba0f96` |
| Installed version | 3.23.0-261003, code 142; `firstInstallTime` unchanged (2026-09-29) |
| Startup | Fresh process; zero reflection/startup failures, including during the benchmark that previously produced the ASF errors |
| Streaming | 352/352 requests 200/206, WAV headers/ranges and full-WAV SHA-256 verified |
| UPnP | Description (Server `MusicMate/3.23.0-261003`), 3 SCPDs, 9 SOAP actions all 200 |
| Library | Browse reports 8,289 songs, 2,544 artists, 70 genres, 10 playlists |

Descriptive latency only. Measured shortly after launch, time-to-headers medians were 8.2/10.1/10.2 ms (HEAD/header/seek) and seek completion 29.4 ms median. On the CSV-fixed APK, a run with the startup analyser quiet measured 4.0/3.9/3.9 ms time-to-headers and 14.1 ms seek completion. A run during startup DSF analysis measured 10.2 ms seek time-to-headers. These figures depend on background work and are not a release comparison.

## Limits

- Database preservation is indirect: `install -r`, unchanged `firstInstallTime` and 8,289 browsable songs. `run-as` is unavailable on the release build, so schema version 3 and the 704 history rows were not read directly.
- ID3 correctness is shown statically (names/constructors in DEX). No MP3 tag read was triggered on the phone, because reads only occur during a library scan or in the tag editor. The ASF fix is shown at runtime.
- The device was reached over USB/device-local HTTP; Wi-Fi throughput and physical-renderer playback were not measured.
- Raw logcat files are not archived; [startup summaries](evidence/final/startup-log-summary.txt) list error/warning tags. Device IPs and the serial are redacted.
