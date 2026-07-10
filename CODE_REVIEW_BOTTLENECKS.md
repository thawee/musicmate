# Bottlenecks and Performance Issues

1. Database queries using `getDao().queryBuilder()` with `LIKE` without proper indexes (`TrackEntity.java`).
2. Iterating through `getAllMusics()` for stats aggregation instead of direct SQL counting/aggregation (`BaseServer.java`).
3. OutOfMemory errors in `AudioFileParser.java` due to heavy allocations while generating waveforms on large files (now bounded by `LruCache`).
4. High CPU usage in extracting CoverArts via FFmpeg (now made lazy loading on-demand).
5. Potential race condition for UI websocket handling on multithreaded servers (`NioHttpServer`, `Netty`, `HttpCore`).

## Fixes Implemented:
- **Pagination & Search Limits**: Limit search results to 100 via base HTTP handler.
- **DB Indexing**: Added `album` to the indexed DB fields.
- **Search Prefix Optimization**: Modified `findByKeyword` to use prefix search on normalized strings instead of `%keyword%`.
- **Database Aggregation**: Replaced in-memory `getAllMusics` scanning with native SQLite query (`getSearchStats`).
- **Waveform LruCache**: Swapped custom strong-reference map for `LruCache<String, byte[]>` with a 256 size limit.
- **WebSocket Thread Safety**: Scoped local copies of ws handler references.
- **Auto-Tagging Support**: Implemented MusicBrainz and AcoustID fingerprinting as an additional UI feature without degrading core read performance.
