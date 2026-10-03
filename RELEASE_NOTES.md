# MusicMate 3.23.0

Streaming now bounds handler work, conversion concurrency and audio buffers, isolates artwork/control traffic, and preserves active audio when new requests exceed capacity. Progress deadlines reclaim stalled streams, and bounded diagnostics record interruptions.

FLAC-to-WAV opening skips unused metadata. File response preparation allocates less, and a non-unique library path index speeds lookup while preserving tracks and listening history through Room migration 2→3.

Targeted retention rules preserve jUPnP members and generic signatures used through reflection, so the optimized APK can start its UPnP services. They also keep the jaudiotagger ID3 frame bodies and WMA chunk readers that earlier minified releases removed or renamed, which could break MP3 ID3 and WMA tag reading. Release builds now keep warning and error logs.

The final APK passed startup, 352 device-local streaming requests with byte/hash checks, and UPnP description and control checks on a Galaxy S25 (Android 16).

The release excludes the lazy FLAC workspace and combined request-parser/copy experiments because their phone results were mixed. Historical candidate measurements are documented in [PERFORMANCE.md](https://github.com/thawee/musicmate/blob/v3.23.0/PERFORMANCE.md); they are not measurements of this retained-only release or proof of higher throughput than Netty.

Version code: 142. See [CHANGELOG.md](https://github.com/thawee/musicmate/blob/v3.23.0/CHANGELOG.md) for details.
