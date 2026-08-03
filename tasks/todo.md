# Tasks: Copy Track Metadata to Converted Files in FileOperationTask

- [x] **Step 1**: Update `encodeFiles` in `FileOperationTask.java` to copy track metadata (Title, Artist, Album, AlbumArtist, Genre, Track, Year, etc.) from source track to newly converted target track and save to file/DB.
- [x] **Step 2**: Compile and verify build with `./gradlew :app:compileRoomDebugSources`.
- [x] **Step 3**: Document review results in `tasks/todo.md`.

## Review & Verification Results
- **Root Cause Identified:** When FFmpeg converts/downsamples a file, FFmpeg produces a raw audio file without copying all metadata tags (Artist, Album, Genre, Year). When `scanMusicFile` indexed the new file, `Artist` and `Album` were empty in the database, causing the new track to be filtered out when viewing by Artist/Album or Library criteria.
- **Fix Applied:** Updated `encodeFiles()` in `FileOperationTask.java` to explicitly copy `Title`, `Artist`, `Album`, `AlbumArtist`, `Genre`, `Track`, `Year`, `Comment`, `Composer`, `Publisher` from the original track, write them into the physical file header (`TagWriter.writeTagToFile`), and save to `tagRepos`.
- **Verification:** Built and verified via `./gradlew :app:compileRoomDebugSources` (**BUILD SUCCESSFUL**).
