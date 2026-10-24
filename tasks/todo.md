# Google Play launch plan (2026-10-15, from the product strategy canvas)

Goal: MusicMate on Google Play as freemium within about 12 weeks. First segment: streamer owners with no server (the phone serves the library to a DLNA renderer). Hook: the fake hi-res and DR check.
North Star: weekly listening hours from local libraries played to an external output (DLNA or USB DAC). OMTM during the closed test: activation, meaning scan finished plus a first play to an external output within 24 hours.

Facts found in code (2026-10-15):
- `minSdkVersion = "36"` in `gradle/libs.versions.toml:4`; every module except `cardashboard` (34) and `vivohifi` reads it. Bit-perfect USB uses `AudioMixerAttributes` (API 34) in `app/.../audio/UsbOutputFormat.kt`.
- All-files access: `MANAGE_EXTERNAL_STORAGE` in the manifest, checked in `PermissionUtils.java`, requested in `PermissionActivity.kt`. Writes go through `java.io.File` paths: `core/.../provider/FileSystem.java`, `FileRepository.java`, `TagReader.java`, `ArtworkDraft.kt`, `CoverThumbnails.java`, `MusicBrainzClient.java`. No `createWriteRequest`, no `ACTION_OPEN_DOCUMENT_TREE`.
- Permissions with no code use: `KILL_BACKGROUND_PROCESSES`, `MEDIA_CONTENT_CONTROL` (system-only). `requestLegacyExternalStorage` does nothing on API 30+.
- `ACCESS_FINE_LOCATION` exists only to read the Wi-Fi SSID (`core/.../utils/ApplicationUtils.java:507`).
- `MediaNotificationListener` watches other apps' media sessions (needs notification access, a sensitive grant).
- `MediaServerHubService` runs as foreground type `dataSync` (manifest line 119). Android 15+ limits `dataSync` to 6 hours per 24 and calls `Service.onTimeout`; no `onTimeout` exists in the code. An overnight server session may already be cut off. Verify on the phone.
- `core` depends on `com.antonkarpenko:ffmpeg-kit-full:2.2.1` (`FFMpegHelper.java`); its license (LGPL or GPL build) is unverified. `jaudiotagger-android` is LGPL.
- `library/crashreporter` makes no network calls, so the Data safety form can say no data collected if nothing else sends data.
- Only one author in git history, so future versions can be relicensed. Releases already published stay Apache 2.0.

Facts from the web (2026-10-15):
- Android developer verification went live on 2026-09-30 in Brazil, Indonesia, Singapore and Thailand: certified phones there refuse APKs from unregistered developers, including sideloaded ones (adb and an "advanced flow" still work). It goes global in 2027. This affects the GitHub APK today, not only Play.
- Personal Play accounts created after 2023-11-13 need a closed test with 12+ testers opted in for 14 continuous days; the clock resets if the count drops below 12. Organization accounts are exempt.

## Phase 0: decisions (week 1, needs the user)
- [ ] D0 signing key and package name. Cannot be undone. Enroll in Play App Signing by uploading the existing release key (not a Google-generated one), and keep `applicationId` the same for both flavors. That is the only setup where a GitHub user can move to the Play build in place, without uninstalling and losing the library.
- [ ] D0b developer verification (urgent, independent of Play): register the developer identity, the package name and the release key so the GitHub APK keeps installing on certified phones in Thailand. Do this first.
- [x] D1 minSdk floor: 34 (Android 14), the bit-perfect API floor. No non-bit-perfect USB fallback needed. (user, 2026-10-15)
- [x] D2 business model: free app plus one-time Pro unlock (5 to 10 USD). (user, 2026-10-15) Pro: DR and fake hi-res reports, batch tag editing, MusicBrainz lookup, organizer. Free: player, DLNA server, quality labels.
- [x] D3 license: stay fully open (Apache 2.0). Pro is sold on Play for convenience and support; anyone can rebuild it. (user, 2026-10-15)
- [x] D4 distribution (user, 2026-10-15): two flavors. `play` uses scoped storage and Play Billing. `github` keeps all-files access, is free, and keeps today's update path (same signing key).
- [ ] D5 developer account. Personal: the 12-tester, 14-day closed test is required (see facts). Organization: exempt, but needs a D-U-N-S number and a registered business.

## Phase 1: demand checks (weeks 1 to 4, runs alongside Phase 2)
- [ ] H1, phone as server: post a "phone as your music server" walkthrough on WiiM and Eversolo forums, Head-Fi, r/audiophile and r/WiiM, linked to the GitHub release. Record GitHub release download counts before and after each post, and count replies saying "I want this" versus "I use a NAS".
- [ ] H2, willingness to pay: add a "Support / Pro pre-order" link (Ko-fi or Gumroad) to the About screen and README. Pass: any paid pre-orders at the D2 price. Payment links for digital features break Play's payments policy, so keep the link in the `github` flavor only and confirm it is absent from the `play` build before the first closed-track upload.
- [ ] H4, shareable hook: design a one-image album report (DR, real or upsampled verdict). Post 3 examples. Pass: people ask how to check their own albums.
- [ ] Recruit 15 to 20 closed-test testers from the same threads (spare above the 12 required). Record each tester's phone, Android version and renderer model.
- [ ] Go or no-go at week 4: if H1 and H2 both show nothing, stop Play work and keep GitHub only.

## Phase 2: Play compliance engineering (weeks 2 to 8)
### 2.1 minSdk (needs D1)
- [x] Set `minSdkVersion` to the D1 value. Run `./gradlew lint` and fix every `NewApi` error with a version check or fallback. Branch `play-minsdk-34`: `minSdkVersion = "34"`; `lintDebug` across all modules reports zero `NewApi`/`InlinedApi` issues (its existing failures are MissingPermission x3 in server-jupnp, MissingSuperCall in `MusicMateServiceImpl.java:627`, SuspiciousIndentation x4, unrelated). Debug APK reports `minSdkVersion:'34'`. README and CONTRIBUTING updated.
- [x] Verify: `:app:testDebugUnitTest`, `:app:assembleDebug`, and a scan, play and DLNA stream on an emulator at the floor API and at API 36. `testDebugUnitTest` (all modules, 395 tests, 0 failures); debug APK. API 34 (new AVD `MM_API34`, google_apis arm64): fresh install, launch, scan of 4 generated files (MP3, 16/44.1, 24/48, 24/96 WAV) with formats and DR shown, phone-speaker playback (session PLAYING), server start, web UI 200, DLNA description and Browse (6 root containers, 4 songs), streams byte-identical to the source files (SHA-256 match for MP3 and 24/96), HTTP range 206. API 36 (`Medium_Phone`): installed as an update with the 22-track library kept, playback, server start, web UI 200, Browse 22 songs, FLAC stream 200. No crash-buffer entries on either. Not covered: release (R8) build, USB DAC, Bluetooth.

### 2.2 Storage without all-files access (needs D4)
- [ ] Spike, 2 to 3 days, on a branch. Compare (a) `MediaStore.createWriteRequest` for batch tag edits plus writing through the granted file, and (b) one `ACTION_OPEN_DOCUMENT_TREE` grant on the music folder with writes through a file descriptor. Test in each case:
  - jaudiotagger's write (temp file, then rename) on FLAC, MP3, M4A/ALAC and DSD (`.dsf`, `.dff`). MediaStore may not index DSD as audio.
  - Sidecar files in album folders (`folder.jpg`, `.cue`). Reading images through MediaStore needs `READ_MEDIA_IMAGES`, which Play restricts.
  - The organizer's move/rename across folders.
  - Prompts the user sees per batch of 50 edits.
  Decision rule: if DSD or sidecars fail under (a), choose (b).
  Spike findings so far (code, 2026-10-15):
  - Tag writes go through `JThinkWriter.writeTag` -> jaudiotagger `audioFile.commit()`. FLAC, DSF, M4A, AIFF use `AudioFileWriter2`, which edits the original in place through a `File` channel. MP3 (`MP3File.commit`) and the older `AudioFileWriter` path create a `.tmp` file in the album folder and rename it over the original.
  - Scanning walks folders with `File.listFiles()` (`FileRepository.java:371`); covers are found by listing the album folder for image names (`FileRepository.java:338`). Under scoped storage `listFiles` returns only files MediaStore lets the app see, so DSD visibility decides whether DSD is scanned at all.
  - `TagReader.SupportedFileFormat`: MP3, FLAC, M4A, WAV, AIF, AIFF, DSF. DFF is commented out, so DFF is out of scope for writing.
  Spike steps (branch `spike-scoped-storage`, never merged). Order: T0 to T3 first; they may settle most of it.
  - [x] T0 setup on API 34 (`MM_API34`) and 36 (`Medium_Phone`): `MANAGE_EXTERNAL_STORAGE: deny` recorded by `appops get` on both, `isExternalStorageManager=false` inside the app, READ_MEDIA_AUDIO granted. Fixtures in `Music/MMSpike/Spike Album/`, pushed by adb: a real 73 MB FLAC and 40 MB M4A from the demo library, an MP3, a generated DSD64 DSF, `folder.jpg`, `album.cue`. Probes run inside the app process (instrumented tests `ScopedStorageProbeTest`, `TreeGrantProbeTest`); `run-as` does not get the app's storage view, so it is not a valid stand-in.
  - [x] T1 MediaStore (both APIs): FLAC, MP3, M4A indexed as audio (media_type 2). **DSF is `application/octet-stream`, media_type 0 (not audio).** `.cue` is non-media; `folder.jpg` is an image.
  - [x] T2 app by path with READ_MEDIA_AUDIO only (both APIs): `listFiles()` returns only FLAC, MP3, M4A. **DSF, `folder.jpg` and `.cue` are unlisted and unreadable (EACCES).**
  - [x] T3 writes by path without a grant (both APIs): "rw" open of every audio file fails (EACCES); `.tmp` and new `.dsf` creation fail ("Operation not permitted"); a new `.flac` can be created.
  - [x] Candidate design, revised: READ_MEDIA_AUDIO plus paths cannot work, because DSD files could not even be scanned or played. The tree grant has to carry reads too.
  - [x] T4 skipped on purpose: `createWriteRequest` takes MediaStore URIs, DSF has no audio row, and jaudiotagger's `.tmp` sibling cannot be created in `Music/`, so option (a) cannot cover DSD tag writes or MP3 tag growth whatever the dialog count.
  - [x] T5 tree grant on `Music/` (both APIs): one picker plus one "Allow" dialog. All 6 fixtures listed (DSF as octet-stream) and readable. Rename and `moveDocument` to another folder work. Path access stays blocked after the grant. The picker refuses the internal storage root and the `Download/` root ("Can't use this folder"); subfolders of Download are allowed. Secondary volume: **untested**, neither emulator has one (`sm list-volumes`: emulated only). Listing at scale (API 36): 201 folders, 2,000 files in 1.68 s through the tree versus 0.71 s by path (about 8.4 versus 3.5 ms per folder), so about 7 s versus 3 s for an 8,100-track library of about 800 folders.
  - [x] T6 tag writes through the tree (both APIs):
    - (i) in place through `/proc/self/fd/N` path: fails for FLAC, DSF, M4A. Reopening that path is denied by the system (`RandomAccessFile` and `FileChannel.open` give EACCES); jaudiotagger also cannot *read* that way (DSF read gave `NoReadPermissionsException`). A read-only descriptor channel (`FileInputStream(pfd.fileDescriptor).channel`) does positional reads at offset 0 and at size-128 on the shell-owned 73 MB FLAC and DSF (API 34). In-place *writes* were not tested: a Java stream channel is one-way, so they would need a custom channel on `Os.pread`/`Os.pwrite` plus jaudiotagger accepting a channel.
    - (ii) copy to cache, commit, write back: works for FLAC, DSF, M4A; written file is byte-identical to the cached commit and the new title reads back. 73 MB FLAC: 103 to 129 ms in, 2 to 3 ms commit, 78 to 169 ms back (emulator on a Mac SSD; a phone will be slower).
    - **Write-back with "wt" is not crash-safe**: `am force-stop` during a slow write-back left the 73 MB FLAC truncated to 22 MB. Safe replace works instead: write a `name.mmtmp` sibling, delete the original, rename the temp to the original name (tested on both APIs). A crash then leaves at worst a complete `.mmtmp` to recover at the next start.
  - [x] Result and rework size:
    - Other-owner files (pushed by adb, owner `com.android.shell`, API 34): "rw" open, safe replace of a DSF, rename and `moveDocument` of a FLAC all work through the tree. After a safe replace the MediaStore owner becomes `com.android.externalstorage`.
    - WAV (`audio/x-wav`) and AIFF (`audio/x-aiff`) are indexed as audio (API 34). DSF is the only supported format MediaStore misses (DFF is not supported by the app).
    - Streaming through a descriptor (API 34): `transferTo` of the 73 MB FLAC from a read-only tree descriptor took 162 ms cold and 21 ms warm, against 71 and 24 ms by path.
    - Prerequisite either way: the readers that touch DSF (jaudiotagger DSF reader, justdsd decoder, `MusicAnalyser` DR pass) must read from a channel, because copying files to cache for every scan read is not viable. With a tree-only design, the same applies to every reader and decoder.
    - Decision for the user, two designs for the `play` flavor:
      - Tree only: one persistable `ACTION_OPEN_DOCUMENT_TREE` grant per music root carries everything. One access mode. Every reader, decoder, the scan and the server move to channels from descriptors; listing is about 2.4x slower.
      - Split by format: READ_MEDIA_AUDIO plus paths for FLAC, MP3, M4A, WAV, AIFF (scan, playback, streaming unchanged), the tree grant for DSF, covers, `.cue` and all writes. Smaller change to the tuned server and scan; two access modes to keep tested.
      - Both need the tree grant, the `MusicFileAccess` interface, and tag writes by copy to cache, commit, safe replace.
    - The scan dialog's default `Download` entry cannot be granted as a root. The `play` flavor must ask for a folder (suggest `Music`).
    - Track identity can stay path-shaped: tree document IDs map 1:1 to paths (`primary:Music/a/b.flac` is `/storage/emulated/0/Music/a/b.flac`).
    - Rework: about 35 source files open music by path (`new File`, `RandomAccessFile`, `FileInputStream`, `FileChannel.open`, `Paths.get`, `listFiles`). The largest are `FileRepository.java` (51 sites), `FileSystem.java` (22), `ApplicationUtils.java` (11), `TagsActivity.java` (9). Also hit: the scan worker, `MusicAnalyser`, `AudioDecoder`, the justdsd and JustFLAC decoders, `CoverThumbnails`, the NIO server's `FileResponse` and `NioWebServerImpl`, `AndroidPlayerController`, `FFMpegHelper`. Plan: one `MusicFileAccess` interface (list, open read channel, open for write via cache plus safe replace, rename, move, delete) with a path implementation for `github` and a tree implementation for `play`, then move call sites behind it module by module. This is the largest item in the Play plan; estimate it before scheduling.
    - Untested: SD card volume; a real phone (UFS speeds, OEM DocumentsUI); MediaStore picking up renamed files promptly; ffmpeg-kit reading through the `saf:` protocol; the NIO server end to end on descriptors; the API 34-only probes above on API 36.
- [x] D6 storage design (user, 2026-10-15): split by format. `play` flavor: READ_MEDIA_AUDIO plus file paths for FLAC, MP3, M4A, WAV, AIFF (scan, playback, server unchanged); one persistable folder grant per music root for DSF, covers, `.cue` and every write. `github` flavor keeps all-files access and paths for everything.
- [ ] S1 `MusicFileAccess` interface in core (list folder, open read channel, write via cache plus safe replace, rename, move, delete) with the path implementation only. Move the write sites (`JThinkWriter`, `ReplayGainManager`, `FileSystem` move/rename/delete, `ArtworkDraft`, `CoverThumbnails`, `MusicBrainzClient`) behind it. No behavior change; unit tests plus a phone check.
  S1 steps (branch `play-storage`, off `play-minsdk-34`):
  - [x] `MusicFileAccess` interface in `core/.../provider`: `edit(target, editor)` (editor gets a file it may change in place), `writeAtomically(target, writer)`, `copy(source, target)`, `move(source, target, replaceExisting)`, `delete(file)`, `mkdirs(dir)`. `PathMusicFileAccess` keeps today's behavior. `FileSystem.access()` / `FileSystem.setAccess()` hold the installed one; `FileSystem`'s static methods delegate their primitive operations to it, keeping their signatures and semantics (`move` no replace, `rename` replaces like `renameTo`).
  - [x] Route library writes that bypass `FileSystem`: `JThinkWriter.writeTag` and `ReplayGainManager` commit (edit), `ArtworkDraft.commitTo` and `MusicBrainzClient.downloadCoverArt` (writeAtomically), `FFMpegHelper.extractCoverArt` final move, `FileRepository.deleteMediaItem` (`FileUtils.delete`), playlist export dir in `ScanAudioFileWorker` and `PlaylistRepository`.
  - [x] Note for S2, not changed now: FFmpeg writes its output straight into the album folder in `removeCoverArt` and `convert`; in tree mode that output must go to app cache first. (`extractCoverArt` now writes to cache and goes through `writeAtomically`.) Reads by path (`isMediaFileExist`, `AudioFileIO.read` of the target, FFmpeg `-i`) are untouched; S3 handles DSF reads.
  - [x] JVM tests for `PathMusicFileAccess` (edit, atomic write keeps the original when the writer throws, copy, move with and without replace, delete, mkdirs) and for `FileSystem` delegating to an installed access.
  - [x] Verify: core and app unit tests, `assembleDebug`, emulator check of a tag edit, a cover change and an organize move. All modules 405 tests, 0 failures (10 new in `PathMusicFileAccessTest`; `ArtworkDraftTest.failedSaveKeepsDraftForRetry` caught that `writeAtomically` must not create a missing album folder, now fixed). API 34 emulator with all-files access, debug build: title edit saved through `edit` (log `JThinkWriter: writeTag`, file hash changed, size unchanged, `TITLE=Adagio Cantabile s1` in the file, no temp file left); Extract Cover Art to Folder wrote a JPEG `Cover.jpg` through `writeAtomically`; Organize moved the FLAC to `Music/Kasem Quartet/Strings At Dawn (HI-RES)/02 - Adagio Cantabile S1.flac` with the same hash and copied `Cover.jpg` along; no crash-buffer entries. Not covered on the device: ReplayGain write, playlist export, MusicBrainz cover download, delete.
  S1 small behavior changes (none look harmful): ReplayGain writing no longer skips files that fail `canWrite()` (it fails in the commit instead); playlist cleanup deletes the files in `AA_Playlists`, not the folder recursively; `copy` creates the target folder; `rename` can move across filesystems (`Files.move` copies where `renameTo` returned false); `copy` stops with an error if the source shrinks mid-copy. Playlist export checked on API 34: a planted stale `Old.m3u` was removed on the next scan, fresh files start with `#EXTM3U`, no temp files left. Delete was not checked on the device (unit tests only).
  Notes for S2:
  - To classify (library or app-private) before tree mode: `ApplicationUtils` writes around lines 142-206 and 461-475, `FileSystem.copy(InputStream, File)`, `FileRepository.copyAssetCover`, `MusicFileProvider.delete`, `TagsActivity` file sites.
  - `ReplayGainManager.writeReplayGainToFile` has no callers today, so no automatic per-track rewrite to design for.
  - `FileSystem` still checks `exists()`, `length()` (`isValidSize`) and `lastModified()` by path. T2 showed `exists()` works on DSF without access; probe `length()` and `lastModified()` on DSF.
  - `FileSystem.java` uses CRLF line endings; keep them (compare CRLF counts before each commit).
- [ ] S2 tree implementation (core only; the folder-grant UI moves to S5 because every screen is gated on all-files access until the `play` permission flow exists):
  - [x] `StorageTreeRoots`: persisted tree grants mapped to absolute paths through `StorageManager` volumes (`primary` and SD UUIDs); path to document ID and back. Pure mapping logic covered by JVM tests.
  - [x] `AndroidMusicFileAccess` (installed at app start in `MusixMateApp`, recovery run on the executor): per call, use paths when all-files access is held or the file is in app storage; use the tree when the file is under a granted root; otherwise paths (fails as today).
  - [x] Tree operations: `edit` = copy to cache, run editor, safe replace; `writeAtomically` = write a `name.mmtmp` sibling, delete original, rename; `copy`; `move` (rename in place, `moveDocument` across folders, copy plus delete across volumes; respect `replaceExisting`); `delete`; `mkdirs` (create each missing level). Check every created or renamed document has exactly the requested name (the provider adds " (1)" on clashes).
  - [x] Replace journal in app storage: record target and temp before a safe replace, clear after. At start: temp and target both present means delete temp; temp present and target missing means rename temp to target.
  - [x] Instrumented tests on API 34 and 36 with a `Music/` grant obtained through DocumentsUI by UiAutomator, all-files access denied: edit FLAC and DSF tags, cover write, copy, rename, move across folders, delete, mkdirs, name clash handling, and crash recovery (kill during a replace, then run recovery). `AndroidMusicFileAccessTest`: 8 of 8 pass on API 34 and on API 36; `@BeforeClass` releases any grant and grants `Music/` again through the new `FolderGrantActivity` (the production picker component S5 will use). Covers: copy in and back out, DSF unreadable by path, title edits on FLAC, DSF and MP3 with no temp left and the journal cleared, a failed edit keeps the original, atomic cover writes (a failing writer keeps the original, a missing folder is not recreated), rename, move across folders, move without and with replace, delete, both recovery cases. Real crash (`InterruptedReplaceProbe`, run with `-e interruptProbe true`, API 36): force-stop mid-write left the original plus a 96 KB partial temp and a journal entry; the next app start logged `DELETE_TEMP`, removed the temp, kept the original, and cleared the journal. Not covered: an SD card volume (no emulator volume), the crash window between deleting the original and the rename (simulated only), and the name-clash path of `createExact`.

  S2 follow-up (2026-10-16): the D6 premise holds. Shell-owned FLAC and MP3 (pushed by adb) edited through the grant, and an MP3 moved into a new folder with a new name, stay readable by path immediately, and MediaStore keeps them as audio (`audio/flac`, `audio/mpeg`, media_type 2; owner becomes `com.android.externalstorage`). Checked on API 34 and 36 with `ShellOwnedAudioProbe` (`-e shellProbe true`); the suite now asserts it too (`editedAndMovedAudioStaysReadableByPath`, 9 of 9 on both APIs). The first run failed because READ_MEDIA_AUDIO was not granted on the reinstalled emulator; the suite now checks both preconditions. Observation: before the edit, the app's `MediaStore.Audio` query by `DATA` returned no row for the shell-owned files although they were readable by path; scanning uses `listFiles`, so this does not affect the design.
  Review fixes: each replace uses a unique temp name (`name.<nanotime>.mmtmp`), so concurrent replaces of one file cannot delete each other's temp; a move across folders now renames in the source folder first and renames back if `moveDocument` fails, so a failed move never leaves the file in the new folder under the old name.
- [x] S3 DSF through the grant: scan lists DSF via the tree; jaudiotagger DSF reader, justdsd decoder and `MusicAnalyser` read from a channel; playback and server stream DSF from a descriptor.
  S3 findings (2026-10-16): on API 34 without all-files access, a DSF in a granted folder passes `exists`, `isFile`, `length`, `lastModified` and `Files.readAttributes` by path; only opening it (`canRead` false, `FileChannel.open` AccessDenied) and listing its folder fail. So stat sites (scan's outdated check, `readFileInfo`, `isMediaFileExist`, `FileResponse` size and date) need no change. justdsd is not used by the app (DSF analysis decodes with FFmpeg). In-app ExoPlayer has no DSF extractor, so in-app DSF playback is out of scope; external players get DSF through `MusicFileProvider`. `TimeSeek` handles FLAC and MP3 only.
  S3 steps:
  - [x] `MusicFileAccess` reads: `openRead(file)` (FileChannel), `readUri(file)` (content URI when the path cannot be read, else null), `ffmpegInput(file)` (quoted path, or ffmpeg-kit `saf:` input; ffmpeg-kit opens the descriptor only when FFmpeg opens the input and closes it on close, so one string per run), `list(dir)`, `walk(dir, nameFilter)`. Android: path when `Files.isReadable` (checked before any grant lookup, so readable FLAC/MP3 still get `RandomAccessFile(...).getChannel()`), the grant otherwise; `walk` routes each folder on its own (tree where a grant covers it, path elsewhere) and stays `Files.find` with all-files access.
  - [x] Data-loss fix found in review: the tree `delete` of a folder is recursive in ExternalStorageProvider, and a path listing hides DSF, covers and `.cue`, so `cleanMediaDirectory` could delete an album folder that still held a DSF. `delete` now refuses a folder with anything in it (path semantics); `cleanMediaDirectory`, `FileSystem.copyDir` and `copyRelatedFiles` list through `access().list` (otherwise cleanup would now see a cover that `copyRelatedFiles` skipped, and delete it).
  - [x] jaudiotagger: `AudioFileIO.read(File, FileChannel)`, implemented for DSF (`DsfFileReader` path methods delegate to channel ones). `TagReader.readAudioFile` reads by path when readable, through the channel otherwise; used by `JThinkReader` and `FileRepository.readTaggedArtwork`.
  - [x] Scan: `ScanAudioFileWorker.search` uses `walk`.
  - [x] FFmpeg inputs (`AudioDecoder`, waveform, `SpectrogramGenerator`, `AudioAuthenticityAnalyzer`, AcoustId, `FFMpegHelper` extract/remove art, convert, ReplayGain, `FFMPegReader.extractTagFromFile`) use `FFMpegHelper.input`. `FFMpegWriter` is unused.
  - [x] Embedded art: a `MediaMetadataRetriever` failure falls back to the tag library instead of aborting; the extracted `Cover.jpg` is written with `writeAtomically` (it can go into the album folder).
  - [x] Server and players: `FileResponse` and `AudioStreamCacheManager` open through `openRead` (`FileResponse` keeps release-on-open-failure); `MusicFileProvider` passes the grant's descriptor through for mode "r" only.
  - [x] Tests. JVM (419 total, 0 failures): `PathMusicFileAccessTest` +4 (delete keeps a non-empty folder, reads, list and walk, related files listed through the access), `DsfChannelReadTest` (channel read equals path read for DSF; other formats refuse). Instrumented `AndroidMusicFileAccessTest` 18 of 18 on API 34 and API 36 with the final code, all-files denied: DSF stats by path but `canRead` is false; `walk` finds the DSF a path walk misses; `delete` keeps a folder holding only the hidden DSF; a folder in the way of a replace or move is never deleted; DSF tags read through the grant; `scanMusicFile` puts the DSF in the library with title and sample rate; FFmpeg decodes the DSF (`Input #0, dsf, from 'saf:1.dsf'`); `openRead` returns the exact bytes and 200 open/close cycles leave the descriptor count unchanged (under 10); `MusicFileProvider` serves the DSF bytes (in-process only).
  - [x] Advisor review fixes: `NioWebServerImpl.createSongResponse` returned 404 on `!canRead()`, which would have blocked every DSF stream in the `play` build; it now accepts a file the grant can read. Library pruning (`cleanInvalidTags` -> `isConfirmedMissingFile`) checks `exists()` first, which works on DSF by path, so scanned DSF stays. `replace` and `moveOnVolume` delete their target only when it is not a folder.
  - [x] Device check on API 36 with the real scan worker and media server (`LibraryScanProbe`, `-e scanProbe true`, then `am start-foreground-service ... START_SERVER` and curl on port 9000). All-files allowed (`github` behavior): full scan 24 tracks (every supported file in `Music/`, DSF included), no temp files left; FLAC and DSF streamed full (200) and ranged (206, `bytes=1000-4999`), md5 equal to the device files. All-files denied with the `Music/` grant: full scan still 24 tracks with the DSF; DSF streamed full and ranged, md5 equal; ranged FLAC 206 by path; no crash-buffer entries.
  Not covered: in-app DSF playback (media3 has no DSF extractor, so ExoPlayer never played DSF; not checked by playing); an external player opening a DSF through `ACTION_VIEW` from another app (S6).
  Moved to S4 by name: FFmpeg output goes straight into the album folder in `FFMpegHelper.removeCoverArt` and `convert`; `getFolderCoverArt` and `CoverThumbnails` list folders by path; `FileContentTypes` sniffs image bytes by path.
- [x] S4 covers and `.cue` through the grant (`FileRepository` cover lookup via `access().list`, `CoverThumbnails`, `FileContentTypes` image sniffing via `openRead`; FFmpeg `removeCoverArt` and `convert` write to cache then `writeAtomically`).
  S4 findings (2026-10-16): no code reads `.cue` contents; `.cue` only travels as a related file (organize copy and folder cleanup), already listed through the access in S3. Covers reach consumers as `File`s from two outlets, `FileRepository.getCoverArt` (Coil `CoverartFetcher`, `TagsTechnicalPage`) and `getCoverArtByAlbumartFilename` (server album art, `CoverThumbnails`, `FileContentTypes` sniffing). Folder covers are found with `listFiles`, which hides images under scoped storage. `TagsActivity` and `ApplicationUtils` otherwise write only app storage; Search & Match and artwork edits already stage and commit through `writeAtomically`.
  S4 steps:
  - [x] `getFolderCoverArt(String)` and `getFolderCoverArt(File)` list the folder through `access().list`, memoized per folder by path and folder modification time (a scan looks the cover up once per track). Empty listings are not remembered.
  - [x] `FileRepository.readableCover(context, cover)`: when only the grant can read a cover, it is copied once to the cover cache (`granted-<md5 of its path>.<ext>`, refreshed when the cover is newer or a different size). Used only where covers are opened: Coil `CoverartFetcher`, the decode in `TagsTechnicalPage` (path and size shown stay the real cover's), and `BaseServer` album art (served and thumbnailed). The lookups still return the real cover, so nothing displays or writes the cache copy. With all-files access the cover itself is returned.
  - [x] `FileContentTypes.readContentForMime` sniffs through `openRead`.
  - [x] FFmpeg output to app cache: `removeCoverArt` replaces the track with `writeAtomically` (no `no_embed` file in the album); `convert` writes its temp in cache, then `moveToAvailablePath`.
  - [x] `PathMusicFileAccess.move` across file systems (app cache to shared storage, EXDEV) copies beside the target and renames it into place instead of copying onto the final name, so a crash cannot leave a truncated converted file for the scan (advisor review). Same file system: an atomic rename as before.
  - [x] Tests. JVM 419, 0 failures. Instrumented `AndroidMusicFileAccessTest` 23 of 23 on API 36 and on API 34 (the EXDEV precondition was added after the API 34 full run and passed there on its own). New: a hidden `Cover.jpg` stats by path but is unlisted and unreadable; the folder lookup finds it, `readableCover` returns a readable copy with its bytes and refreshes after the cover changes size; `removeCoverArt` on FLAC leaves the album's names unchanged and readable audio; DSF to FLAC `convert` (FFmpeg reading through `saf:`) adds only `02 Song.flac`; a cache to app-external move (EXDEV confirmed) lands complete with no temp left and keeps an existing target.
  - [x] Device, API 34, 100 album folders x 10 MP3 plus a hidden `Cover.jpg` each (1,021 tracks with the test files): full scan 13.0 s with the grant against 8.0 s with all-files access; all 1,000 covers found. Cover lookups for 1,000 tracks: raw listing per track 10.2 s with the grant (1.9 s by path), memoized 0.6 s (0.65 s). Server album art for a real 600x600 JPEG cover: 200 `image/jpeg`, md5 equal to the cover, and the `tn_` thumbnail 160x160 JPEG, in both modes. No crash-buffer entries, no temp files left.
  - [x] Advisor review fixes: a sweep for other cover opens found none (every Coil request, notification included, goes through `CoverartFetcher`; no artwork URIs). `move` onto the same path is a no-op success again in both implementations (the exists-first check had made it fail), and a rename that changes only the letter case works: by path an atomic rename, through the grant via a journaled temporary name, because the provider would add " (1)". `renameExact` now restores the old name when the provider picks another one. A move across file systems keeps the file's date. Organize itself never moves to a path equal ignoring case. Tests: JVM 421, 0 failures; instrumented 24 of 24 on API 34 and API 36 with the final code.
  S4 changed the all-files (`github`) build too: the per-folder listing memo and the cross-file-system move. Checked in that mode: full scan of 1,021 tracks, server album art and thumbnail. Not run on a device: a move between internal storage and an SD card (no emulator has one).
  Not covered: Coil displaying a granted-folder cover on screen (the fetcher change is one line; S6 on device); the copy cache grows by one file per album cover read through the grant until a full rescan clears the cover cache.

- [x] S5 `play`/`github` product flavors; `MANAGE_EXTERNAL_STORAGE` only in `github`. `play` permission flow (READ_MEDIA_AUDIO plus folder grants instead of all-files access) and the folder-grant UI: "Add folder" opens the system folder picker, explains why, persists the grant, and stores the folder path; the `Download` default is replaced by asking for a folder.
  S5 findings (2026-10-16): the only storage gate is `PermissionUtils.checkAccessPermissions` (all-files access), used by the folder dialog, the System Access screen and its summary chip. Startup scans only when the user has saved scan folders, so a fresh install starts with none. The folder dialog defaults to `Music` and `Download` per volume (shown as undeletable "Default") and adds folders with a path-based file picker. Organize always writes under primary `Music/` and starts from `MainActivity.doMoveMediaItems` and `TagsActivity.doMoveMediaItems`. No BuildConfig use yet. CI builds `assembleRelease` and publishes `apk/release/app-release.apk`.
  S5 steps:
  - [x] Flavors: dimension `distribution`, `github` and `play`, same `applicationId` for now (D0 still open: this only lets the two builds update each other if Play uses the existing key; with a Play-generated key, switching means uninstalling). `MANAGE_EXTERNAL_STORAGE` is in `src/github/AndroidManifest.xml` only. `aapt2 dump permissions`: the `play` APK has no `MANAGE_EXTERNAL_STORAGE`; SimpleStorage's merged `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` (no effect on API 34+) are removed from both. `StorageMode.usesAllFilesAccess(context)` reads the mode from the manifest; flavor-specific `StorageModeTest`s (`androidTestPlay`, `androidTestGithub`) keep it honest.
  - [x] Access gate per mode: `PermissionUtils.checkAccessPermissions` is all-files access (`github`) or READ_MEDIA_AUDIO (`play`). System Access in `play` asks for READ_MEDIA_AUDIO at runtime and opens the app's settings once Android stops showing the dialog; card texts and the intro come from `src/play/res` overrides. `hasFullStorageAccess` became `hasStorageAccess`. Installing `play` over `github` resets the all-files appop to default (checked on API 36).
  - [x] Folder dialog in `play`: no default folders (unset means none), "Add folder" opens `FolderGrantActivity` at that volume's Music, rows not covered by a grant show "Allow access" (picker at that folder; a different pick is added as its own row with a message), an explanation line, an empty-list hint, and Start Scan disabled with no folders (both flavors). A folder the picker cannot map to a path has its grant released. If the activity is recreated while the picker is open, a granted scan folder is saved and the dialog reopens.
  - [x] Organize in `play` (`MainActivity` and `TagsActivity`): without a grant on primary `Music` itself, explain, open the picker at Music, and continue only if the result covers Music (`MusicFolderAccess`).
  - [x] CI and docs: `release.yml` builds `assembleGithubRelease`, publishes `apk/github/release/app-github-release.apk`, and fails if it lacks `MANAGE_EXTERNAL_STORAGE`. CONTRIBUTING describes both flavors and the new task names. Screenshot references moved to `src/screenshotTestGithubDebug` (validate with `:app:validateGithubDebugScreenshotTest`); 33 of 34 previews already differed at HEAD before S5, so the references are stale independently of this step. Lessons and memory: the S25 gets the `github` flavor only, checked with `aapt2 dump permissions` before installing.
  - [x] Tests: unit 421 (core 226, app 151 per flavor, server-jupnp 43, db-room 1), 0 failures. `play` instrumented on API 34 and 36: `StorageModeTest`, `MusicFolderAccessTest` (a subfolder grant does not count for Organize; a Music grant does), `AndroidMusicFileAccessTest` 24 of 24 (the cross-file-system test now clears its folder first: a temp left by an earlier interrupted run had failed it; three clean runs leave nothing). `github` on API 36: `StorageModeTest`, `AndroidMusicFileAccessTest` 24 of 24 (all-files denied). Manual, API 36: `play` first run (empty library, System Access with the Play texts, the system audio dialog, back to the folder dialog with no defaults, Add folder, the picker at Music, scan of 22 tracks including the DSF); Organize with only `Music/MMGrantTest` granted showed the prompt, the picker at Music, and moved the MP3 to `Music/Anar Software LLC/Blank Audio (LOSSY)/` with its `Cover.jpg`, then removed the emptied folder. `github`: folder dialog unchanged (Default Download and Music).
  - [x] Advisor review: full instrumented suites with no class filter on API 36: `play` 46 of 46, `github` 45 of 45 (all-files denied). Two accessibility tests had hard-coded the GitHub wording of the storage card and summary; they now read the strings from resources, so they hold for both flavors. Release builds with R8: `assembleGithubRelease` and `assemblePlayRelease` build; `aapt2 dump permissions` shows `MANAGE_EXTERNAL_STORAGE` only in `github` and no legacy storage permissions in either. Play release signed with the local debug keystore, on the API 36 emulator (fresh install): System Access, the audio dialog, Add folder through the picker, scan of 22 tracks, and the DSF listed as DSD / DSD64 with DR 39 (header read through the grant and DR decoded by FFmpeg after R8). `./gradlew test` as documented: 421 tests, 0 failures.
  Not covered: recreation while the picker is open (code path only); "Allow access" on a row (same launcher as Add folder, not tapped by hand); the `github` flavor's storage suite on API 34 (ran on 36).
  Follow-ups (not blocking, only for scan folders carried over from the GitHub build, since in the normal Play flow every scan folder is granted):
  - Every write in a folder no grant covers (tag save, convert, delete, cover change, extract art) fails with a generic error; it should name the folder to allow.
  - A carried-over `Download` (or volume root) row shows "Allow access", but the picker refuses those roots, so it can never be granted; hide the action there or say why.

- [x] S6 verify on API 34 and 36 emulators and the S25 (signing gate first, never uninstall or clear data): scan, DSF playback in an external player through `ACTION_VIEW` (cross-app `MusicFileProvider` grant) and streaming, tag edit on FLAC/MP3/DSF, organize, crash during write leaves the original intact.
  S6 steps (emulators first, `play` flavor unless noted; phone last, `github` flavor only):
  - [x] E1 (API 36, `play`): titles of an untagged FLAC, MP3 and DSF saved through the tag editor and found in the files ("Edited FLAC S6" in the Vorbis comment, the ID3 tags of the MP3 and DSF); no temp files. **Bug found and fixed (pre-existing on `main`):** `JThinkWriter` created a default tag for an untagged file but never attached it, so the DSF save failed with a NullPointerException in `DsfFileWriter` and edits to other untagged files were dropped (`2f7a13c7`, JVM regression test fails without the fix). Artwork: **second pre-existing bug fixed:** "Choose Cover Art from Gallery" returned nothing on Android 16, because the GET_CONTENT trampoline's forwarded launch is cancelled for the `singleInstance` `TagsActivity`; it now uses the photo picker directly (`1ea01181`). Then the chosen JPEG was staged, saved as `Cover.jpg` through the grant (md5 equal to the source) and shown in the editor and the list for the DSF. Batch save through `FileRepository.setMusicTag` (the editor's Save call) on the demo library: 19 files, 761 MB in 5.1 s (101 to 406 ms per file, 939 ms for the first), 0 failures, app cache clean afterwards (emulator on a Mac SSD; a phone will be slower). Not as specified: now playing could not show the DSF's cover, because the in-app player skips DSF (media3 has no DSF extractor; confirmed by playing: the queue moved on to the next track).
  - [x] E2 (API 34, root shell): `content read` of the DSF through `MusicFileProvider` from another process. **Third bug found and fixed:** on a cold start the provider served requests before `Application.onCreate` installed the folder-grant access, so the read failed with EACCES (warm reads worked). The provider now installs it in its own `onCreate` (`4ef37c8a`); five cold reads in a row byte-identical. Proves the descriptor crosses processes, not that a real player app works.
  - [x] E3 (API 34, `play`): DSF and FLAC streamed full (200) and ranged (206), md5 equal.
  - [x] E4 (API 34, `play`): force-stop mid-replace left the original, a 125 KB partial `.mmtmp` and a journal entry; the next start logged `DELETE_TEMP`, removed the temp, kept "original", cleared the journal.
  - [x] E5 (API 34, `github`): full instrumented suite 46 of 46. Release signed with the real key (B5:75:7A) at `4ef37c8a` (all fixes): scan of 23 tracks, DSF title saved through the editor; the earlier release build of this branch also saved a FLAC title, organized an MP3 into `Music/Anar Software LLC/Blank Audio (LOSSY)/` with its cover, and streamed a ranged DSF (206, `audio/x-dsf`, md5 equal). The only crash-buffer entry was Google Messages, not MusicMate.
  - [x] E6 (API 34, `play`, "Don't keep activities" on): a seeded ungranted `Podcasts` row showed "Allow access", the picker opened at Podcasts, and after the activity was discarded the app saved the grant and reopened the dialog with the row granted. Setting turned off again.
  Findings, not fixed: after a cover change, other tracks in the same folder keep showing their old cached image (Coil is keyed by the album art filename set at scan time, which does not change), until a rescan re-reads them; same logic in both builds. Probe-scanned libraries keep rows for deleted folders (`isConfirmedMissingFile` treats a missing parent as unavailable, by design).
  - [ ] P1 Phone (user connects it and says yes after being told: which build, that it is unreleased, and that rollback means reinstalling a published APK with the same or higher versionCode): signing gate (installed `base.apk` certificate equals the release APK's; APK requests `MANAGE_EXTERNAL_STORAGE`; phone versionCode not above 154; no Room schema change since the installed release's tag), library count from the UI header; install `github` release as an update (never uninstall, never clear data).
  - [x] P1 Phone (2026-10-16, S25 SM-S931B, Android 17 / API 37, wireless adb 192.168.1.54:45099): installed 3.28.3-261015 versionCode 154 with all-files allowed; installed `base.apk` and the new APK both certificate b5757a1e... (B5:75:7A); new APK requests MANAGE_EXTERNAL_STORAGE; no db-room change since v3.28.3. Library 8,083 tracks before. Installed `github` release `4ef37c8a` with `adb install -r` (no uninstall, no clear): 3.28.3-261016, all-files still allowed, 8,083 tracks after, no crash.
  P2 progress: incremental rescan under 10 s, still 8,083. DSF "14 - Sizzlin BALANCE" (track id 3047816908, found through the web player's websocket `search`) streamed from the phone's running server over Wi-Fi: full 200, 142,639,628 bytes, md5 equal; range 206 of 1 MB, md5 equal. Test album: the two FLACs of "Various Artists/Audiophile Analog Collection Vol 1" copied to `/sdcard/Music/MMTest/` (originals md5 b94df674614bbd4fbcec256b3c936ad3 for "03 - Improvisation.flac" and b1f64008e6c6f65b43162589cae8a79f for "10 - Esperance, Improvisation.flac"); scanned, library 8,085. Finished after the user unlocked the phone: both copies selected in the list (the phone's long press starts multi-select) and edited together in batch mode with Album "MMTest Album" and Album Artist "MMTest Artist" (checked in the files' Vorbis comments); Organize (2) moved them to `Music/MMTest Artist/MMTest Album/` and removed the emptied `MMTest`; Delete (2) removed both files and the emptied folders; library back to 8,083; the originals' md5 unchanged throughout; no MusicMate crash-buffer entries. External player on the phone: "Open in External App" on the DSF "Sizzlin' BALANCE", chooser, HiBy Music: HiBy's media session reported PLAYING with that title (descriptor handed over through `MusicFileProvider` across apps), then paused. Final runs with all S6 fixes: `./gradlew test` 422, 0 failures (core 227 with `JThinkWriterTest`); `play` full instrumented suite on API 36: 47 of 47; `github` full suite on API 34: 46 of 46 (run before the cold-start fix, which only touches the provider and app start). No other content picker is launched from the `singleInstance` editor (only the cover picker used GET_CONTENT). Rollback file for the phone: the pulled previous build `installed.apk` (3.28.3-261015, same certificate and versionCode) in the session scratchpad. Behavior change for GitHub users: "Choose Cover Art from Gallery" now opens the system photo picker instead of a file browser (tested on the API 36 emulator only).
  - [x] P2 Phone checks: app starts, library count unchanged, rescan, DSF stream over the server, and tag edit plus organize only on a copied test album retagged with a unique album artist and album ("MMTest") so organize cannot land in a real album; afterwards delete only that folder.
- [x] Add the `play` and `github` product flavors. Move `MANAGE_EXTERNAL_STORAGE` into the `github` flavor manifest only. (Done in S5.)
- [ ] Verify on the phone (S25, real library), signing gate first (see lessons.md; never uninstall, never clear data): edit tags on 1 and 50 files, change artwork, organize an album, rescan. The library must survive.

### 2.3 Permissions and services
- [x] Remove `KILL_BACKGROUND_PROCESSES`, `MEDIA_CONTENT_CONTROL`, `requestLegacyExternalStorage` (`953ae3dd`). Also removed: `BLUETOOTH` (replaced by `BLUETOOTH_CONNECT` on API 31+), `FOREGROUND_SERVICE_DATA_SYNC` (no data sync service remains: `MediaServerHubService` was already commented out; WorkManager's `SystemForegroundService` has no type and no worker calls `setForeground`), `BIND_NOTIFICATION_LISTENER_SERVICE` as a uses-permission (system-only; the service's `android:permission` attribute stays), and the commented-out service entries.
- [x] Location: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` were never requested at runtime, and the only user, `ApplicationUtils.getWifiSSID`, had no callers. Both permissions and the helper removed from both flavors.
- [x] Notification listener: it powers "External Player Access" (discover and control playback in other music apps through `MediaSessionManager.getActiveSessions`). It is already opt-in with an in-app explanation on System Access ("Optional"), and Android grants it only on a settings screen. Notification access is not on Play's list of permissions needing a declaration (support.google.com/googleplay/android-developer/answer/16585319). Kept in both flavors.
- [x] Media server service: the 6-hour cutoff does not apply: no service uses `dataSync`. Decision (user, 2026-10-16): `MusicMateServiceImpl` declares `mediaPlayback|connectedDevice`, starts as `mediaPlayback`, and adds `connectedDevice` while the media server runs (`setServerForeground`, re-posting the notification already shown); `FOREGROUND_SERVICE_CONNECTED_DEVICE` added, its prerequisite `CHANGE_WIFI_MULTICAST_STATE` was already declared. Neither type has a timeout. Advisor review fix (`c1b86d92`): stopping the server only drops connectedDevice from a service that is in the foreground, and `onDestroy` no longer touches foreground state. Checked with `dumpsys activity services`: started and stopped with `am start-foreground-service` on API 34 and 36 (0x2, 0x12, 0x2), and through the app's own Music Center Server tab on API 36: start 0x12, stop 0x2; with a track paused: stop 0x2, start 0x12; playing with the server on 0x12 (media3's playback does not reset the types); paused and stopped 0x2; no errors, no crash. Media3 did not take the service out of the foreground after a pause (still foreground 4 minutes later, 2 of them with the app in the background), so the "stopped while out of the foreground" case could not be produced; the guard is defensive. Play Console: declare both types with a short video each (Phase 4).
- [x] Verify (release builds of the final code, `aapt2 dump permissions`): `play`: BLUETOOTH_CONNECT, INTERNET, ACCESS_LOCAL_NETWORK, MODIFY_AUDIO_SETTINGS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, FOREGROUND_SERVICE_CONNECTED_DEVICE, READ_MEDIA_AUDIO, POST_NOTIFICATIONS, VIBRATE, WAKE_LOCK, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, RECEIVE_BOOT_COMPLETED (WorkManager), and the app's own dynamic receiver permission. `github` release: the same plus MANAGE_EXTERNAL_STORAGE. Final code: `./gradlew test` 0 failures (core 227, server-jupnp 43, db-room 1, app 151 in each flavor); full instrumented suites on API 36: `play` 47 of 47, `github` 46 of 46 (all-files denied). These changes apply to the `github` build too; none of them is on the phone yet.

### 2.4 Licenses and policy
- [x] Confirm the license of `ffmpeg-kit-full` 2.2.1 from that fork's own docs and meet its terms (D3 keeps the app open, so GPL would not block it, but the notice and source offer still apply).
  2.4 findings (2026-10-16):
  - FFmpeg: the `ffmpeg-kit-full` 2.2.1 AAR (Maven POM: "FFmpeg v8.1.1 Full", source `github.com/sk3llo/ffmpeg-kit-flutter`, POM lists LGPL 3.0 and GPL 3.0 because the project also ships GPL variants) is configured with `--enable-version3` and without `--enable-gpl` or `--enable-nonfree` (read from the configure string in its `libavutil.so`). So the build MusicMate ships is LGPL-3.0-or-later. External libraries compiled in: LAME, opus, vorbis, theora, speex, shine, twolame, libvpx, dav1d, kvazaar, libwebp, libass, freetype, fontconfig, fribidi, harfbuzz, libxml2, gnutls, gmp, iconv, zimg, snappy, soxr, ilbc, opencore-amrnb, vo-amrwbenc, zlib. No x264, x265 or other GPL-only codecs. Obligations: notices and license texts in the app; corresponding source available (link the exact upstream source, and ideally mirror it with each GitHub release).
  - In-repo modules: jaudiotagger LGPL-2.1-or-later and modified here (the full modified source is in this open repo, which satisfies relinking); JustFLAC under the GNU Library GPL (shipped through core); justdsd not linked; crashreporter is balsikandar's CrashReporter; `library:library` vendors Facebook Rebound (BSD), opencsv, carousel layout manager, loading animations, a particle loader, a trapezoid view and a curved bottom bar, none of which the app or core uses (only its `apincer.android.utils` and `storage` packages are used; release builds strip the rest).
  2.4 steps:
  - [x] Deleted the unused third-party code from `library:library` (Rebound, opencsv, carousel layout manager, loading and particle animations, trapezoid view, curved bottom bar, dialog listener, and the layouts only they used; `1b8a219d`). The slf4j provider stays: it is loaded at runtime through `META-INF/services`, which an import search misses.
  - [x] Licenses screen (user chose AboutLibraries; `89868b9d`): About > Open-source licenses, AboutLibraries 15.3.0 (`com.mikepenz.aboutlibraries.plugin.android` plus `aboutlibraries-compose-m3`), 231 libraries, every one with a license (`library.requireLicense` fails the build otherwise). `app/aboutlibraries/` adds FFmpeg 8.1.1 and FFmpegKit (LGPL 3.0 or later), the 30 libraries linked into FFmpeg with each upstream notice (found from the configure string and ffmpeg-kit's build scripts, fetched from each project), jaudiotagger, JustFLAC and CrashReporter, and upstream notices for commonmark, jsoup, SLF4J and jUPnP (CDDL, with its source link); PhotoView gets Apache 2.0 (its JitPack POM names none). The FFmpegKit POM entry, which lists GPL 3.0 for every variant, is excluded. The GNU texts show in the app; Apache 2.0 opens the official text (the POM URL wins). Checked in the R8 `play` release on API 36: the list loads, FFmpegKit shows LGPL 3.0 or later, zimg's notice opens in-app.
  - [ ] FFmpeg source (user's decision): FFmpeg 8.1 confirmed from the binary (libavcodec 62.28.102, libavformat 62.12.102; the release/8.1 branch has libavcodec 62.28, release/8.0 has 62.11), matching the POM's 8.1.1; the "ffmpeg-kit-6.0.LTS" in the configure string is only a folder name. The POM's source link, github.com/sk3llo/ffmpeg-kit-flutter, returns 404; the live repos are github.com/sk3llo/ffmpeg_kit_flutter (Flutter plugin) and github.com/sk3llo/ffmpeg-kit (native build), and the latter holds build scripts that download FFmpeg and the libraries at build time, not their source. So a link does not give users the corresponding source of the LGPL parts in the APK: FFmpeg, FFmpegKit, LAME, GnuTLS, Nettle, GMP, libiconv, FriBidi, shine, TwoLAME and soxr. Recommendation: publish a source archive of those (FFmpeg 8.1 release plus each library at the version the fork's scripts pin) with each GitHub release and the Play listing, or build FFmpegKit ourselves from pinned sources.
  - [x] Privacy policy: `docs/PRIVACY_POLICY.md`. Public URL once on `main`: https://github.com/thawee/musicmate/blob/main/docs/PRIVACY_POLICY.md (Play Console needs it; a GitHub Pages page also works). Contact is GitHub issues; add an email if wanted. Data leaving the device, all on a user action and over HTTPS: title and artist to MusicBrainz and, only for songs with neither, an AcoustID fingerprint and duration (Search & Match); release ids to the Cover Art Archive; artist and album names to Wikipedia (web player details); a Google search in the browser (Search Song on Web). Local network only: DLNA and the web UI. No analytics, ads or accounts; crash logs stay on the device unless the user shares one. Notification access (External Player Access) only lets MusicMate see which music apps are playing; `MediaNotificationListener` never reads notifications (corrected in `bdf679ae`).
  - [ ] Data safety answers (user to enter in Play Console; Google counts any transmission off the device as "collection", support.google.com/googleplay/android-developer/answer/10787469). Proposed, conservative: collects data: Yes. Type: Audio > Music files (song titles, artists and audio fingerprints derived from the user's files). Collected, not shared (sent on a specific user action, which Google exempts from "sharing"). Optional (only when the user runs Search & Match or opens web player details). Purpose: App functionality. Encrypted in transit: Yes. Deletion: no data reaches the developer, so nothing to delete; answer per the form. No other data types (no location, identifiers, contacts, analytics). Microphone (car calibration, 2026-10-23): no declaration, since the recording is processed only on the device and never leaves it; Google lists on-device-only processing as not in scope for data collection (same help page). The alternative answer "no data collected" rests on the ephemeral-processing exemption, which does not obviously apply because the third parties may keep their logs.
  - [ ] jaudiotagger modification notices: the three files this branch changed carry one (`bdf679ae`); older MusicMate changes to jaudiotagger in `library/jaudiotagger-android` still need the notice LGPL 2.1 section 2 asks for (follow-up).
  - [x] Verification after the 2.4 deletions: no test, androidTest or screenshot source references the removed strings or classes; the only runtime resource lookup is Android's `status_bar_height`. `./gradlew test` 0 failures; both instrumented test APKs build; screenshot sources compile (34 run, the same 33 stale references fail); `play` full instrumented suite 47 of 47 on API 36.
  - [x] MQA wording: the UI shows detection badges ("MQA", "MQA MASTER", "MQA STUDIO", from the file's MQA encoder tag) and the format's name "Master Quality Authenticated (MQA)" in the About quality guide; no certification claim. Removed 35 unused `encoding_*` strings that included "MQA Verified" and "authenticated and matches the original master". For the store listing: say MusicMate detects MQA-encoded files from their tags and does not decode MQA; no MQA logo.

## Phase 3: Pro unlock (weeks 6 to 9, needs D2 and D3)
- [ ] Play Billing one-time product `pro_unlock`. Cache the entitlement so it works offline, and offer restore purchase.
- [ ] Gate the Pro features with one `ProFeatures` check. Free users see the feature with a short "Pro" explanation, not a hidden menu.
- [ ] `github` flavor: Pro unlocked (D3 fully open makes a lock there pointless). State in the README that buying Pro on Play is how to support the project.
- [ ] Verify with Play license testers: buy, refund, reinstall, offline launch.
  Phase 3 proposal (2026-10-16):
  - Pro entry points (D2): Verify Lossless Quality report (`TagsActivity.doShowSpectrum`: verdict, spectrogram, format and DR analysis), batch tag editing (Edit Tags with more than one track: `MainActivity.doShowEditActivity`, the batch editor), MusicBrainz lookup (`doSearchAndMatchTags`, Auto-Tag), Organize (`MainActivity` and `TagsActivity.doMoveMediaItems`). Free: player, DLNA server, quality labels and DR badges in lists (background analysis), single-track tag editing, Tidy Tags and Smart Clean, Convert Format, covers.
  - One `ProFeatures` interface in core: `isPro()` and a `LiveData`/flow of it. `github` flavor: always Pro (no billing library in that APK). `play` flavor: Play Billing Library 9.1.0, one-time in-app product `pro_unlock`; the entitlement is cached locally so Pro works offline, refreshed from `queryPurchasesAsync` at start; purchases acknowledged; "Restore purchase" re-queries. Verification stays on the device: the app is open source, so a server check would only protect against people who could rebuild it anyway (D3).
  - Decisions (user, 2026-10-16): Verify Lossless Quality: free users see the verdict, Pro adds the spectrogram, band analysis and DR details. Price: 4.99 USD for `pro_unlock` (set in Play Console; the app shows Play's localized price).
  - Gate UX: the feature stays visible; tapping it opens one "MusicMate Pro" sheet that names the feature, lists what Pro includes, shows the price from Play, and offers Buy and Restore.
  - Batch scope (user, 2026-10-16): free users can open the batch editor (a Pro notice shows at once); any tag write to more than one file needs Pro (Save, Smart Clean, Tidy Tags and From File all save through `TagsActivity.doSaveMediaItemsDirectly` or `TagsEditorFragment.doSaveMediaItem`); batch delete and convert stay free.
  - Route table (every way into a Pro feature; gates sit inside these functions, not on menu items): Verify Lossless report `TagsActivity.doShowSpectrum` (menu line 765, action 1086): free runs `AudioAuthenticityAnalyzer` for the verdict and skips `SpectrogramGenerator` and the analytics block. MusicBrainz `doSearchAndMatchTags` (759, 1525) and `doAutoTag` (1084; also AcoustID). Organize `TagsActivity.doMoveMediaItems` (613 via AfterSave, 2017, 2033) and `MainActivity.doMoveMediaItems(List)` (2055, 2277); `FileOperationTask.moveFiles` and `importAudioFile` are only reached from these. Batch tag writes: the two save functions above when more than one track. Not Pro: web player commands (browse, search, queue, playback; only the DR score, same as the list badge), Tech Info tab (DR score), DR badges, `measureDR`, `encodeFiles`, delete.
  Phase 3 steps:
  - [x] `ProStore` interface in app main (Pro state, Play price, purchase, restore); entitlement rules in a pure class over a small `BillingGateway` interface, unit-tested with a fake: Pro only for `pro_unlock` in PURCHASED (never PENDING), acknowledge unacknowledged purchases on every query, cache locally for offline, revoke only after a successful query without the product (never on SERVICE_UNAVAILABLE, BILLING_UNAVAILABLE or network errors).
  - [x] `play` flavor: Play Billing Library 9.1.0 (`playImplementation`) behind `BillingGateway`; startup query; auto service reconnection. `github` flavor: always Pro, no billing library.
  - [x] One `ProGate.require(activity, feature, action)` used inside every routed function; one Pro dialog (feature name, what Pro includes, Play price, Buy, Restore, Not now). About gets a Pro row (Play: Get Pro / Restore / active; GitHub: "Pro features are included in this build").
  - [x] Verify Lossless free verdict; batch editor Pro notice on open.
  - [x] Debug-only Pro override (debug source set; release builds cannot contain it; checked in the release APK).
  - [x] Tests: entitlement JVM tests; flavor tests (GitHub always Pro, never shows the dialog); emulator check of every gate with the override off and on; `aapt2` permissions (`com.android.vending.BILLING` in `play` only, absent from `github`, no billing classes in the GitHub APK); privacy policy and Data safety updated for Google Play purchases.
  Phase 3 results (2026-10-16, commit fdbf19a2): the entitlement rules live in `ProEntitlement` (pure, 7 JVM tests: PURCHASED only, PENDING never, acknowledge on every query, offline keeps the cache, only a successful query without the product revokes, purchase updates never revoke, other products ignored). Emulator, `play` debug with the override off: the Pro dialog opens from MusicBrainz lookup, Organize in both screens, and Save on two tracks; Verify Lossless shows the verdict only with Get Pro; the batch editor shows its notice. Override `pro`: the batch Save wrote both files. About shows the Pro row. `com.android.vending.BILLING` is in the `play` APK only; no billing classes in the `github` APK; the release APK has no override code. `./gradlew test` passes; instrumented suites: `play` 48 OK, `github` 47 OK. Privacy policy has a Purchases section. Data safety: purchases need no declaration, since Play Billing handles payment and the app never sees it (Google's Data safety FAQ). Write audit (advisor): every file tag write was traced to its caller. Batch Remove Embedded Cover Art skipped the gate; fixed and checked on the emulator (free: Pro dialog; override `pro`: the confirm dialog). Free by decision: Convert copies tags into the new file, DR measuring writes its results, and Extract Cover writes folder cover files (not tags). Cover drafts commit through the gated Save. Open polish: the dialog theme colors Not now and Restore red; Restore shows only a "checking" toast and no result.
  - [ ] Blocked on D0, D0b and a Play Console account: purchase, pending purchase, refund, reinstall and offline launch with license testers on an internal-testing track.

## Phase 3b: Verify Lossless analyzer (user, 2026-10-16: before Phase 4 continues; test music from free sample downloads)
- [x] A1 Corpus: free hi-res sample files whose license allows this use (checked and recorded), kept in the scratchpad, never committed. Keep only originals whose spectrum clearly reaches above 22 kHz. Derive from each: a fake (to 16/44.1, back to the original rate and depth), a lossy fake (AAC 256 at 44.1, decoded, back to the original rate), and a CD copy (16/44.1). Expected verdicts: original native hi-res; fake upscaled; lossy fake lossy or upscaled; CD copy lossless CD quality.
  A1 corpus (2026-10-16), in scratchpad `corpus/` only: tuning originals from the Live Music Archive (archive.org etree; each band's non-commercial trading policy; used locally for testing, not redistributed or committed): AGP 2007-03-03 (Microtech Gefell M200 > Edirol R-4 at 24/96), Dead & Company 2023-07-16 "High Time" (Schoeps MK4 > PCM-A10 at 24/96), NRPS 2011-09-15 "Whatcha Gonna Do" (Schoeps MK41, analog remaster at 24/192). Derived per original (afconvert): CD copy 16/44.1, fake upsampled from it, fake from AAC 256, fake with a cheap resampler (`-r 0 --src-complexity line`), fake from 48 kHz, 48 kHz fake from 44.1, AAC 128 and AAC 256 CD copies. Held out, never tuned on: Furthur 2012-07-15 "Other One Tease" and "At a Siding", published from one master as 24/192, 24/48 and 16/44. Plus demo Night Drive (upsampled) and Strings at Dawn (synthetic 24/192).
  Findings: the genuine originals have almost nothing above 22 kHz (microphones roll off near 20 kHz), so band levels cannot separate them from their fakes at any filter order. HEAD's analyzer on the device gives each original the same verdict as its fakes and calls CD copies "MPEG/Lossy Source" (noise floor always NaN). What separates them: a fine spectrum (Welch, 8192 points, 250 Hz bands). Fakes fall off a 50 to 85 dB cliff at 22.05 kHz (24 kHz when made from 48 kHz); originals continue smoothly. A cheap resampler leaves no cliff but mirror images that rise with frequency above 22.05 kHz. AAC 128 leaves a cliff near 17.5 to 18 kHz; AAC 256 (Apple encoder) leaves none and is undetectable.
  Prototype `tune/cliff.py` (2026-10-16), every corpus file as expected: originals "no sign of upsampling" 3/3; fakes upsampled from 44.1 6/6 (plain and from AAC), cheap resampler 3/3 (images), from 48 kHz 3/3, 48 kHz from 44.1 2/2; CD copies and AAC 256 "no lossy cutoff found"; AAC 128 "lossy, cutoff about 17.5 to 18 kHz" 2/2. Held out: 24/192 not flagged 2/2, 24/48 and 16/44 no lossy cutoff 4/4. "Can't tell" when the band where a cliff would show is within 12 dB of the format's rounding floor.
- [x] A2 Offline harness: `analyzer_sim.py` mirrors the Java filters, levels and `classify()`; check it against the app on at least two files before trusting it. Score the current analyzer on the corpus (baseline).
- [x] A3 Fix: whole-window statistics (no `reset`), steeper bands (cascaded 2-pole stages), thresholds retuned on the corpus only where needed; every change explained by a measurement. Keep the Java and the harness in step.
- [x] A4 Device check: an androidTest probe that runs `AudioAuthenticityAnalyzer` on pushed corpus files and prints value and verdict, on the emulator; the app's Verify dialog on two files; analysis time before and after.
- [x] A5 The report's "Dynamic Range" (peak minus RMS) next to the DR badge: relabel or show the DR measurement, so one screen never shows two "DR" numbers.
  Results (2026-10-16, user chose the new method and verdicts): commits bb64c241 (FFmpeg log level, reworded from 53bedf42 before any push), 6799274d (`SpectrumAccumulator`, `SpectralCliffDetector`, analyzer rewrite, report shows the track's DR, error state), 91f3a9cd (demo Night Drive upsampled). Device probe (`AuthenticityProbe`, `-e authProbe <folder>`) matches the Python prototype on 33 of 33 files; 1 to 6 s per file on the emulator (HEAD's analyzer took 1 to 5.7 s); no temp files left in cache. JVM: 8 `SpectralCliffDetectorTest` cases on synthesized signals; `./gradlew test` passes. App: Night Drive "Afterglow" shows "Upsampled from 44.1 kHz", cutoff 22 kHz, DR7 (same as its badge), and the spectrogram shows the cliff. DSD files are not checked ("Not checked: DSD"). User Guide describes the verdicts and their limits.
  Advisor review fixes (2026-10-16, commit 7e17b49e): the image rule compared trends, so DSD-sourced PCM (noise hump rising above 25 kHz) read as resampler images: simulated with a 2nd-order 1-bit modulator on the AGP original, decimated to 88.2 and 176.4 kHz, both flagged. Now per-bin fine structure (images 0.89 to 0.99, everything else 0.10 or less; a time-correlation test was rejected: genuine content scored 0.42 to 0.94), gated on content above the edge. Cheap 48 to 96 kHz fakes added (2/2). Prototype and device agree on 37 of 37 files, 0.2 to 0.9 s each with nothing else running. MP3 and AAC files report "Lossy format (AAC)" (checked in the app, free mode); Verify runs on its own thread instead of the pool Measure DR batches use. JVM tests: 9, including a noise hump; a mutation (threshold -1) fails two of them.
  Follow-ups found: a full rescan kept library entries for folders deleted outside the app (fixed 2026-10-16, commit 733d0316: the missing check walks up to the nearest existing folder, bounded by the mounted storage volume; emulator 21 to 19 tracks after deleting a test artist folder); README still says MusicMate "detects ... MQA"; at 1080x1920 the Verify dialog's bottom button is cut off.
- [x] A6 Tests and record: unit test for the parsing and classification with captured FFmpeg output; results table in this file; commit; then reword or follow up commit 53bedf42's message before any push.

## Phase 4: store listing (weeks 7 to 10)
- [ ] Title and short description built around the server and honesty, not "music player". Draft 2 variants for a Play store listing experiment.
- [ ] 6 to 8 screenshots: the server serving a renderer, the fake hi-res verdict, DR, the audio path, the tag editor, the player picker. Plus a feature graphic.
- [ ] Renderer compatibility page (docs or a GitHub page) seeded with closed-test results.
- [ ] Translations: Thai first (native speaker), then Japanese and German if Phase 1 shows demand from those communities.
  Phase 4 facts (2026-10-16, Play Console Help 9898842 and 9866151, gradle-play-publisher README):
  - Title 30 characters, short description 80, full description 4000. No ranking or price words ("#1", "best", "top", "new", "sale", "free for a limited time"), no emojis or repeated special characters, no ALL CAPS outside the brand, no anonymous testimonials, no brand logos without permission.
  - Screenshots: JPEG or 24-bit PNG without alpha, 320 to 3840 px, long side at most twice the short side; for promotion at least 4 portrait shots of 1080x1920 or more at 9:16. No device frames, no fingers, taglines under 20% of the image, full status bar icons. Feature graphic 1024x500, JPEG or 24-bit PNG, no alpha. Icon 512x512 32-bit PNG.
  - The name "MusicMate" is already used by other music apps (an iOS social app "MusicMate: Songs & Friends", older Android apps), so the title needs a descriptive suffix.
  - Layout chosen for the text and images: `app/src/play/play/listings/en-US/` (`title.txt`, `short-description.txt`, `full-description.txt`, `graphics/phone-screenshots/`, `graphics/feature-graphic/`), which gradle-play-publisher reads for the `play` flavor if uploads are automated later. The listing is `play` only because it sells Pro.
  - Screenshot content: the generated demo library (`tools/demo-library/make_demo_library.py`: invented artists, synthesized audio and covers, free to publish). The emulator cannot see network renderers, so the player picker and a server serving a renderer come from the phone (no cover art from the real library may show).
  Phase 4 steps:
  - [x] 4.1 Listing text, en-US: title, short description, full description; a second title and short description for the store listing experiment. Every claim maps to a feature in the `play` build (folder grants, not all-files access; no MQA; Pro and free split stated plainly). Check lengths and banned words with a script.
  4.1 drafts (2026-10-16), in `app/src/play/play/listings/en-US/`: A (default, server-led) title "MusicMate: Hi-Res DLNA Server" (29), short "Stream hi-res music from your phone to DLNA players, and spot upsampled fakes." (78; was "spot fake hi-res files" before Phase 3b narrowed it); full description 2030 characters. B (experiment, quality-led) title "MusicMate: Hi-Res Player & DR" (29), short "Is your hi-res real? Check DR and upsampling, then stream it to your hi-fi." (75). No third-party app or device names (only codec names); "DLNA" is a trademark used descriptively, as other Play apps do.
  - [x] BLOCKER for the listing hook (resolved by Phase 3b): the Verify Lossless analyzer is not validated (found 2026-10-16 while taking the verdict screenshot).
    - Fixed (commit bb64c241, reworded from 53bedf42): FFmpeg's log level is process-wide; the spectrogram ran alongside with `-loglevel error` and other jobs set FFmpegKit to QUIET, so the analyzer's session output was empty and every value was NaN.
    - Open: every `astats` in `AudioAuthenticityAnalyzer` has `reset=1..5` (used like labels). FFmpeg resets the statistics every N frames (`af_astats.c`), so DR, peak, RMS, noise floor and every band level come from the last 1 to 5 frames of the 60 s window, each band from a different span. Verdicts so far are effectively from a tenth of a second.
    - Open: removing `reset` alone is a regression. The bands use FFmpeg's 2-pole (12 dB/oct) lowpass/highpass, so loud audible content leaks into the 16 to 20 kHz and >24 kHz bands at about -38 dB. Measured on the emulator: the upsampled demo track (made at 44.1 kHz, upsampled to 24/96) went from "Upscaled Content" (cutoff 16 kHz) to "MPEG/Lossy Source" (cutoff 48 kHz). An offline copy of the analyzer (scratchpad `tune/analyzer_sim.py`) with four cascaded stages per edge (48 dB/oct) restores the separation for that track.
    - Not done on purpose: tuning synthetic "genuine hi-res" content until the analyzer agrees would be circular. Validation needs real recordings: real hi-res tracks with content above 22 kHz, each paired with a fake made from it (down to 16/44.1, back up), plus a lossy copy; the analyzer must tell each original from its fakes. Source and scope are the user's call; the fix changes verdicts for current GitHub users.
    - Also: the report's "Dynamic Range" is peak minus RMS (crest factor) while the list badge is the DR measurement, so one screen shows two different "DR" numbers.
    - Held until validated: "spot fake hi-res" in the short description, the Verify Lossless line in the full description, and any verdict screenshot. Resolved by Phase 3b; wording narrowed to what the check shows.
  - Demo library: Night Drive (24/96) is now made at 44.1 kHz and upsampled with afconvert, a true fake hi-res album (`UPSAMPLED_FROM` in the generator).
  - [ ] 4.2 Screenshots: 6 to 8 portrait PNGs at 1080x1920 from the `play` debug build on the emulator (demo mode status bar, demo library), plus phone shots for the player picker and the server; check size, ratio, no alpha and no real-library art.
  - [x] 4.3 Feature graphic 1024x500 (no alpha), drawn from the app's own colors and icon, no ranking or price text.
  - [x] 4.4 Renderer compatibility page `docs/RENDERERS.md` plus a GitHub issue template for reports; seeded only with renderers actually tested, filled during Phase 5.
  4.2 to 4.4 results (2026-10-16): seven 1080x1920 RGB screenshots from the GitHub debug build (same screens as Play; Pro shown) on the emulator with the demo library and demo-mode status bar, in `graphics/phone-screenshots/1..7.png`: 1 Verify Lossless on Night Drive "Afterglow" ("Upsampled from 44.1 kHz", cutoff 22 kHz, the cliff visible in the spectrogram), 2 library, 3 media server and web remote (idle), 4 Music Center, 5 song details, 6 tag editor, 7 queue. Alt text for Play Console (140 max): 1 "Verify Lossless: a 24/96 file upsampled from 44.1 kHz, its spectrum cut off at 22 kHz"; 2 "Library with format, bit depth, sample rate and dynamic range on every track"; 3 "Built-in DLNA media server and web remote running on the phone"; 4 "Music Center: now playing, with the output shown"; 5 "Song details: Hi-Res lossless, 24/192, DR13"; 6 "Tag editor with ready-made choices for artist, album and genre"; 7 "Play queue with quality badges". Icon 512x512 (the launcher vector) and feature graphic 1024x500 rendered from `tools/store-art/` with headless Chrome. `docs/RENDERERS.md` seeded with the HiBy R3 only (the one renderer the project history shows was tested), plus `.github/ISSUE_TEMPLATE/renderer-report.yml`.
  Not done, needs the user: a phone shot of the player picker with real renderers and one of the server streaming to a real renderer (the emulator cannot see renderers; a staged stream from the host showed a fake client name and was discarded); a USB DAC audio path shot. Known blemishes: the Verify dialog's bottom button is cut off at 1920 px; in Music Center shots the status bar icons are dark on dark.
  Listing corrections found while checking claims: 3.26.0 removed bit-perfect USB output (now 24-bit at the song's rate through Android's mixer), and Sony/LG TVs get some formats converted to WAV, so "bit-perfect" and "no transcoding" were removed from the listing. README's "bit-perfect through a USB DAC" and "whether the output is bit-perfect" corrected too (MQA detection still exists in code, so that claim stays).
  Final checks (2026-10-16): fresh APKs (outputs deleted first), `./gradlew test` passes, instrumented `play` 49 OK and `github` 48 OK (each includes the skipped `AuthenticityProbe`). Play debug in free mode with a real folder grant (picker) and a scan through it: Verify shows its verdict ("No sign of upsampling" on the snapshot's native 96 kHz Afterglow, "Upsampled from 44.1 kHz" after pushing the upsampled album), spinner clears, no crash. Pro copy no longer lists DR (the report's DR is now the free badge number): "the spectrogram, the cutoff, and peak and RMS levels". DSD shows "-" for the cutoff instead of "None found".
  - [ ] 4.5 Thai listing: needs a native speaker (the user?); Japanese and German wait for Phase 1 demand.
  - [ ] User decisions: final title, which variant is the default, Thai review.

## Phase 5: closed test (weeks 9 to 11, at least 14 days)
- [ ] Upload the `play` release AAB to a closed track. Invite the testers from Phase 1.
- [ ] Activation (OMTM): the app has no analytics by design. Measure through a short tester form on day 1 and day 7 (scanned? played to an external output? which renderer?) plus Play Console vitals. Do not add tracking.
- [ ] Fix crash and ANR issues reported in vitals. Target: crash-free users at 99% or better.
- [ ] Record renderer compatibility per tester for the Phase 4 page.

## Phase 6: production launch (week 12)
- [ ] Apply for production access with the closed-test evidence. Staged rollout 20%, then 100% if vitals stay clean.
- [ ] Post the launch in the Phase 1 threads, with the album report card.
- [ ] Day-30 review: installs, Pro conversion (H2), store listing experiment result, support load (H5). Decide: keep investing, adjust price, or hold.

# `playlists.json` rule review (2026-10-15)

- [x] Inventory each playlist and its JSON rules, including wildcard and exclusion shapes. Ten playlists: four smart, one title with 519 rules, five genre; no exclusions.
- [x] Compare each rule with `PlaylistEntry`/`GenreRule` matching semantics and the playlist's stated intent.
- [x] Check for duplicate, dead, overbroad, or missing rules using reproducible source or library evidence. JSON parses; one duplicate title/artist key; no unrecognized genre/mood/style terms. No representative phone library available for zero-match validation.
- [x] Report specific JSON paths and proposed corrections, separating confirmed errors from taste or intent choices.

# Static and dynamic playlist review (2026-10-15)

- [x] Trace creation, editing, deletion, persistence, and playback for static playlists.
- [x] Trace rule evaluation, refresh, persistence, and playback for dynamic playlists.
- [x] Check existing tests and run focused verification for suspected defects. Focused core tests passed: 7 `PlaylistEntrySmartTest` and 41 `QueueManagerTest`; neither covers the reviewed UI count, duplicate-name, or delimiter paths.
- [x] Report only confirmed findings with severity, file references, and any verification limits. Static add order is noted as an unresolved product expectation because the docs do not promise playlist ordering.

# Idle playback artwork bug (2026-10-15)

- [x] Reproduce it yourself on the matching surface via the driver skill (Non-negotiables), even when a debug or instrumentation protocol says to ask the user to reproduce. Ask the user only with a stated, specific reason the control surface cannot reach the target, and only after driving it as far as it goes. If it won't reproduce directly, synthesize the trigger, tighten conditions, or instrument until it fires. Attempted on the Android emulator with 22 tracks, screen-off playback, and song transitions. The blank-list symptom did not occur there; the user's phone is not connected.
- [x] Binary-search the cause. Form the candidate hypotheses, then rule them out until one survives. Seed them with `how` over the affected subsystem and the **why** skill for regression history. Each pass, take the split that cuts the most remaining problem space, get runtime evidence, eliminate. When program state is unclear, add instrumentation or logging and read it as the code runs. Don't guess. Drive a long or stubborn hunt with Claude Code's `loop` skill. Confirm the surviving *mechanism* with runtime evidence before the step-3 architect/interrogate fan-out. Service StateFlow polling does not replay old tracks; `MainActivity` did issue an artwork request on every playback tick and accepted late results. The independent list-image path yielded no confirmed failure on the emulator.
- [x] Plan the fix. If it crosses a function boundary, `architect` first. Delegate implementation to a subagent using your configured bug-fix model (default in poteto-mode's Models section) with a specific scope. Scoped to `MainActivity` artwork requests and callbacks; no function signature changed.
- [x] Verify on the same surface. The original repro now passes. "Inconclusive" or wrong-surface is not a pass. Flag it. Unit tests show branch behavior, not bug absence. `:app:testDebugUnitTest` and `:app:assembleDebug` passed; the updated emulator displayed matching artwork after rapid Next actions and a screen-off song transition. Exact reported blank-list reproduction remains inconclusive on the emulator.
- [x] Stage the commits so the failing repro lands before the fix in git history. See the **tdd** skill for the failing-test-first cadence when the bug has a cheap local test path. Skip it when the test would be expensive, integration-heavy, or unclear. Skip: no cheap JVM test can drive `MainActivity` and Coil; the app has no Robolectric dependency, and the emulator baseline did not reproduce the reported blank-list symptom. No commit requested.
- [x] Run **Opening a PR**. Skip: this is a local bug fix on `master`; no PR target was requested.

# UX review fixes (2026-10-13, from tasks/ux-review-2026-10-13.md)
Branch `ux-review-fixes`. Batches 1-6 done and checked on the phone (S25, 3.27.0 release build installed as an update, library kept): commits 91807796, 0271b56b, d0550dda, 3eef3906, e55be42b, ad796260. Signing gate passed (installed and release certs both B5:75:7A:...).
Found while doing batch 6: with no current track and shuffle off, Play calls `getNextTrack()` from index 0 and starts the SECOND track (`MainActivity.onDockPlayPauseClicked`). Up Next shows that honestly; the Play behaviour itself is not changed. Needs a decision.
Verify each batch with unit tests, `./gradlew :app:assembleDebug`, and a check on the phone (S25, real library). Run the reviewer on batches 3, 4 and 6. Proposed: one commit per batch on a branch (we are on `master`); this needs the user's OK.

Gate before the first install on the phone: the installed 3.27.0 is a release build (not debuggable), with 8,117 tracks scanned and analysed. Compare the signing certificate from `./gradlew :app:signingReport` with the installed package's certificate (`apksigner verify --print-certs` on the pulled APK). If they differ, stop and ask. Never uninstall and never clear data (see lessons.md).

Decisions (user, 2026-10-13): D1 "24-Bit Lossless"; D2 option A (list order + Shuffled chip + Up next line); D3 #FFD700; D4 drop "Hi-Res Edition".
- D1 (batch 3): one name for the 24-bit 44.1-88.2 kHz tier, used in all four places: library pill, song page, the Audio Quality category title (`Constants.TITLE_CD_EXT_QUALITY` = "Studio Quality", which also produces the "SQ" tile) and About. Proposal: "24-Bit" (pill "24-BIT", expanded "24-BIT LOSSLESS", category "24-Bit Lossless"). The Hi-Res tile badge becomes "HI-RES" / "LOSSLESS" instead of "24-BIT" / "STUDIO".
- D2 (batch 6): Queue tab while shuffle is on. Option A: keep list order, add a "Shuffled" chip and an "Up next: <title>" line in the header (row numbers stay list positions). Option B: render the list in play order while shuffle is on (drag-reorder then edits the play order).
- D4 (batch 2): drop "Hi-Res Edition" from the drawer, or keep it.
- D3 (batch 7): the brand gold. Proposal: #FFD700 for accents and active states, with #FFB300 kept only as a DR tier color.

## Batch 1: contrast (#1, #2)
- [x] `dr_high` is used for text (`TagUIUtils.getDRScoreColor`) and by `shape_background_dr_high.xml` via the deprecated `getDRScoreBackgroundColor`, which has no callers. Change it from #2E7D32 to a green of at least 4.5:1 on #1E1E1E (#4CAF50 measures 6.74 / 6.00 / 5.02:1 on #121212 / #1E1E1E / #2C2C2C).
- [x] `dr_none` #BDBDBD: 9.97:1 on #121212, no change.
- [x] Replace text uses of #888888 (13), #757575 (6) and #666666 (2) with one muted color of at least 4.5:1 on #2C2C2C (#9E9E9E, 5.21:1). Change only text colors; leave borders and icon tints alone. Add it as `Text.Muted` in `MusicMateDesignTokens` and reference it.
- [x] Phone: library DR labels, queue durations, server status lines. Screenshot before and after.

## Batch 2: copy and labels (#11, #12, #13, #16)
- [x] `StringUtils.formatDuration` (core): fix singular and plural in the current capitalized style: "1 Hr", "2 Hrs", "1 Min", "1.0 Day". "Days of Music" was chosen on purpose (`MainActivity.java:670`). Add a core unit test that covers the singular, plural, under-1-hour and over-48-hour cases. There are 16 callers in app/core, and no other module uses it. Check that none of them parses the text.
- [x] Tech Info (`TagsTechnicalPage.kt:263,421`): "ALL TAGS COMPLETE" and "High resolution" / "Standard" / "Low resolution".
- [x] Tag editor dock: the "Format" button (`action_reformat` -> `TagsEditorFragment.doFormatTags`) fixes garbled Thai text and tidies the case and spacing of tag text. Add a new string "Tidy Tags" for that button; `btn_format` itself is used only there.
- [x] Drawer: drop " • Hi-Res Edition" (`MainScaffold.kt:232`), per D4.

## Batch 3: one name per quality tier (#6), needs D1
- [x] Before renaming, list every `contains(` / `equals` match on tier strings. Several places pick a tier or color by searching the label text: `AudioPresentation.tier(raw)` ("Studio", "Hi-Res", "Lossless"), `FolderListItem` (category title), `NowPlayingPage.kt:121` (grade name), `TagRepository.java:483,556` and `TagUtils.java:368` (`TITLE_CD_EXT_QUALITY` as a search keyword). Make each match use the tier itself or the constant, not the display text.
- [x] `AudioPresentation.Tier.PCM24` labels and `Constants.TITLE_CD_EXT_QUALITY` per D1.
- [x] `FolderListItem.kt:187-188`: the Hi-Res tile badge uses the same tier names.
- [x] Grep for other hard-coded tier strings ("24-BIT STUDIO", "STUDIO", "HI-RES LOSSLESS") and route them through `AudioPresentation`.
- [x] Unit test: each tier maps to its labels, and each new label maps back to the same tier through `tier(raw)`, so a rename that breaks detection fails the test.
- [x] Phone: library pill, song page, Audio Quality tiles, console badge.

## Batch 4: console states (#15 idle part, #17)
- [x] With no track loaded, hide the quality badge and the empty pill (`FullscreenStudioConsole.kt`). Show "N tracks queued" instead of "MusicMate / High-Fidelity Studio Audio".
- [x] Music Center idle: first read what Play does when no track is loaded but the queue is not empty. If it starts the queue, change "Ready to play / Select a song" to "N tracks queued · press Play".
- [x] Console Shuffle and Repeat descriptions include their state, matching Music Center (`FullscreenStudioConsole.kt:752,836`).

## Batch 5: layout (#8, #10, #14)
- [x] Queue chip row: already had a fading edge (`QueuePage.kt` CompactSourceDeck); review finding #8 was wrong. Duplicate count left as is.
- [x] Song page Play button: move it off the artwork, next to the title, as a 48dp button. Keep long-press for Play Next.
- [x] Output picker: give the app list a fading bottom edge, or a height that ends on a whole row.

## Batch 6: queue under shuffle (#15), needs D2
- [x] Per D2, in `QueuePage.kt` / `QueueState.kt`, using the play order from `QueueManager` (`shuffleOrder` / `shuffleIds`).
- [x] `QueueManagerTest`: a test that the play-order view matches `upNext()`.
- [x] Phone: with shuffle on, the console's Up Next matches what the Queue tab shows next.

## Batch 7: tokens (#5, #4, #3, #9), one file per commit
- [x] Theme already reads MusicMateDesignTokens (`MusicMateTheme.kt:41-52`). Gold per D3: accents use Brand.Gold (6986d49f, reviewer PASS; follow-up b8596356). Amber kept for DR 8-12, warnings, VU needles, Studio Console (now Brand.GoldAmber).
- [x] Guard: `ColorLiteralBudgetTest` (fbdf468c), per-file budget that only goes down; proved failing on an added literal. Regenerate with the scratchpad budget.py or by hand after each migration.
- [x] Migrated (values unchanged): QueuePage 72c1f058, MainScaffold 2b4ab384, FullscreenStudioConsole 7bcd31f4. New tokens: Brand.GoldContainer, Brand.GoldOutline, Status.Positive. Budget 861 -> 745.
- [x] Remaining files: context-based pass (borders, surfaces, accent gold) over 24 files, one commit each, reviewer PASS. Budget 745 -> 668. The rest are identity/data colours (tiers, sources, DR, meters, gradients) left literal on purpose; migrate only with a named role.
- [x] #4/#3/#9 in 3bbb1036, phone-checked: sentence text 11sp, caps badges >= 10sp, 48dp side-by-side buttons (playlist names wrap to 2 lines), gold seek bar, volume percent, console Up Next room.
- [x] Play fix follow-up: f73daa44 + 8cd25215 (first playable track on smart sources; reviewer CHANGES addressed).

## Not planned
- #7 PLAY NEXT pills: check them on the phone while something is playing (this changes the playback state, so ask first), then decide.

# Release 3.26.0 (2026-10-10)
- [x] DLNA on Android 17: request ACCESS_LOCAL_NETWORK before the media server starts. Phone (SM-S931B, Android 17): permission denied = 258 SSDP `sendto EPERM`; granted = 0, SSDP bound on 1900, HTTP on 192.168.1.51, LAN devices discovered; system permission dialog shown on launch with auto-start.
- [x] USB DAC hi-res output replaces USB bit-perfect; Next in Music Center fix (see "USB DAC hi-res output" below).
- [ ] Listener check still open: a hi-res track over Bluetooth.

# Release 3.25.0 (2026-10-05)
- [x] Native USB bit-perfect playback (Android 14+): opt-in mixer bypass, exact decoded format matching, unity gain, ReplayGain bypass, and status badges.
- [x] Dock and Music Center output label alignment: both local playback surfaces consistently display the active resolved audio device name.
- [x] Verified on emulator: instrumented settings test passed, library scan and audio playback functional, settings toggle verified. Core and app unit tests pass.

# Release 3.24.1 (2026-10-04)
- [x] Fixes: DSD DR via FFmpeg (emulator: generated DSD64 DSF measured DR8, previously no DR), DR rounding in Now Playing/Console (emulator: console DR 13 = list DR13), console Up Next (shows Afterglow after Adagio Cantabile), mode-aware tips, Off/Track/Album labels on one line. Core and app unit tests pass.
- [ ] Phone check of DSD analysis on the 6 real DSD tracks (phone disconnected during the fix).
- [x] Paused row shows pause bars while the dock shows Play: intentional (status glyph vs action), no change.

# Docs reorganization and User Guide (2026-10-04)
- [x] Move docs with git mv (names unchanged): docs/ gets USER_GUIDE, MUSIC_QUALITY_GUIDE, screenshots; docs/technical/ gets TECHNICAL, DESIGN, UI, ECOSYSTEM, PLAYBACK_ARCHITECTURE, WEBSOCKET_API, WEBUI, PERFORMANCE, NETWORK_RESILIENCE; docs/archive/ gets superseded CHANGES_SUMMARY (removed HttpCore), NIO_WEB_ENGINE_10_10 (marked superseded), lib-review (2026-07). Root keeps README, CHANGELOG, CONTRIBUTING, RELEASE_NOTES (release workflow reads it).
- [x] Script: rewrite relative links after the move and check every Markdown link and image in the repo resolves.
- [x] Rewrite USER_GUIDE from the user's view, by task: getting started, browsing, playing and the queue, choosing a player, streaming and web remote, song details and tags, organizing files, quality badges, settings, troubleshooting. Check each behaviour on the phone while writing.
- [x] Capture extra screenshots for the guide (menu, queue, tag edit form, settings, Studio Console) after analysis finishes, same crop/resize script.
- [x] Verify links and images (check_links.py: 0 broken before and after the move), review, commit (no push).
- [x] Retake all README and User Guide screenshots on the emulator with a demo library of freely licensed music (CC0 / Creative Commons, covers free to use), so no copyrighted cover art or personal library is published. Current shots are from the user's phone (user decision 2026-10-04: keep as is until then).
  - [x] tools/demo-library: generate original music (numpy synthesis, varied dynamics), covers (CoreGraphics), FLAC 16/44.1, 24/88.2, 24/96, 24/192 and ALAC 24/48 via afconvert, tags and art via mutagen. Invented artists only.
  - [x] Emulator: replace old /sdcard/Music/MusicMate Demo, reinstall signed 3.24 build, grant access, scan, wait for analysis.
  - [x] Retake the 12 screenshots (player picker stays from the phone: emulator cannot see network renderers, and the shot has no cover art or library), crop, check each image.
  - [ ] Commit and push.
- [x] DR mismatch: Studio Console / Now Playing show dynamicRange truncated ("DR 13" for '39) while lists show drScore rounded ("DR14"). Use one field everywhere.
- [x] Library tips card says "Long Press: Long-press a song for batch editing", but in the default Play tracks mode a long press opens the song details; batch selection needs the Edit tags mode. Make the tip follow the tap mode.
- [x] Studio Console "UP NEXT" showed the playing track (Adagio Cantabile) while the queue's next was Afterglow (emulator, 2026-10-04).
- [x] "Don't show these tips again" kept the tips on screen until restart (GestureHintBanner had no state; callers pass no onDismissAll). Fixed with remembered dismissal; verified on the emulator queue tips.
- [x] Settings: "Album Gain" button text wraps onto two lines at default font size.

# Scan analysis phase (2026-10-04, found during README screenshots)
- [x] Deep scan (DR / extras for every track) runs inside the expedited ScanAudioFileWorker with no progress: header stays at "Scanning: 8302/8302 files" for hours. Job scheduler stops the job on timeout (observed STOP ... timeout after ~10 min, next run capped ~5 min) and WorkManager reruns the whole worker, repeating the folder walk and file check before resuming DR. Fix: separate DR worker chained after the scan that resumes from tracks without DR and reports "Analyzing: x/y". Verify on the phone, including a stop and resume.
- [x] Fixed: AnalyzeTracksWorker (unique work MusicAnalyzeWork) runs after the scan, cancelled by any new scan. Phone: header "Analyzing: x/5387 tracks"; forced timeout (cmd jobscheduler timeout -n androidx.work.systemjobscheduler) at 22:13:26, new run 15 s later at 34/3428, no scan job ran.
- [x] Parallel analysis (min(4, cores/2) threads) and ALAC temp file in cache. Phone: 1 thread 1.79 tracks/s overall (2.3/s on the same remaining tracks); 4 threads 5.1 tracks/s (630 tracks in 123 s), 428% CPU, PSS 420 MB, skin 42 C (moderate), no crashes.
- [x] DR badge color used truncation while the label rounds; color now rounds too.
- [x] DSD tracks never get DR (no Android DSD decoder: "Failed to initialize audio/dsd"), so every analysis run retries them. Decide: mark as not analyzable, or analyze via FFmpeg.

# README for users (2026-10-04)
- [x] Move technical README sections (architecture, SonicNIO, performance baseline, tech stack, developer notes) verbatim into TECHNICAL.md.
- [x] Rewrite README from the user's view: what it is, screenshots, features in plain words, install from GitHub, getting started, typical setup, links.
- [x] Screenshots captured on the phone (library, Music Center, player picker, song details, server); Samsung ignores demo mode, so the status bar is cropped off (103 px) and images resized to 540 px wide with a CoreGraphics script. Library shot shows "Scanning" and some DR values missing; retake after the full scan.
- [ ] Check every link and image path resolves; review on GitHub after push.

# Hotspot renderer discovery (2026-10-04)

- [x] Reproduce: hotspot on next to Wi-Fi; /proc/net/igmp showed SSDP joined on wlan0 only for 2.5 min (same pid); stopping/starting the server added swlan0.
- [x] Root cause 1: UPnP binds interfaces at startup; restart only on primary-IP change, which stays the Wi-Fi IP. Fix: interface signature; restart on change (hotspot/network events + 30 s tick), deferred while casting. Verified: swlan0 joined 31 s after hotspot on, no app restart.
- [x] Root cause 2: player list filtered renderers to the primary IP's subnet. Fix: accept any bound interface subnet. Verified: HiBy 10.221.118.217 listed (user).
- [x] Root cause 3 (found by step 2 failing: off/on lost ALL renderers): UpnpServiceImpl.shutdown() shuts down its configuration's executor; the hub reused one configuration, so a restarted stack never processed discovery replies. Fix: Supplier of a fresh configuration per stack (main, jetty, undertow modules). Verified with logs (same pid): off -> Verox re-added 1.3 s after restart; on -> HiBy R3 + Verox re-added; user confirmed both in the picker.
- skip: failing-test-first commit; no cheap local test (needs real interfaces); verified on device instead.

# Reliability review (2026-10-04)

- [x] Background tasks: MusicMateExecutors guards every task (Exception and LinkageError logged instead of killing the app); double-checked singleton fixed.
- [x] NIO request handler also catches LinkageError (e.g. ExceptionInInitializerError) and answers 500.
- [x] Media server: wake lock (4 h timeout) renewed every 30 s while RUNNING; lock methods synchronized; periodic discovery, renderer sync monitor and position poll chain survive exceptions.
- [x] Local playback: onPlayerError skips to the next track (once per bad item), stops after 3 failures in a row. Verified on the emulator with a corrupt file.
- [x] Main-thread DB audit: debug builds now reject main-thread Room queries (release still allows them as a safety net). Emulator sweep of launch, drawer screens, play, preview, editor, Tech Info, Music Center and search found 5 violations in 3 places, all fixed: editor Artist and genre-library lists (now loaded on IO), servers' fallback QueueManager (now lazy), shared QueueManager load at service start (background when constructed on the main thread; whenLoaded() publishes queue, shuffle and repeat). Final sweep: 0 violations.

# Genre / mood / style / origin presets (2026-10-04)

- [x] 1. Presets without "/" (single names); playlist rules use the same names; a shared vocabulary maps legacy values (e.g. "R&B, Soul", "Chill / Relax", "US/UK") to the new names on read and in matching.
- [x] 2. Playlist rules match value by value (split multi-value genre, case-insensitive, via the vocabulary).
- [x] 3. Dropdown opens with the full list and the current value ticked; filters only after typing; no unrelated fallback.
- [x] 4. Presets first, then "In your library" without near-duplicates; "Clear" in every field.
- [x] 5. One-line hint per field; tidy overlaps (Origin list; Style entries duplicating genres).
- [x] Tests (343, 8 new); emulator: dropdown opens full list with Clear and hint; legacy files tagged "Alternative/Indie" and "R&B/Soul" scan as "Alternative" and "R&B"; Personal Essentials includes the legacy Alternative track. Editor save also normalizes. Style "Ambient" kept (used by playlist rules); "Acoustic" style removed.

# Emulator review fixes, 13 items (2026-10-04)

- [x] 1. Search box does not auto-focus / open the keyboard on launch or return.
- [x] 2. System Access screen has a way back (and continues once storage is granted).
- [x] 3. Folder dialog: opaque; readable paths; clear "Add folder"; visible remove per folder.
- [x] 4. Drawer: "System Access" not truncated by its badge.
- [x] 5. Audio Quality: header counts categories; every category has art.
- [x] 6. Similar Tracks empty state says no similar songs were found.
- [x] 7. Playlist cover badges do not break or clip words.
- [x] 8. "Audiophile Sanctuary (DR12+)" only includes tracks that meet its rule.
- [x] 9. Artists/Genres show distinguishable placeholders.
- [x] 10. Settings switches are visible when off.
- [x] 11. One name for the fullscreen console.
- [x] 12. Server tab: neutral stopped state, consistent wording.
- [x] 13. Queue header icons readable/labelled and on-theme; source chips show they scroll.
- [x] Tests (335), release build, emulator check of each item, changelog, commit. Item 8 root cause: silent audio stored DR as Infinity (~9.2e16), which passed DR12+.

# WAV tags and NPE audit (2026-10-04)

- [x] Find why WAV embedded tags/cover art are not read. Not release-only (debug same). Genre: JThinkReader replaced the ID3 genre with an absent TXXX:GENRE. Cover: MediaMetadataRetriever ignores ID3 pictures in WAV.
- [x] Fix the demonstrated cause; verified on the emulator (debug DB: Classical/Folk; release: 4 genres, gold/green covers, Tech Info 100%, MP3s unchanged).
- [x] NPE audit: getPackage (WavTag, OkHttp) fixed earlier; JustDSD not shipped; track-getter dereferences reviewed (13, 2 guarded + 3 container titles); Kotlin !! (3, QR dialog race fixed); system services and listFiles/getParentFile chains reviewed.
- [x] Fixes committed; 328 JVM tests pass (no existing reader tests; verified on emulator instead).
- [x] Release 3.23.2 (code 144): optimized APK verified on the emulator (scan, 4 genres, WAV covers, Tech Info, no crashes). The phone was not connected, so the phone streaming/UPnP gates were not rerun; streaming code is unchanged since 3.23.1.

# Release 3.24.0 (2026-10-04)
- [x] Root cause of "GitHub APK will not install": every release since 3.21.0 published app-release-unsigned.apk; the phone reports INSTALL_PARSE_FAILED_NO_CERTIFICATES for the 3.23.2 asset.
- [x] Release key generated outside the repo (~/.config/musicmate/musicmate-release.jks, SHA-256 b5757a1e...1d29ee). Gradle signs release builds from MUSICMATE_KEYSTORE* env vars; CI decodes the keystore secret, verifies the signature and fails without secrets.
- [x] Local signed build verified: apksigner shows CN=MusicMate, code 145, 3.24.0.
- [x] Secrets added; tag v3.24.0 pushed; workflow run 37208736621 succeeded.
- [x] Published MusicMate-v3.24.0.apk verified: CN=MusicMate, SHA-256 b5757a1e...1d29ee, code 145.
- [ ] Install on the phone after the one-time uninstall (user decision: wipes library data).

# Release 3.23.1 (2026-10-04)

- [x] Version 3.23.1 / code 143; changelog and release notes.
- [x] 328 JVM tests; optimized APK on the phone: startup clean, Tech Info opens, 352/352 streaming requests with full-WAV SHA-256, UPnP description 200, no crashes.
- [x] Tag v3.23.1, push master and tag atomically, verify CI asset/notes and the shipped DEX (FFmpegKit members kept). Published; all six previously missing FFmpegKit members present in the CI APK.
- [ ] Follow-up: 3.23.1 still crashes scanning WAV files (fixed in bf56925e, unreleased); WAV embedded tags/art not read (to investigate).

# Song preview / tag editor UX fixes (2026-10-04)

Crash fix committed separately (96ea1785, FFmpegKit keep rules). Remaining review items:
- [x] 1. Save reflects pending edits on both preview and editor (editor typing lights it up; dim when nothing to save).
- [x] 2. Change Cover reads as a button (add-photo icon).
- [x] 3. Selection mode shows "N Selected" (Move/Convert/Remove/Select All moved to overflow). Correction: the long-press label was already right; the review wrongly assumed listener tap mode, but this phone uses edit mode.
- [x] 4. Editor form fills the space above the keyboard (adjustResize + overlap-only IME padding) and restores after it closes.
- [x] 5. "Year" label reads "Year / Date".
- [x] 6. Bottom panel padding/margins tightened (modest). User decision (2026-10-04): keep Organize/More in the editor.
- [x] 7. Discard and Remove-art dialogs: opaque dark panel, light Cancel, red destructive button. Remaining: a brown band over the button area is drawn above the app's views (persists with the panel painted solid); it existed before.
- [x] 8. Back from the editor returns to the preview; Discard resets text edits in place (other pending drafts keep close-and-discard).
- [x] 9. Section headings use the app's gold accent.
- [x] 10. Copy Diagnostics is a quiet text button.
- [x] 11. "Reload Tags", "Save Art", "Remove Art" (red); icons dropped so labels fit.
- [x] 12. Tech Info headings: Tag Check, Loudness (ReplayGain), Embedded Cover Art, File Location, All Embedded Tags, Audio Stream (FFmpeg).
- [x] Build, 328 unit tests, phone check of each item (test edits discarded, no tag writes), changelog, commit.

# Main screen: single Play button (2026-10-03)

- [x] Replace the Play + Shuffle pills beside the search stats with one Play pill; shuffle stays in the now-playing controls.
- [x] When shuffle mode is on, start results playback at a random track (shuffle mode keeps the starting track first).
- [x] Remove the unused shuffle parameter from onPlayResults/playCurrentResults; add a view-model test (10/10 MainViewModelTest, UiLayoutPolicyTest pass).
- [x] On-device check (optimized build signed with the phone key, data kept): one Play pill shows; shuffle off starts at the first result; shuffle on gave three different random starts. Shuffle was turned back off and playback paused.
- [x] Main-screen UX fixes, tested on the phone: opaque mini player, solid scroll-to-top button, marquee only while playing, and compact badges at large text (duration inline at 1.3x). 109 app unit tests pass.
- [x] Changelog/UI.md updated and committed.
- [x] Follow-up, tested on the phone: NEW/DL badge becomes a dot on list artwork (spoken label kept), menu mark on the logo, equaliser icon for Music Center, "Days of Music" with playtime before size, and singular "1 Track"/"1 Song".
- [x] Library size removed from the main header (kept for search results); About shows "22.9 Days of Music • 286.68 GB" and includes it in the share text. Verified on the phone.

# SonicNIO external server research (2026-10-03)

## Release 3.23.0

Objective: publish the retained changes as 3.23.0 / version code 142, push the code and tag, and verify the GitHub release asset. User confirmed retained changes only; lazy FLAC workspace allocation and the combined parser/copy experiment stay excluded.

### 1. Lock release scope and preserve evidence
- [x] Inspect the existing tag-triggered release workflow and preserve the experimental candidate.
- [x] Exclude both held experiments from active production code and their experiment-specific tests; retain archived sources/results.
- [x] Set version 3.23.0 / code 142 and draft CHANGELOG.md and RELEASE_NOTES.md.

### 2. Resolve optimized APK startup failure
- [x] Reproduce the failure in the correctly signed optimized APK without uninstalling or resetting app data.
- [x] Identify the missing reflected String constructor, datatype generic signatures and CSV generic signatures through diagnostic logs and R8 mapping.
- [x] Add targeted jUPnP consumer rules and retain warning/error logs in normal release builds.
- [x] Confirm the latest normal release build succeeds; inspect mapping/DEX for retained reflective members and generic signatures.
- [x] Install a scratch-signed copy of that exact optimized APK, start a fresh process and confirm UPnP initializes without listener shutdown.
- [x] If startup fails, capture the retained error, update this plan and fix the demonstrated cause before proceeding. (Startup passed; the CSV-signature build is runtime-clean for UPnP.)

### 2b. Fix jaudiotagger reflection in optimized builds (user approved for 3.23.0)
Found via newly retained error logs: ASF chunk readers fail `Class.newInstance()` (constructors removed) and ID3 frame bodies are looked up with `Class.forName("org.jaudiotagger.tag.id3.framebody.FrameBody" + id)`, but R8 renamed or removed them (only 26 survive, renamed). 3.22.0 shipped the same configuration.
- [x] Reproduce: ASF reader instantiation errors observed at runtime (33). ID3 confirmed statically: only 26/108 frame bodies survive, renamed. A phone MP3 tag read was not triggered (reads need a library scan or the tag editor), and the library has no WMA files, so synthetic files were not added.
- [x] Add targeted keep rules in library/jaudiotagger-android/consumer-rules.pro for every reflective lookup (frame bodies by name and copy constructor, ASF chunk readers).
- [x] Rebuild release; verify mapping/DEX keeps the original frame-body names and constructors; rerun JVM suites. (108/108 names and all constructors kept; ASF readers keep `<init>()`; 219 JVM cases pass.)
- [x] Reinstall (data preserved) and confirm no jaudiotagger reflection errors in the scenario that produced them. (Zero errors.)

### 3. Prove retained-only release correctness
- [x] Pass core, UPnP and Room JVM suites: 219 cases, zero failures/errors.
- [x] Pass all three Android Room index/migration cases.
- [x] Pass the optimized APK's full converted-audio SHA-256 check and all 352 measured HEAD/header/seek requests. (CSV-fixed APK; rerun on the final APK.)
- [x] Verify UPnP description and representative control actions, including ContentDirectory and ConnectionManager, on the optimized APK. (CSV-fixed APK; rerun on the final APK.)
- [x] Confirm the installed app version, process identity and APK provenance; preserve live app data and database version 3. (Indirect: install -r, firstInstallTime unchanged, Browse reports 8,289 tracks; run-as is unavailable on the release build.)
- [x] Repeat startup, streaming, UPnP and provenance gates on the final APK with the jaudiotagger rules. (352/352, SHA-256 OK, 9 SOAP actions 200, installed hash matches.)

### 4. Finalize documentation and review the commit
- [x] Archive successful and failed release checks, APK/source hashes and descriptive latency results in the release report.
- [x] Add the final release scope and verification results to PERFORMANCE.md; distinguish historical candidate comparisons from this release's correctness check. Claim no new throughput or Netty advantage.
- [x] Finalize changelog/release notes, including the reflection fix and held changes; verify release workflow uses the notes.
- [x] Stage only intended code, tests, documentation and curated evidence. Exclude caches, private database fixtures, signing keys and scratch APKs.
- [x] Review the staged diff against the previous release/default branch, check archive integrity and run git diff --check. (Archived CRLF CSVs left untouched because they are hash-pinned.)

### 5. Publish and verify
- [x] Create the release commit and annotated v3.23.0 tag after all runtime gates pass. (eb6fbed3; a clean worktree reproduced the tested DEX byte for byte.)
- [x] Push master and v3.23.0 atomically to origin; never force-push.
- [x] Verify GitHub Actions completes and the v3.23.0 release contains its expected APK and release notes. (Run 37135807310 succeeded. The CI DEX differs only in obfuscated names; it keeps all reflection targets, and the shipped APK passed startup, 352/352 streaming requests and the UPnP description check on the phone.)
- [x] Report the release URL, commit, version and verification totals.

Status: all runtime gates pass on the final APK (jUPnP + jaudiotagger rules). No release commit, tag or push has been made. Evidence redaction is done (device serial replaced, manifest hashes cascaded); the user chose to commit all evidence, including the large raw traces.

## Streaming metadata skipping

- [x] Preserve current sources/APK and add a streaming metadata API that skips unused payloads without weakening existing validation.
- [x] Verify malformed/truncated metadata, mixed APIs, seeking and byte-identical PCM; measure large-metadata CPU/allocation against preserved baseline.
- [x] Build and compare baseline/candidate on Android with reversed order and verified ranges; archive every result and restore candidate.

Result: [metadata-skip implementation/results](performance/sonicnio-metadata-skip-2026-10-03/REPORT.md). Two production files add/use a streaming-specific API; 228 core/UPnP tests pass. Host 1 MiB unused metadata allocation −99.17%; metadata time −96.13%, with no STREAMINFO-only timing improvement. Eight phone runs/5,632 requests pass; combined idle seeks median 16.738→10.255 ms (−38.7%), p95 19.600→12.248 ms, max 23.012→16.701 ms, direction repeats in reversed order. Candidate restored; retain this optimization candidate while earlier release holds remain.

## Idle seek attribution

- [x] Capture idle seek timestamps and scheduling/frequency traces on the current candidate, without changing server behavior.
- [x] Attribute slow requests using device monotonic clock snapshots; distinguish running, runnable and sleeping time.
- [x] Confirm the leading cause with a bounded stack/event measurement if needed, archive evidence and state the next justified change.

Result: [idle seek attribution](performance/sonicnio-idle-seek-2026-10-03/REPORT.md). Nine invocations verify 3,168 requests; four traces align 1,408 intervals with device clocks/client markers. Confirmation median 13.871 ms includes 8.818 ms worker CPU; CPU stacks identify FLAC metadata reading/CRC before headers. Traced medians differ from ~18 ms controls, so no optimization gain is claimed. Initial worker PID metadata is incomplete; validated confirmation inventories recover all eight workers. Next independently test unused metadata skipping, preserving validation/seek offsets/audio. Production unchanged, candidate restored, release holds remain.

## Room path index and Android validation

- [x] Preserve version-2 schema/APK/database evidence and implement a non-unique path index with migration 2→3.
- [x] Verify fresh creation and migration chains from versions 1/2, duplicate paths and listening-history preservation on Android.
- [x] Build/install the candidate after preservation checks; compare Android path lookup and controlled streaming contention without downgrading the live database.
- [x] Archive every measurement, validation and limitation; update the performance decision and restore the candidate.

Result: [Android index results](performance/sonicnio-path-index-2026-10-03/REPORT.md). Implemented migration/index; 226 correctness cases pass, live 8,289 tracks/704 history rows preserved exactly. Native lookup 51.3× faster, fixed-work worker CPU −98.2%, storage +1.164 MiB. All 2,112 requests pass; idle/indexed seeks ~18 ms versus busy unindexed ~5 ms, confirmed in reversed order. Retain as database candidate without streaming gain; existing release holds remain. Next profile idle/backoff delays. Candidate installed/restored.

## Busy-worker attribution

- [x] Verify device stack-capture capabilities and preserve the installed APK identity.
- [x] Capture bounded worker stacks and correlate their functions with the prior CPU trace.
- [x] Measure the identified path lookup on isolated database copies, without changing the live database.
- [x] Record evidence, instrumentation limits and the next controlled streaming measurement; verify production sources remain unchanged.

Result: [worker attribution](performance/sonicnio-worker-profile-2026-10-03/REPORT.md). 528 verified requests and three CPU-stack captures identify library scanning/SQLite path lookup. Phone DB copy has 8,289 tracks and no path index; host indexed lookup is 130–226× faster with identical results and +1.164 MiB storage. Scan completion confounds foreground/background comparison; Android benefit remains unmeasured. Next candidate: non-unique path index with preserving Room migration. Production/live schema unchanged; candidate restored; release holds remain.

## Seek-tail and memory profiling

- [x] Verify available Android tracing, GC and scheduling telemetry without changing streaming behavior.
- [x] Capture matched baseline/candidate workloads with profiling controls, preserve traces and validate bodies/APK identity.
- [x] Document attributable differences and limits, verify evidence and leave the candidate APK restored.

Result: [profile report](performance/sonicnio-seek-profile-2026-10-03/REPORT.md). All 1,408 comparative requests passed, five traces archived including pilot. Captured GC cycles 18 → 7; recorded suspension scopes ≤1.12 ms. PSS increase not consistently reproduced; unnamed pool workers dominate CPU and ±24 ms clock alignment prevents reliable request attribution. Release hold remains; production hashes unchanged, candidate APK restored.

## Lazy FLAC physical-phone comparison

- [x] Preserve baseline/candidate sources and build APKs differing only in lazy decoder allocation; verify connected device and workload.
- [x] Run interleaved, warmed-up phone comparisons with body/range verification, converted seeks, metadata-only latency and available memory/GC telemetry.
- [x] Analyze every run, preserve APK hashes/raw evidence and document limits; leave candidate source/APK restored.

Result: [phone report](performance/sonicnio-lazy-flac-2026-10-03/PHONE_RESULTS.md). Twelve external-only app runs plus six intrusive instrumentation-control runs, failed unpaced attempt preserved, and eighteen isolated ART samples. ART allocation −99.1%; app throughput −0.3%, median converted-seek run maxima +6.6%, sampled maximum PSS +14.1%. Pooled seek p99 improves; latency is mixed and exact app GC activity is unmeasured. Hold lazy decoder change from release pending profiling; candidate source/build/APK restored, no rollback performed.

## Lazy FLAC decoder scratch allocation

- [x] Defer FrameDecoder construction until the first audio read/seek, preserving metadata readiness and closed-state checks.
- [x] Verify metadata-only reads avoid scratch allocation, decoder reuse and byte-identical PCM/ranges with core/UPnP tests.
- [x] Measure isolated metadata allocation before/after and document results and remaining runtime limits.

Result: [PERFORMANCE section 14](../PERFORMANCE.md#14-lazy-flac-decoder-scratch-allocation), [full methods and evidence](performance/sonicnio-lazy-flac-2026-10-03/REPORT.md). Metadata probe allocation 1,057,392 → 8,752 bytes (−99.2%) in both host series; 222 core/UPnP tests pass. Audio workspace is still allocated once when decoding begins. Android follow-up is now complete in the phone report above; release recommendation is hold pending profiling.

## Streaming allocation audit

- [x] Inspect file, generated-audio and decoder allocation sites and their frequency.
- [x] Identify ownership-safe reductions and distinguish already measured savings from unmeasured candidates.
- [x] Record prioritized findings with source locations; do not change streaming behavior during this audit.

Result: [allocation audit](sonicnio-streaming-allocations-2026-10-03.md). First candidate is lazy FLAC frame-decoder initialization: metadata-only scanning currently creates two 65,536-element long buffers (1 MiB element storage). Other candidates include metadata skipping, seek workspace reuse, PCM chunk recycling and response-bound callbacks. No production changes or new performance claims.

## one-nio source review

- [x] Inspect a pinned one-nio revision, license, runtime/native requirements and benchmark scope.
- [x] Compare transfer, write queues, readiness handling and HTTP parsing with SonicNIO; identify Android-compatible ideas and measured-risk constraints.
- [x] Record source-linked recommendations and validation gates without changing production code or claiming unmeasured gains.

Result: [one-nio source review](sonicnio-one-nio-research-2026-10-03.md). Profile seek tails and selector backoff first; then independently measure a bounded selector-owned immediate-write experiment. Native transport replacement, body parser and shared buffers are deferred. No new benchmark results or production changes.

Context: encoding and metadata changes are recommended for retention; the combined parser/copy change is held from release pending investigation of higher phone seek maxima. Those changes remain in the working tree/test APK.

## Request framing and copy reduction follow-up

- [x] Preserve current source/APK baseline and establish parser benchmarks with small headers and large buffered bodies.
- [x] Remove redundant full-buffer/body copies; retain existing incremental header scanning and public parse behavior.
- [x] Validate repeated Content-Length and Transfer-Encoding combinations against RFC 9112, with fragmentation and socket regression coverage.
- [x] Measure copy-only and final variants independently, run core/UPnP tests and device checks, and document results and compatibility changes.

Policy: normalize repeated identical Content-Length values; reject conflicting/invalid lengths and TE+CL with 400 and close. Chunked remains unsupported. No broad parser replacement.

Result: [PERFORMANCE.md §13](../PERFORMANCE.md#13-request-framing-and-copy-reduction), [full report and evidence](performance/sonicnio-parser-2026-10-03/REPORT.md). 360 extraction and 24 host transfer samples; twelve interleaved phone runs after an adverse initial latency result; 219 unit/socket cases and seventeen Android framing probes pass. Staged 1 MiB allocation falls 66.8%. Bulk throughput stays essentially unchanged. Phone seek maximum rises 25.3% across both series, despite a lower median; retain correctness/copy savings without claiming an end-to-end speedup. Final APK installed and verified.

- [ ] Follow-up: profile the repeated Android seek-tail regression (read/parse, worker wait, metadata, first successful write, client/header timing and GC) before further throughput tuning. Empty-request micro timings also vary substantially; identify runtime effects instead of treating the favorable model as proof.

## Next experiment: one file-metadata read per response

- [x] Preserve the optimized-encoding baseline and add a constructor-focused comparison that works with both variants.
- [x] Read size/mtime together once per FileResponse; derive ETag and Last-Modified from that snapshot. Verify unchanged stable-file responses and fresh metadata after replacement.
- [x] Run interleaved host preparation/transfer measurements, core/UPnP regression tests, debug build and matched phone checks; retain only supported behavior and publish all results.

Scope: remove repeated filesystem metadata calls without a cross-request cache. Preserve the prior encoding optimization and streaming settings.

Result: [PERFORMANCE.md §12](../PERFORMANCE.md#12-file-metadata-snapshot-follow-up), [report and all evidence](performance/sonicnio-stat-2026-10-03/REPORT.md). Conditional/range preparation medians fall 76.7%/21.9%, with +144 allocated bytes per response. Phone seek median falls 14.679 → 9.539 ms, while the seek maximum rises 6.3%; throughput stays unchanged. Initial host tail regression prompted 18 additional confirmation samples; no consistent tail advantage is established. All 209 tests, build, body/metadata checks and installed-APK verification pass. Tested APK remains installed.

- [x] Inspect hella-http and yelmach/http-server source, project requirements and stated reuse licenses.
- [x] Compare concrete transfer, buffering, parser and event-loop implementations with current SonicNIO; identify useful patterns and compatibility/correctness constraints.
- [x] Record source-linked recommendations, implementation sketches and measurement gates; distinguish research from implemented or benchmarked gains.

Delivery: [source review and implementation proposals](sonicnio-http-server-research-2026-10-03.md), pinned to both reviewed revisions. Priorities: ETag/date/header allocation, framing-header validation and incremental scanning, then PCM buffer reuse only if profiling supports it. Research/documentation only; no new throughput or Netty advantage claimed.

# SonicNIO throughput experiments (2026-10-03)

- [x] Capture a repeatable current-code host baseline and preserve source/evidence before tuning.
- [x] Implement configurable, contention-aware file write budgets and bounded PCM batching; test bytes, partial writes, short/failing producers and cancellation ownership.
- [x] Compare repeated identical host workloads, including seek contention; retain supported defaults and run core/UPnP checks and debug build. Existing socket coverage checks HTTP overload with active WebSocket control.
- [x] Update PERFORMANCE.md with methods, measurements, decisions and device/Netty verification limits.

Result: PCM batching enabled; shared/solo file budgets retain 256 KiB defaults because 512 KiB/1 MiB experiments did not establish benefit. 99 complete host measurements, nine matched-content phone runs, exact byte/range checks and 162 core + 43 UPnP cases pass. Synthetic 4 KiB generated-body throughput median 169.054 → 1,070.370 MiB/s (6.33×); phone WAV duration 0.852291 → 0.830846 s, with no substantial throughput gain established. Some native host medians were 2.8–5.1% lower; preserve this limitation and require controlled profiling rather than claiming a native speedup. A discovered queue-space/partial-buffer race was diagnosed and regressed. Final APK installed and its SHA-256 verified. Details and artifacts: [PERFORMANCE.md §10](../PERFORMANCE.md#10-throughput-follow-up), [205 individual test results](performance/sonicnio-throughput-2026-10-03/TEST_RESULTS.md).

# SonicNIO performance documentation (2026-10-03)

- [x] Inventory design decisions, benchmark scripts, retained device measurements and individual JUnit results; identify missing evidence and comparison limits.
- [x] Create a durable performance document with concepts, implementation approach, methodology, per-test results, before/after and historical Netty comparisons, reproducible commands and outstanding measurements.
- [x] Validate totals, calculations, source links and documentation diffs; link the report from the existing streaming summary.

Delivery: [PERFORMANCE.md](../PERFORMANCE.md), [199 individual test results](performance/sonicnio-2026-10-03/TEST_RESULTS.md), four device JSON artifacts, normalized JUnit/repeat evidence, source/evidence digests and five archived session harnesses. Verified 35 suites / 199 passing cases, 40 repeat runs / 2,160 executions, before/after percentage calculations, local links and manifest digests. Documentation-only work; benchmarks were not rerun. Netty attribution, unmatched Wi-Fi and missing raw artifacts remain explicitly qualified.

# SonicNIO streaming reliability plan (2026-10-03)

Objective: improve playback continuity, seek responsiveness and bounded resource use using Grizzly's lifecycle, backpressure and monitoring patterns. Implementation and validation results: [sonicnio-streaming-2026-10-03.md](sonicnio-streaming-2026-10-03.md). Reliability/resource changes are implemented; the unexplained intermittent header failure and physical-renderer checks remain open.

Constraints: preserve ADR-036 selector ownership, serial WebSocket callbacks, exact response framing and non-blocking stop. Keep the existing file transfer path and bounded producer queue. No additional HTTP engine dependency. The earlier HiBy/MQA dropout remains an independent, unconfirmed report; these changes must not be presented as its fix without reproduction.

## Phase 1 — Establish evidence and diagnose connection closures
- [x] Add lightweight per-response lifecycle measurements: request/connection identifier, first successful write, last successful write, declared body length, body bytes sent, terminal outcome and close reason. Distinguish header bytes from body bytes and transport completion from playback completion.
- [x] Preserve producer failure causes in StreamingResponse; emit one terminal event per response and one close event per connection, including disconnect, eviction, shutdown and failure. Bound diagnostic storage and avoid per-chunk logging.
- [x] Measure selector-turn duration, worker queue depth/wait and generated-buffer occupancy. Use monotonic elapsed time for new measurements.
- [ ] Reproduce the known premature-close test failures (including midStreamDisconnect and stop_releases under suite load), capture fresh reports and fix the demonstrated cause with a regression before optimizing.
- [ ] Capture a device baseline: original FLAC/MP3, FLAC/ALAC converted to WAV, single and concurrent streams, seek during artwork/browse bursts. Record bytes received, transfer failures, first-byte/seek p50 and p95, CPU, memory and GC activity.

Acceptance: every exercised terminal path has a distinguishable outcome; diagnostics do not retain tracks or connections indefinitely; repeated lifecycle tests pass and observed failures have explained causes. A bounded repeat run is evidence, not proof that all races are eliminated.

## Phase 2 — Progress deadlines and fair generated writes
- [x] Separate header/body-read, handler, keep-alive, socket-write-stall and producer-stall states/deadlines. Update write progress only when bytes are transmitted; do not reset it merely because OP_WRITE fires.
- [x] Include parked producers in deadline evaluation without rearming OP_WRITE. Distinguish an empty producer queue from a full queue awaiting a slow reader; cancel and release resources exactly once on expiry.
- [x] Make stall limits configurable and choose defaults from baseline renderer behavior. Use monotonic elapsed time; healthy streams may run longer than any stall limit.
- [x] Apply a per-selector-turn byte budget to StreamingResponse.write(), initially matching FileResponse's 256 KB budget. Preserve pending buffer offsets, producer parking/wakeup and partial header writes.
- [x] Test a zero-progress writer, stalled producer, slow but progressing reader, producer wakeup races, long healthy stream and two concurrent streams plus a small response. Use controlled clocks/channel doubles or latches instead of timing-sensitive sleeps where feasible.

Acceptance: stalled work is reclaimed, successful slow playback survives, parked producers do not spin, response bytes remain identical, and a continuously replenished producer yields predictably.

## Phase 3 — Bound overload and protect active playback
- [x] Replace the unbounded HTTP worker queue with configurable bounded admission. Handle rejection on the selector with a self-delimiting 503 response; release request/response resources and handle shutdown races. Audit shared SerialExecutor/WebSocket submission so saturation cannot strand callbacks or suppress cleanup.
- [x] Audit artwork versus audio stream-slot admission and concurrent acquire/eviction. Prefer refusing excess new work over interrupting healthy audio; define explicit treatment for artwork and seek replacement requests, with bounded total connections/file descriptors.
- [x] Enforce an aggregate generated-audio buffer budget in addition to the existing 16 x 64 KB per-response queue. Include pending buffers and producer-held chunks; keep producer concurrency bounded and reclaim budget on all terminal paths.
- [x] Test request bursts, artwork while audio slots are occupied, concurrent admission, slow readers, overload during stop and WebSocket callback ordering/rejection. Verify excess work gets a controlled response and admitted playback continues.

Acceptance: queue, producer and buffer usage stay within configured bounds; overload cannot silently close a healthy audio stream or leak slots, descriptors or callbacks.

## Phase 4 — Measurement-gated optimizations
- [ ] Evaluate a bounded reusable pool for generated-audio chunks only if allocation/GC measurements justify it. Define ownership from producer through queue/pending write to release; test partial writes, cancellation and failure. Retain only if device comparisons show a useful improvement.
- [ ] Measure cold-file and slow-storage transferTo latency on the selector. Add a bounded file-reader fallback only if measured stalls harm other requests; workers read files, selector still writes sockets, and byte-range behavior remains exact.

Acceptance: any retained optimization improves the measured target without worsening transfer correctness, memory bounds or seek latency. Record a decision to defer when evidence does not justify added complexity.

## Verification and delivery checkpoints
- [x] After each phase, run relevant core socket/streaming/diagnostics tests; repeat only to investigate changes or unresolved races. Final coverage includes NioHttpServerTest, NioHttpServerFuzzTest, NioHttpServerSoakTest and ServerDiagnosticsTest, plus impacted UPnP tests and assembleDebug.
- [x] Review scoped diffs against main and run git diff --check. Keep existing unrelated task entries and worktree changes intact.
- [ ] Repeat the baseline device scenarios with tools/bench/stream-bench.sh and byte/hash verification for original/ranged/converted content. Exercise disconnect/reconnect, seek bursts and artwork/browse bursts; compare latency, CPU, memory, GC and failed transfers with the baseline under comparable network conditions.
- [ ] Verify playback on available real renderers; record unavailable renderer checks as outstanding. Network transfer success alone does not establish audible playback continuity.
- [x] Update DESIGN.md/ADR-036 and CHANGELOG for implemented behavior and limits; summarize results and deferred optimizations. Deliver each phase as a separately reviewable change.

Current verification: 156 core + 43 UPnP tests pass; 32-client soak passes. After an initial unexplained HTTP/1.0 failure, 20 trace-enabled and 20 ordinary socket-suite repetitions plus 5,000 focused close exchanges pass. Device original/converted bytes, FLAC/ALAC independent PCM references, MP3 ranges and artwork bursts pass. Keep the premature-close investigation open; defer pooling/storage isolation and broad renderer tuning until evidence is available.

# DLNA interruption when cover art loads; possibly MQA (2026-10-03)

- [x] Trace artwork requests and audio writes for shared blocking work or stream contention.
- [ ] Identify the affected track and verify its actual format/encoded rate (initially reported as 24/192; user now suspects MQA), then reproduce before choosing a diagnostic comparison.
- [ ] Once the cause is established, add a focused regression and implement the smallest supported fix.
- [ ] Run relevant tests/build, review scoped diffs against main, and verify runtime behavior where available.
- [ ] Record findings and any remaining physical-renderer verification.

User update: cannot locate the track now; it may have been MQA. Format and cause remain unconfirmed. Keep playback code unchanged pending reproduction; artwork appearing at the dropout is correlation, not proof of image-decoding contention.

Investigation: user confirmed HiBy renderer, started from MusicMate. `MediaServerHubImpl.createDidlLiteMetadata()` sends no albumArtURI on this route; `NioWebServerImpl.createSongResponse()` serves the original file for HiBy. Embedded-art decoding on the renderer is a hypothesis, not confirmed. `schedulePreloadNextTrack()` also immediately pre-warms up to 4 MB of the next track for HiBy; phone logs show that work at track start. Recent logs do not capture the reported dropout and show the selected HiBy unavailable. No playback code changed: need the affected track to distinguish renderer image decoding from host/network contention before choosing a fix. Browser artwork uses a separate worker for resolution/thumbnail decoding, though file writes share the selector and stream slots with audio.

# UX/UI improvements (review: tasks/ux-review-2026-10-02.md)

## Batch 1: P1 quick wins
- [x] 1. Drawer version from BuildConfig (MainScaffold.kt:220)
- [x] 2. Hide player app build number outside Diagnostics (PlayerNameUtils)
- [x] 3. Play / Play Next on song detail
- [x] 4. Play all / Shuffle for the current list and search results
- [x] 5. Track menu (Add to Playlist dropped: no single-track playlist add exists): Go to Artist, Go to Album, icon for Open in External App

- [x] Device check (S25): drawer v3.21.0, Play/Shuffle on "queen" (35 tracks, shuffled queue), Go to Artist (21 Queen tracks, Back clears), song page Play
- [x] Filter label: "Artist: Queen • 21 Tracks" (verified on device)

## Batch 2: P2 polish
- [x] 6. Music Center button icon
- [x] 7. Queue source chips: Replenish to header, fade edge, clearer counter, one count
- [x] 8. Server tab clients as rows, friendly browser name
- [x] 9. Output picker: "This phone" with live route
- [x] 10. Now Playing scrim and album line

- [x] Device check (S25): speaker icon, queue fade and no 20/20, Server CONNECTED list (Chrome browser), "Phone Speaker" in picker and mini player, Now Playing "Queen • The Works"
- [x] Scroll-to-top FAB centered (verified on device)
- [x] Screenshot references regenerated after inspecting renders (34/34 pass); found and fixed: 200% text pills, pills in selection mode, faded first letter
- [x] Volume slider matches seek bar family
- [x] Delete moved into song page More… (last, red, batch count); verified on device
- [x] Add to Playlist: track menu, song page More…, multi-select; persisted in custom_playlists.json; verified on device (create, add, duplicate skipped, survives restart)
- [x] Remove from Playlist + Delete playlist (long-press own card); verified on device, test playlist "UX Test" removed with it
- [ ] Flaky: NioHttpServerTest midStreamDisconnect / stop_releases fail under full-suite load, pass alone

## Verification
- [ ] Unit tests + assembleDebug; update screenshot references touched
- [ ] Install on device and re-capture each changed screen
- [ ] UI.md, USER_GUIDE.md, CHANGELOG Unreleased

# MQA display fixes

## Documentation and commit follow-up

- [x] Update DESIGN.md, UI.md, and CHANGELOG.md to describe the implemented MQA behavior. (DESIGN "Native MQA metadata presentation", UI.md MQA precedence, CHANGELOG MQA entries)
- [x] Stage only MQA changes and verify the staged source snapshot builds and passes app tests.
- [x] Commit the MQA fix and documentation; preserve unrelated working-tree changes. (85065968; confirmed 2026-10-02)
- [ ] Device: real MQA / MQA Studio files, track changes, compact and wide layouts, both player modes (mqa-display-fix-plan.md step 4)
- [x] Record reference images for MqaScreenshotTest: 5 of 8 recorded after inspection (audio details, badges compact/large text/wider, playback)
- [x] Fixed and recorded the last 3 (audio details 200% text, full screen default and 200% text): VU header one line, dial sized by width as well as height, dial lettering capped by dial size, FlowRow for the spec rows, shrinking output chip, fixed preview clock. All 8 MQA previews pass; the other 22 screenshot failures are the same stale baselines as before

Plan and verification: `tasks/mqa-display-fix-plan.md`. Implemented; 94 app tests and debug build pass. Eight previews visually inspected; screenshot references and remaining live-device matrix pending.

- [x] Add MQA/MQA Studio regression cases.
- [x] Share native quality labels and colors; prioritize MQA over generic PCM tiers.
- [x] Show encoded and original sample rates in player details and badge accessibility descriptions.
- [x] Verify compact/wide layouts, focused and app tests, debug build, screenshots, and scoped diffs.
- [x] Update UI/user documentation and record runtime verification limits.

# Full-Height Music Center (Eliminate Empty Black Gap Above Sheet)

## Status: 🟢 Completed & Verified

### Objective
Expand Music Center (`AudioHubSheet.kt`) to full height (filling available vertical space ~95-100% like Spotify and Apple Music), eliminating the empty black backdrop above the previous 65% clamped sheet.

### Checklist
- [x] **Task 1: Update `AudioHubSheet.kt` to Full Height**
  - [x] Remove hardcoded `sheetHeight = screenHeightDp * 0.65f` clamping.
  - [x] Set root content Column to `Modifier.fillMaxSize()`.
  - [x] Ensure `Box` container in `AudioHubContainer` uses `fillMaxSize()` for MODAL and PREVIEW presentations.
- [x] **Task 2: Verify Layout Behavior across Tabs**
  - [x] `NowPlayingPage.kt`: Verify album art flip container and controls expand gracefully with `.weight(1f)` without clipping.
  - [x] `QueuePage.kt`: Verify queue list takes full height without clipping.
  - [x] `MediaServerPage.kt`: Verify scrollable cards display properly with expanded space.
- [x] **Task 3: Unit Tests, Screenshots & Regression Testing**
  - [x] Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest`: All tests pass cleanly.
  - [x] Run `./gradlew assembleDebug`: Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
  - [x] Check git diff hygiene (`git diff --check` passes with 0 warnings).

---

# Fix Music Center Play & Next Controls (Items 1 to 4)

## Status: 🟢 Completed & Verified

### Objective
Resolve the 4 identified issues in Music Center -> Playback screen and underlying playback control architecture:
1. Permit playback start from Play & Next in `NowPlayingPage.kt` via `canStartPlayback` and `UiLayoutPolicy.playControlsEnabled`.
2. Dim Next and Previous button icons when disabled (`Color(0x66FFFFFF)` vs `Color.White`).
3. Differentiate user-initiated skips from natural looping on `RepeatMode.ONE` in `QueueManager.java` and `MusicMateServiceImpl.java`.
4. Prioritize queue order (`getCurrentTrack()` / `getNextTrack()`) over random selection when starting playback from the queue in `MainActivity.java`.

### Checklist
- [x] **Task 1: Permit Playback Start in `NowPlayingPage.kt` & `UiLayoutPolicy.kt`**
  - [x] Add `playControlsEnabled` to `UiLayoutPolicy.kt` and tests in `UiLayoutPolicyTest.kt`.
  - [x] Support `canStartPlayback` parameter in `NowPlayingPage.kt` and wire it in `AudioHubSheet.kt`.
- [x] **Task 2: Correct Disabled Icon Tint for Next and Previous in `NowPlayingPage.kt`**
  - [x] Apply `if (playControlsEnabled) Color.White else Color(0x66FFFFFF)` to Next icon.
  - [x] Apply `if (controlsEnabled) Color.White else Color(0x66FFFFFF)` to Previous icon.
- [x] **Task 3: Differentiate User Skip from Natural Loop on `RepeatMode.ONE`**
  - [x] Overload `QueueManager.getNextTrack(boolean forceSkip)` to bypass `RepeatMode.ONE` on user skip.
  - [x] Call `getNextTrack(true)` for explicit skip actions in `MusicMateServiceImpl.java`, while keeping `getNextTrack(false)` for natural completion.
  - [x] Add regression tests in `QueueManagerTest.java`.
- [x] **Task 4: Preserve Queue Order on Initial Playback in `MainActivity.java`**
  - [x] In `onDockPlayPauseClicked()`, query `qm.getCurrentTrack()` or `qm.getNextTrack()` when shuffle is off, falling back to random only when shuffle is on or no track is found.
- [x] **Task 5: Verification & Polish**
  - [x] Run `:core:testDebugUnitTest` and `:app:testDebugUnitTest`: all unit tests pass cleanly.
  - [x] Check git diff hygiene: `git diff --check` passes with zero warnings.

---

# Compact Smart Queue Header & Playlist Integration

### Objective
1. **Drastically reduce vertical space footprint of Queue header:**
   - Consolidate Toolbar (40dp) and Engine HUD (32dp) into a single 34dp header bar.
   - Replace bulky 52dp 2-line cards with ultra-compact 28dp micro-capsule chips (`[🖐️ Manual]`, `[✨ New]`, `[📥 DL]`, `[🧭 Discover]`, `[⏳ Rediscover]`, `[📋 Playlist ▾]`).
   - Remove the 40dp static intelligence info box (retain only transient error micro-banners).
   - Cut total header height from ~196dp to ~60dp (~68% reduction), maximizing visible track listing inside the 65% height bottom sheet.
2. **Support Load from Playlist:**
   - **Pattern A (Action): Instant Load/Append to Queue:** Add `PlaylistPickerDialog` to directly replace ("Play All") or append ("+ Queue") all playable tracks of any playlist (Smart or Custom) into the queue.
   - **Pattern B (Smart Source): Playlist as Auto-Refill Engine:** Add `QueueManager.Source.PLAYLIST` and `activePlaylistName` to continuously auto-fill up to 20 unplayed tracks matching the selected playlist.

### Checklist
- [x] **Phase 1: Ultra-Compact Queue Header Redesign (`QueuePage.kt`)**
  - [x] Merge Top Toolbar and Engine HUD into a unified 34dp bar with title, live jewel LED dot, track count/time, inline slots badge, and compact focus/clear/playlist actions.
  - [x] Redesign `SmartSourceDeck` into sleek single-line 28dp micro-capsules without subtitles.
  - [x] Remove static `SmartSourceInfoBanner` container; keep only conditional error micro-alerts.
- [x] **Phase 2: Playlist Selection & Loading Implementation**
  - [x] Create `PlaylistPickerDialog.kt` composable to display all available playlists from `PlaylistRepository.getPlaylists()`.
  - [x] Implement instant actions: "Play All" (replaces queue) and "Add to Queue" (appends).
  - [x] Add `Source.PLAYLIST` and `activePlaylistName` to `QueueManager.java` for continuous smart auto-refill from a chosen playlist.
  - [x] Connect `QueuePage.kt` Playlist chip & toolbar icon to open picker and switch active playlist.
  - [x] Add playlist button in `QueueEmptyState`.
- [x] **Phase 3: Verification & Polish**
  - [x] Run unit tests (`:app:testDebugUnitTest`, `:core:test`): all 102 unit tests pass cleanly.
  - [x] Verify build via `./gradlew assembleDebug`: clean APK generated.
  - [x] Verified `git diff --check`: zero warnings.

---

# Smart Queue UI/UX Overhaul: Audiophile Console & Visual Discovery

## Status: 🟢 Completed & Hardened

### Objective
Elevate the Smart Queue UI/UX from a plain utility list into an immersive, audiophile-grade smart console with tactile source switching, real-time refill HUD, cover-art thumbnails, audio fidelity badges, and clear visual queue zoning (Now Playing vs. Manual Play Next vs. Smart Refill).

### Checklist
- [x] **Phase 1: Audiophile Smart Source Deck & Engine HUD (`QueuePage.kt`)**
  - Replaced generic Material dropdown menu with a horizontally scrollable obsidian segmented pill deck for the 5 sources (Manual, New, Downloads, Discover, Rediscover) with distinct accent colors and jewel borders.
  - Implemented live "Engine HUD": pulsing status LED indicator (Emerald pulse for active auto-fill, Amber for refreshing, Slate for manual), slot count badge (`SLOTS: 18/20`), and manual refill button with haptic feedback.
  - Added frosted glassmorphic intelligence banner explaining the active smart algorithm and auto-refill rules.
- [x] **Phase 2: High-Fidelity Queue Items with Cover Art & Codec Badges**
  - Added 42dp cover-art squircle thumbnails with Coil `AsyncImage` and dark background fallback.
  - Added animated vector equalizer overlay and gold border/title accent for the active playing track.
  - Integrated compact audiophile quality badge (`QualityBadge`) for Hi-Res, DSD, 24-bit, and CD quality tracks.
  - Added subtle track provenance micro-badge (`NOW PLAYING`, `PLAY NEXT`, `SMART`) to distinguish user choices from smart auto-fill.
- [x] **Phase 3: Visual Queue Zoning & Rich Empty State**
  - Grouped and sectioned upcoming tracks visually with categorized dividers (`NOW PLAYING`, `UP NEXT · MANUAL PRIORITY`, `✦ SMART REFILL · [SOURCE]`).
  - Designed an audiophile empty-state graphic with quick-start action pills (`[✨ Try Discoveries]`, `[Browse Library]`).
  - Polished swipe-to-dismiss gesture with crimson gradient and haptic resistance.
- [x] **Phase 4: Verification, Lint & Build**
  - 117 unit tests passed across `:app`, `:core`, and `:server` modules; 3 Python SQLite migration tests passed.
  - Assembled clean debug APK (`app-debug.apk`).
  - Updated `UI.md`, `CHANGELOG.md`, and recorded UI lessons in `tasks/lessons.md`.

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite tests.
- **Build:** Clean debug APK assembled (`app/build/outputs/apk/debug/app-debug.apk`).
- **Hygiene:** `git diff --check` passes with zero warnings.
- **Device Limitations:** Physical device display and touch interaction remain pending runtime verification.

---

# Listening History and Discovery Sources

## Status: 🟢 Implemented & Verified (Device Review Pending)

- [x] Add durable history storage separate from queue state, with a non-destructive database migration (`listening_history` table, `MIGRATION_1_2`, RoomDbHelper, tested with SQLite script).
- [x] Implement playback accounting with regression tests for pauses, seeks, duplicate events, same-track handoffs/repeats, completion and skips (`ListeningHistoryTracker`, 8 core unit tests).
- [x] Wire service playback events and add Unplayed/Rediscover sources using the approved 90%/30-day defaults (`MusicMateServiceImpl`, `QueueManager`, `QueuePage.kt`).
- [x] Run core/app tests and debug build, update documentation, and record device verification limitations (117 unit tests pass across all modules, debug APK built, `USER_GUIDE.md` and `CHANGELOG.md` updated).

### Verification & Results
- **Unit Tests:** 117 tests pass (50 core, 51 app, 16 server), plus 3 Python SQLite migration/query tests.
- **Build:** `./gradlew assembleDebug` succeeds cleanly (`app/build/outputs/apk/debug/app-debug.apk`).
- **Device Limitations:** Physical device playback telemetry, UI interaction, and audio transitions remain unverified in runtime environment due to device access constraints.

---

# Smart Queue Implementation

- [x] Implement persisted Manual/New/Downloads source state and bounded refill with session exclusions and manual priority.
- [x] Integrate background refresh and shared source selector in queue UI, with accurate category descriptions and empty states.
- [x] Add six queue regressions for stable next tracks/manual priority, removal suppression after restart, anchor restoration/manual freeze, bounded refill/new candidates, clear-during-query races, and Downloads/missing files. All 40 core and 51 app tests pass; debug APK built; diff check clean.
- Detailed approved definitions: `tasks/smart-queue-plan.md`.
- Device playback, source-menu rendering, and process-restart journeys remain unverified because device-tool instruction access was blocked earlier in this session.

---

# Preview Simplification and Save Feedback

- [x] Quiet genre, cover controls, metadata borders, and utility colors; center button content and reserve indicator space while keeping five actions.
- [x] Connect saving/success/failure feedback to both save paths and clear delayed feedback on destruction. Source review complete; device interactions remain unverified.
- [x] Build debug APK, run app tests, inspect resources/diff, and document visual verification limits. Debug assembly succeeds; 51 app tests pass; diff clean. XML checks confirm all five centered actions. Updated UI/changelog. New rendering, large-font behavior, and interactive save outcomes still require device verification; device-tool instruction access remains blocked in this session.

---

# Studio Command Dock — Implemented; Device Review Pending

## Objective
- Redesign the five tag-preview actions into one console-like control surface without removing any action.

## Checklist
- [x] Charcoal dock surface with 24dp top corners, hairline top border, and 16dp side padding.
- [x] Evenly spaced utility row with vector icons, 48dp targets, hairline separators, and neutral styling; removed Organize's filled pill.
- [x] 65/35 split between graphite Edit Song Info and Save, both 52dp with 14dp corners.
- [x] Save indicator covering clean, unsaved, saving, and saved states with accessibility state descriptions.
- [x] Brief press compression plus existing haptics; long-press shortcuts preserved.
- [x] Build, app tests, and diff checks pass; all five action IDs retained.

## Verification limit
- Device visual and interactive review remains pending; device-tool instruction access was denied earlier in this session.

---

# Compact Tag Preview — Implemented; Device Review Pending

## Objective
- Improve first-screen density after the user confirmed folder scrolling works. Detailed plan: `tasks/plan.md`.

## Checklist
- [x] Review the proposed compact layout with the user. Approved: “do it”.
- [x] Compact the centered square artwork and title using available viewport space. Java/resources compile; source checked for restoring full-width editor artwork. Device transition verification pending.
- [x] Group genre/audio badges and consolidate labeled metadata rows. Compose compilation passes; navigation callbacks preserved and source reviewed.
- [ ] Compare against the reference screenshot; target all metadata visible at default font on that phone.
- [x] Build, run app tests, check diff, and update UI/release documentation. Debug assembly and app tests passed; diff clean. Source review covers 48dp controls, 56dp metadata rows, wrapping, and viewport bounds; runtime responsive/accessibility checks remain pending.

## Verification limit
- The same-device screenshot comparison and interactive preview/editor checks remain pending because device-tool instruction access was denied earlier in this session. Do not treat the first-screen fit target as visually verified.

---

# Preview Screen Visual Fixes

## Objective
- Resolve screenshot review findings while preserving all five bottom actions and existing editing work.

## Plan and verification
- [x] Replace fixed preview header sizing with scrollable content bounded above the measured action dock; match and center artwork controls. Java/resource compilation passed.
- [x] Align labeled metadata rows, clarify track counts, unify vector icons/accent colors, and improve badge contrast and wrapping. Debug assembly and app unit tests passed.
- [x] Build debug APK, run app unit tests, inspect diff, and verify rendering on an available device if access permits. Debug assembly and 51 app tests pass; `git diff --check` passes. XML comparison confirms all five bottom action definitions are unchanged. Device rendering remains unverified because access to the required device-interaction reference was denied.

## Result
- Preview scrolls independently above the dock; editor return-to-preview requires an actual expanding gesture. Removed the obsolete pager dock margin to avoid double-reserving footer space.
- Matching 48dp artwork controls, labeled metadata rows, localized track counts, consistent navigation icons/colors, wrapping badges, and brighter preview DR colors implemented.
- Updated UI documentation and Unreleased notes. APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

# Statusline Overhaul: Single-Line HUD with Remaining Quota

## Status: 🟢 Completed & Hardened

### Objectives
1. **Compact Single-Line Statusline:**
   - Migrate quota display from a second line to the primary line directly following `Google AI Pro` (plan tier) separated by `│`, eliminating vertical clutter and the newline.
2. **Quota Metrics Format Update:**
   - Switch from used percentages to remaining percentages (`% left`).
   - Format each quota section as `<reset_time> <percentage>% left` (e.g., `4h31m 100% left` and `6d4h 93% left`).
   - Remove wide horizontal progress bars to keep single-line width within standard terminal boundaries (~100 chars).
3. **Color Inversion for Remaining Quota:**
   - Implement `color_for_remaining_pct()` so high quota (>60%) displays green, moderate (36-60%) yellow, low (16-35%) orange, and critical (<=15%) red.
   - Retain `color_for_pct()` for used metrics like context window usage (`ctx_pct`).
4. **Performance & Reliability Hardening:**
   - Single-invocation `jq` execution for all JSON data extraction and arithmetic.
   - Full ISO 8601 timezone offset parsing (`+07:00`, `-05:00`, `.123Z`, raw epoch).
   - Stale/elapsed countdown handling (suppresses stale negative timers when quota already reset).
   - Strict zero-stderr failsafe exit on invalid/empty JSON inputs.

---

### Master Checklist

- [x] **Phase 1: Statusline Script Refactor (`~/.gemini/statusline.sh`)**
  - [x] Backup current script to `~/.gemini/statusline.sh.bak3`
  - [x] Update jq extraction to calculate remaining percentages (`% left`) from `remaining_fraction`, `remaining_percentage`, or `used_percentage`
  - [x] Add `color_for_remaining_pct()` function with appropriate color bands
  - [x] Format 5h quota as `${c_muted}${q5h_rel}${reset} ${q5h_color}${q5h_pct}% left${reset}`
  - [x] Format weekly quota as `${c_muted}${qwk_rel}${reset} ${qwk_color}${qwk_pct}% left${reset}`
  - [x] Append quotas directly to `line1` after `plan_tier` separated by `${sep}` without newline
  - [x] Remove `line2` multiline output

- [x] **Phase 2: Comprehensive Test Suite & Verification**
  - [x] Test with user image scenario: Gemini 3.8 Flash, 22% context, `musicmate (master*)`, `Google AI Pro`, 5h quota (4h31m, 100% left), 7d quota (6d4h, 93% left)
  - [x] Test with Claude Code rate_limits payload
  - [x] Test with edge cases (empty JSON, missing quota, detached HEAD, missing plan tier)
  - [x] Verify ANSI 24-bit TrueColor sequences across remaining quota gradient (100% -> green, 70% -> green, 50% -> yellow, 25% -> orange, 5% -> red)

- [x] **Phase 3: Stability Hardening & Edge-Case Audit**
  - [x] RFC3339 / ISO 8601 parser in `jq` supporting UTC `Z`, subseconds, and timezone offsets (`+07:00`, `-05:00`)
  - [x] Prevent stale countdowns: when reset timestamp is in the past (>60s elapsed), suppress countdown and show pure `% left`
  - [x] Remove newline from empty/corrupt fallback (`printf "agy"`) to ensure clean TUI rendering

---

## Review & Verification

### Verification Summary
1. **Single-Line Rendering Verification:**
   - Contiguous single-line HUD:
     `Gemini 3.8 Flash (High) │ 󰍛 22% │ musicmate (master*) │ Google AI Pro │ 4h31m 100% left │ 6d4h 93% left`
   - Verified no extra newlines or trailing newline breaks in terminal TUIs.
2. **Quota Calculations & Formatting:**
   - Evaluated `remaining_fraction` (1.0 -> `100% left`, 0.93 -> `93% left`).
   - Verified fallback for Claude Code `used_percentage` (`100 - used_percentage`).
   - Relative reset times (`calc_relative_time`) accurately format hours/minutes (`4h31m`) and days/hours (`6d4h`).
3. **Adaptive Color Grading:**
   - Tested 5 quota levels: 100% (green), 70% (green), 50% (yellow), 25% (orange), 5% (red).
   - Time string is styled in `c_muted` matching the Tokyo Night theme hierarchy.
4. **Resilience & Fallbacks:**
   - Tested 5 corrupt/null input variations: zero stderr bytes, clean exits with code 0.
   - Tested non-git directories and detached HEAD states.

---

# Tag Activity Preview Refinement & Dialog Lifecycle Hardening

## Status: 🟢 Completed & Verified

### Objectives
1. **Tag Preview Screen UX:**
   - Eliminate duplicated artist and album overlay text (`panel_artist`) on cover art scrim.
   - Retain prominent song title overlay while delegating discography navigation to interactive Compose [`StudioProvenanceSection`](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/java/apincer/android/mmate/ui/compose/AudioBadges.kt).
2. **Compose Dialog Lifecycle Resolution:**
   - Fix fatal `IllegalStateException: ViewTreeLifecycleOwner not found from androidx.compose.ui.platform.ComposeView` when launching Search & Match dialogs.
   - Standardize on `ComponentDialog` with explicit ViewTree owner bindings.
3. **Menu & Performance Hygiene:**
   - Purge obsolete commented-out actions in `tag_more_actions_menu.xml` and `TagsActivity.java`.
   - Add in-memory `LruCache` for third-party music app icons in `PlayerPickerDialog.kt`.

### Checklist
- [x] Remove `panel_artist` from `activity_tags.xml` and `TagsActivity.java`
- [x] Migrate `showSearchQueryDialog` and `showSearchResultsDialog` to `ComponentDialog`
- [x] Bind `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner`, `ViewTreeViewModelStoreOwner`
- [x] Clean up `tag_more_actions_menu.xml` and `TagsActivity.doShowMoreActions()`
- [x] Implement `appIconCache` in `PlayerPickerDialog.kt`
- [x] Record pattern in `tasks/lessons.md`
- [x] Update `UI.md` and `CHANGELOG.md`
- [x] Run unit tests and deploy debug build to physical device (`<device-serial>`)

---

# Whole-App Functional and UI/UX Audit

## Objective
- Review the Android app end to end for reproducible functional defects and user-facing usability/accessibility issues without changing application code or overwriting existing work.

## Checklist
- [x] Map app entry points, primary flows, test coverage, and available verification environment.
- [x] Inspect representative user flows and UI resources for concrete issues; distinguish confirmed defects from hypotheses.
- [x] Run feasible build/tests; device UI testing was not performed because the connected device was in another app and the required interaction reference was inaccessible.
- [x] Report prioritized findings with file/line evidence, impact, reproduction steps, and verification limitations.

---

# Fix Whole-App Audit Findings

## Objective
- Fix all seven findings from the whole-app audit while preserving pre-existing edits in TagsActivity.java, AudioBadges.kt and activity_tags.xml.

## Ordered implementation and verification
- [x] Recheck affected code and existing tests; establish baseline of pre-existing changes.
- [x] Fix lost Search & Match and auto-tag draft warning; focused build/tests pass (activity flow needs device verification).
- [x] Fix non-paginated folders/playlists (and other full-list queries) duplicating on scroll; pagination regression test and focused build pass.
- [x] Fix empty-playlist creation affordance and stale navigation filters; build/tests pass, runtime UI check pending.
- [x] Wire empty queue Browse Library action through both hosts; build/tests pass, runtime UI check pending.
- [x] Add semantic activation to mini-player and Now Playing flip, and adjustable/keyboard controls to Studio Console seek/volume; build/tests pass, accessibility device check pending.
- [x] Persist the user's media-server stopped intent across activity recreation and cancel pending starts; app tests and debug build pass.
- [x] Run full core/app unit tests and debug build; review diff and report that device/visual verification was not performed and app lint still has 22 unrelated pre-existing errors.

---

# Document, Version, and Commit Audit Fixes

## Objective
- Update user/developer documentation and release notes to reflect the completed fixes and existing preview edits, bump the Android patch version, then commit the complete working tree requested by the user.

## Checklist
- [x] Reconcile current documentation and release conventions with the complete diff, including earlier uncommitted preview work.
- [x] Update relevant user/UI documentation and changelog with accurate behavior and verification caveats.
- [x] Bump patch version and versionCode; update stale displayed version in the drawer.
- [x] Verify Gradle tests/build, documentation consistency, staged scope, and diff hygiene (lint has known unrelated failures; device UI not run).
- [x] Commit all intended source/documentation changes; confirm clean working tree and report commit ID.

---

# Usage and Functional UX Review

## Objective
- Review current primary user journeys for usability and functional gaps, rechecking the latest implementation rather than repeating resolved audit findings.

## Checklist
- [x] Inspect navigation, discovery, playback, queue, and library-editing flows with source evidence.
- [x] Verify high-impact findings against call sites; source review only, no builds, tests, or device interaction performed.
- [x] Deliver prioritized findings with user impact and actionable recommendations.

## Review summary
- Found playback handoff ordering, immediate artwork writes despite Discard, loaded-page queue scope, search state divergence, and folder draft persistence defects.
- Identified ineffective empty-library recovery and queue-clear recovery friction.
- Rechecked current implementation rather than repeating previously resolved audit findings. Runtime playback and visual/accessibility checks remain unverified.

---

# Improve Usage and Functional UX

## Objective
- Resolve the seven reviewed issues with predictable playback, recoverable editing, and actionable library states.

## Implementation and verification
- [x] Correct playback target handoff ordering; app unit tests/compilation pass (physical handoff still needs device verification).
- [x] Stage selected artwork until Save, preserve originals on failure, and retain drafts for retry; four file-persistence tests and TagsViewModel tests pass.
- [x] Resolve the full active query for Quick Play; 1,003-track regression fails before fix and passes after fix, with MainViewModel suite passing.
- [x] Synchronize visible search with navigation and Back behavior; app compilation/tests pass.
- [x] Keep folder additions/removals in one draft until scan confirmation; app compilation/tests pass.
- [x] Offer context-specific empty-library, no-results, and error recovery actions; test distinguishes no matches from no indexed music.
- [x] Make queue clearing explicitly confirmed in the shared QueuePage used by both hosts.
- [x] Run app/core tests and debug build, check lint/diff, and document verification limits.

## Verification refinements
- Verify artwork also resolves after Save (including previously cached artwork); preserve the selected file path in saved metadata.
- Bind the folder dialog to a lifecycle-aware host so the new discovery action can safely display Compose content.
- Device interaction references are permission-blocked; device/visual journeys cannot be verified in this session.

## Results
- 51 app and 34 core tests pass (85 total), including eight new regression cases covering artwork persistence/retry, complete-result playback, failed playback lookup, and empty-library classification.
- Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- Lint reports 22 pre-existing errors in unchanged service/audio declarations (MissingSuperCall and Media3 opt-in), plus warnings/hints; no lint errors in added behavior.
- `git diff --check` passes. Updated `UI.md` and Unreleased changelog to document the new interactions.
- Device playback handoff, dialog rendering, and touch/accessibility journeys remain unverified.

---

# Main Dock Menu Safety and UX

## Status
- Tasks 1-4 implemented and source-verified; Task 5 device verification blocked by the phone lock screen. Detailed plan: [dock-menu-fix-plan.md](dock-menu-fix-plan.md).

## Checklist
- [x] Task 1: Added `LibrarySelectionModel`, which keys multi-selection by stable track identity (unique key, then container type/title, then path, then id) instead of row position, so no list replacement can retarget a batch action. Selection whose track leaves the list is dropped; reordering and re-indexing keep it. Selection also ends on library-destination change. 10 regression tests cover destination change, pagination, vanished track, same-position-different-track, reordering, re-indexing, toggle, select-all, and out-of-range input.
- [x] Task 2: Added `MainBackPolicy` (enum-based) so an open drawer consumes Back before selection or library navigation. 3 regression tests cover the precedence order. The `DrawerInterop` open/close helpers, previously unused, are now wired to Back.
- [x] Checkpoint A: 61 app+core tests pass with zero failures; `:app:assembleDebug` succeeds. Live device check still pending.
- [x] Task 3: Renamed to Similar Tracks, Audio Quality, Music Folders & Scan, and Notification Access; moved all drawer copy into `R.string.nav_*` resources. Destinations and queries unchanged.
- [x] Task 4: Drawer tiles and rows now use `Modifier.selectable` with `Role.Tab` (matching Material 3 `NavigationDrawerItem`) and a 48dp minimum height. The dock menu button was already 48dp via `IconButton`'s internal `minimumInteractiveComponentSize()`; verified against the resolved Material 3 1.4.0 source rather than assumed from the 44dp modifier.
- [ ] Checkpoint B: Screenshot and TalkBack comparison on device — blocked, phone still locked.
- [x] Task 5: Ran app+core tests (64 passing, 0 failures), debug build, and diff check; updated `UI.md` (new §3F drawer section) and `CHANGELOG.md`. An independent review of the first pass found selection was still positional and guarded at only one of seven list-replacing call sites; Task 1 was reworked to identity-keyed selection, which closes the gap at the data layer rather than per call site. The same review caught a double `onDestroyActionMode` invocation, now fixed by clearing the `actionMode` field before `finish()`.

## Boundaries and verification limits
- No new Clean Up/Inspect workflows, monetization, playback-engine changes, or selection-system refactor beyond what the safety fix required. The unrelated `AboutScreen.kt` edit is untouched.
- **Not verified on a device:** live Back behavior with the drawer open, visual drawer layout, TalkBack output, large-text rendering, and before/after screenshots. Source and unit tests only. The phone stayed locked for the whole session.
- Batch-action regression tests use fixture tracks; no destructive operation was run against the real music library.
- **Dead code found, not removed:** `MySelectionTracker.java` now has no callers after `LibrarySelectionModel` replaced it, including its duplicate position-to-track resolution. Awaiting a decision before deletion.
- **Deliberately unchanged:** whether the MusicMate logo alone reads as "menu" to a first-time user, and the fact that Back at a root destination with no selection re-opens the drawer (pre-existing `onSearchBackClicked` behavior, not a regression). Both need live comparison; the correctness fixes do not depend on them.

---

# App UX Modernization

## Objective

- Add measurable visual and accessibility quality gates, move the hybrid shell toward type-safe Navigation 3, use larger windows for a Music Center supporting pane, and complete interaction/device validation without changing playback or repository behavior.
- Detailed plan: [plan.md](plan.md).

## Task 1: Add the UI verification harness

**Description:** Add isolated Compose screenshot testing and connected Compose accessibility testing. Resolve screenshot-plugin compatibility with AGP 9.4.1 before changing production UI.

**Acceptance criteria:**
- [x] A deterministic Compose smoke preview has record and verify Gradle tasks.
- [x] A connected Compose test can run Accessibility Test Framework checks.
- [x] Test-only tooling does not alter release packaging or runtime dependencies.

**Verification:**
- [x] Run screenshot verification twice with identical output (1 preview, 0 failures, 0.0% diff on 2026-09-28).
- [x] Run the focused connected smoke test on API 36 or newer (`SM-S931B - 16`, 1 test, 0 failures).
- [x] Run `./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug` (69 app + 52 core tests, 0 failures; debug APK assembled on 2026-09-28).

**Dependencies:** None.

**Files likely touched:** `gradle/libs.versions.toml`, `build.gradle`, `app/build.gradle`, `gradle.properties`, `app/src/screenshotTest/java/apincer/android/mmate/ui/compose/ScreenshotSmokeTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 2: Lock the main-shell and library visual baseline

**Description:** Add stable preview fixtures for the main shell and library so empty, populated, filtered, and selected states render without live services or mutable global data.

**Acceptance criteria:**
- [x] Baselines cover compact portrait, compact at 200% font scale, landscape, and expanded tablet widths.
- [x] Empty, populated, search/no-results, and selection states use deterministic track and artwork fixtures.
- [x] Tests do not read storage, network, playback, wall-clock, or device artwork.

**Verification:**
- [x] Run focused main/library screenshot verification twice (7 references, 0 failures, 0.0% diff).
- [x] Review first-generation references for clipping, unstable pixels, and fixture accuracy; approved on 2026-09-28. The captured 200% metadata clipping is retained as Task 12 evidence.
- [x] Run `./gradlew :app:testDebugUnitTest` (69 tests, 0 failures) and `:app:assembleDebug`.

**Dependencies:** Task 1.

**Files likely touched:** `MainScaffold.kt`, `MusicListScreen.kt`, `UxPreviewFixtures.kt`, `MainLibraryScreenshotTest.kt`.

**Estimated scope:** Medium (4 files).

## Task 3: Lock Music Center and Settings visual baselines

**Description:** Add deterministic fixtures and screenshots for playback, empty/populated queue, media-server states, and responsive Settings layouts.

**Acceptance criteria:**
- [x] Music Center covers no-track, playing, empty queue, populated queue, server stopped, and server running states.
- [x] Settings covers compact stacked controls and expanded two-column layout at default and 200% text.
- [x] Dynamic meters, progress, QR data, and artwork are fixed or disabled in reference renders.

**Verification:**
- [x] Run focused Music Center and Settings screenshot verification (18 total references, 0 failures on a fresh rerender).
- [x] Review references for stable animation, typography, and system-inset behavior (approved 2026-09-28).
- [x] Run `./gradlew :app:assembleDebug`.

**Dependencies:** Tasks 1-2.

**Files likely touched:** `AudioHubSheet.kt`, `SettingsScreen.kt`, `UxPreviewFixtures.kt`, `MusicCenterScreenshotTest.kt`, `SettingsScreenshotTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint A: Baseline locked

- [x] Screenshot verification runs without updating reference images.
- [x] The accessibility smoke test runs on API 36 (`Medium_Phone` emulator, 2026-09-28).
- [x] App unit tests and debug build pass.
- [x] Human review confirms the baseline represents the intended current UI (approved 2026-09-28).

## Task 4: Harden library and shell accessibility

**Description:** Correct labels, roles, state descriptions, traversal order, and target sizes in main navigation, library, search, selection, and empty/error states.

**Acceptance criteria:**
- [x] Every actionable node has one concise accessible name and the correct role/state.
- [x] Decorative children do not create duplicate TalkBack stops; reading order follows the visible hierarchy.
- [x] Automated checks pass for touch target size and contrast on primary library states.

**Verification:**
- [x] Run `MainLibraryAccessibilityTest` with accessibility checks enabled (4 primary states pass on API 36).
- [x] Run focused screenshot verification and inspect intentional diffs (18 references pass unchanged).
- [ ] Manually traverse Library with TalkBack.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `MainScaffold.kt`, `MusicListScreen.kt`, `TrackListItem.kt`, `strings.xml`, `MainLibraryAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 5: Harden Music Center accessibility

**Description:** Make playback, queue, and media-server controls understandable and operable through semantics while moving touched user-facing strings into resources.

**Acceptance criteria:**
- [x] Transport, seek, volume, tabs, queue count, server status, and destructive actions announce label, value, and state once.
- [x] Queue drag/remove actions expose accessible alternatives without requiring a gesture.
- [x] Media-server URLs, QR actions, and engine choices have meaningful semantics and traversal order.

**Verification:**
- [x] Run a connected accessibility test across no-track, playing, queue, and server states (5 tests pass on API 36).
- [x] Run focused screenshot verification and review resource-text diffs (18 references pass; 2 intentional Playback references updated for volume controls).
- [ ] Manually traverse all three Music Center tabs with TalkBack.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `AudioHubSheet.kt`, `NowPlayingPage.kt`, `QueuePage.kt`, `MediaServerPage.kt`, `strings.xml`.

**Estimated scope:** Medium (5 files).

## Task 6: Harden Settings and support-screen accessibility

**Description:** Correct semantics and large-text behavior in Settings, permission onboarding, and About while centralizing their remaining user-facing labels.

**Acceptance criteria:**
- [x] Setting rows expose one action, their current value, and the correct switch/radio role.
- [x] Permission explanations and recovery actions are read in a logical order.
- [x] About links identify their destination and action without duplicate icon announcements.

**Verification:**
- [x] Run a focused connected accessibility test for Settings, Permission, and About (3 tests pass on API 36).
- [x] Verify compact and expanded screenshots at default and 200% font scale (all 4 Settings references pass unchanged).
- [x] Run app unit tests and the debug build.

**Dependencies:** Tasks 1-3.

**Files likely touched:** `SettingsScreen.kt`, `PermissionScreen.kt`, `AboutScreen.kt`, `strings.xml`, `SupportScreensAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint B: Accessible primary flows

- [x] Automated accessibility checks pass for Tasks 4-6 (12 connected scenarios on API 36).
- [x] Touched user-facing labels are resource-backed.
- [x] Screenshot diffs are intentional and reviewed (18 references pass).
- [x] App unit tests and debug build pass.

## Task 7: Add the Navigation 3 route foundation

**Description:** Define serializable, type-safe destinations and a saved back-stack owner while keeping the current UI and Java callback bridge in place.

**Acceptance criteria:**
- [x] Routes represent library destinations, Music Center tabs, Studio Console, Settings, About, and tag editing without resource-ID identity.
- [x] Back-stack state restores selected destination and overlay route after recreation.
- [x] Unit tests define root, overlay, selection, search, and exit Back precedence.

**Verification:**
- [x] Focused route serialization, save/restore, and Back-policy tests pass (8 tests, 0 failures on 2026-09-28).
- [x] App/core unit tests and debug APK pass (128 tests, 0 failures on 2026-09-28).
- [x] Production UI remains visually unchanged (18 screenshot references, 0 failures on 2026-09-28).

**Dependencies:** Checkpoint B.

**Files likely touched:** `gradle/libs.versions.toml`, `app/build.gradle`, `MainRoute.kt`, `MainNavigationState.kt`, `MainNavigationStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 8: Move overlay destinations to Navigation 3 scenes

**Description:** Represent Music Center and Studio Console as Navigation 3 scenes, replacing boolean overlay ownership while preserving state and callbacks.

**Acceptance criteria:**
- [x] Opening and dismissing either surface pushes/pops the correct typed route.
- [x] Music Center tab and playback state survive rotation and window resize.
- [x] Back dismisses the visible overlay before lower-priority states according to the tested policy.

**Verification:**
- [x] Run focused overlay navigation and save/restore tests (`MainNavigationStateTest` and `FullscreenStudioConsoleTest`; included in 79 app tests, 0 failures on 2026-09-28).
- [x] Verify compact screenshots match the approved baseline except for intentional transitions (18 references, 0 failures on 2026-09-28).
- [x] Exercise Music Center and Studio Console open/dismiss flows on the API 36 `emulator-5554`; Queue remained selected after rotation, Studio opened in landscape, and system Back returned to the Library. The focused connected Music Center route test also passed.

**Dependencies:** Task 7.

**Files likely touched:** `MainScaffold.kt`, `MainScaffoldState.kt`, `AudioHubSheet.kt`, `FullscreenStudioConsole.kt`, `MainNavigationState.kt`.

**Estimated scope:** Medium (5 files).

## Task 9: Move top-level destinations to Navigation 3

**Description:** Make Navigation 3 the source of truth for library destinations and Back behavior while retaining `MainActivity` service and data callbacks.

**Acceptance criteria:**
- [x] Drawer/rail selection, search, filter clearing, selection mode, and Back update one route/back stack.
- [x] Existing resource-ID callbacks are isolated in one compatibility mapping rather than stored as navigation state.
- [x] Rotation, resize, and process recreation retain route and visible content without stale selection or search.

**Verification:**
- [x] Run route, `MainBackPolicy`, `LibrarySelectionModel`, and `MainViewModel` tests (31 focused tests; full gate: 80 app + 52 core tests, 0 failures on 2026-09-28).
- [x] Run main/library screenshot verification across all four form factors (18 references, 0 failures, 0.0% diff on 2026-09-28).
- [x] Exercise each top-level destination and Back path on the API 36 `emulator-5554`: all six non-root destinations exposed Back; rotation retained Genres; Back returned to All Songs; active search was cleared before root exit. The consolidated connected class passed 7 tests, including typed drawer selection and saved-state restoration.

**Dependencies:** Tasks 7-8.

**Files likely touched:** `MainActivity.java`, `MainScaffold.kt`, `DrawerInterop.kt`, `MainRoute.kt`, `MainNavigationStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint C: Navigation compatibility

- [x] All existing app/core unit tests pass (80 app + 52 core, 0 failures on 2026-09-28).
- [x] Screenshot verification shows no unintended compact-window regression (18 references, 0 failures, 0.0% diff).
- [x] Search, selection, drawer/rail, playback callbacks, and Back precedence pass device checks on API 36.
- [x] Route state survives rotation, resize, and process recreation (live rotation plus connected saved-state restoration coverage).

## Task 10: Add the expanded Music Center supporting pane

**Description:** Extract reusable Music Center content and host it as a Material 3 Adaptive Navigation 3 supporting pane on expanded windows while retaining the sheet scene on compact windows.

**Acceptance criteria:**
- [x] Expanded windows show library content and Music Center together without duplicated state or callbacks.
- [x] Resizing keeps the selected Music Center tab, queue position, and playback state.
- [x] Pane navigation, Back, focus order, and system insets remain correct in split-screen and foldable postures.

**Verification:**
- [x] Compact, medium, expanded, and foldable open/closed Music Center screenshots pass (8 new references; 26 total, 0 failures on 2026-09-29).
- [x] Navigation save/restore tests pass across the 840dp supporting-pane boundary while preserving the Queue tab and route state.
- [x] API 36 `emulator-5554` resize checks pass from expanded to compact and back: the Library and Music Center share expanded space, compact mode returns to the sheet, Queue/playback state remains hoisted, and one system Back dismisses the pane after search focus is cleared.

**Dependencies:** Tasks 8-9 and Checkpoint C.

**Files likely touched:** `MainScaffold.kt`, `AudioHubSheet.kt`, `AudioHubContent.kt`, `MusicCenterSceneStrategy.kt`, `AdaptiveMusicCenterScreenshotTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 11: Make queue and library interactions reversible and input-adaptive

**Description:** Improve destructive/reorder feedback and add keyboard and pointer affordances for core library and queue actions.

**Acceptance criteria:**
- [ ] Queue swipe removal uses one clear direction, offers Undo, and restores the exact item position when undone.
- [ ] Reorder exposes drag-handle semantics plus accessible move actions; keyboard users can move focus and activate row actions.
- [ ] Pointer hover/focus states are visible and do not leave sticky touch hover behavior.

**Verification:**
- [ ] Run queue state tests for remove, undo, move, and playing-item identity.
- [ ] Run connected keyboard and semantics tests for library and queue rows.
- [ ] Exercise touch, mouse, keyboard, and TalkBack paths.

**Dependencies:** Tasks 4-5 and 9.

**Files likely touched:** `QueuePage.kt`, `QueueState.kt`, `MusicListScreen.kt`, `TrackListItem.kt`, `QueueInteractionStateTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 12: Respect reduced motion and 200% text

**Description:** Centralize motion policy, stop nonessential infinite effects when animations are disabled, and remove remaining clipping/overlap at large font scales.

**Acceptance criteria:**
- [ ] Empty-state pulses, meters, and decorative transitions settle to a meaningful static state when system animations are disabled.
- [ ] Primary screens remain readable and operable at 200% font scale without horizontal clipping or hidden actions.
- [ ] Focus, state, and content survive configuration and font-scale changes.

**Verification:**
- [ ] Run motion-policy unit tests and large-text screenshot verification.
- [ ] Run accessibility checks at 200% font scale.
- [ ] Exercise animation scale 0x and 200% text on device/emulator.

**Dependencies:** Tasks 4-6 and 10.

**Files likely touched:** `UiMotionPolicy.kt`, `MusicListScreen.kt`, `NowPlayingPage.kt`, `FullscreenStudioConsole.kt`, `UiMotionPolicyTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint D: Adaptive experience

- [ ] Compact, medium, expanded, foldable, and split-screen layouts preserve task continuity.
- [ ] Core flows complete through touch, keyboard, pointer, and TalkBack.
- [ ] No content is clipped by system bars, cutouts, IME, or pane boundaries.
- [ ] Screenshot and accessibility suites pass without updating references.

## Task 13: Add the system-access state foundation

**Description:** Define one authoritative, side-effect-free model for MusicMate's full-storage and external-player access. Replace package-wide notification-listener parsing with the public component-specific API and expose state that UI hosts can refresh without triggering a prompt or Settings intent.

**Acceptance criteria:**
- [x] Storage state is derived from `Environment.isExternalStorageManager()` and external-player state from `NotificationManager.isNotificationListenerAccessGranted()` using `MediaNotificationListener`'s explicit `ComponentName`.
- [x] Capability checks never launch UI, mutate permission state, or conflate notification-listener access with `POST_NOTIFICATIONS`.
- [x] The main Compose state can represent granted, denied, and optional/required presentation without retaining an `Activity` context.

**Verification:**
- [x] Run focused unit tests for all capability-state combinations and the drawer summary mapping.
- [x] Run existing permission/service unit tests and `:app:assembleDebug`.
- [x] Run `git diff --check`.

**Dependencies:** Task 6.

**Files likely touched:** `PermissionUtils.java`, `SystemAccessState.kt`, `MainScaffoldState.kt`, `SystemAccessStateTest.kt`.

**Estimated scope:** Medium (4 files).

## Task 14: Build the status-driven System Access screen

**Description:** Refactor the current all-or-nothing permission onboarding into a lifecycle-aware System Access screen with separate storage-management and external-player rows, clear required/optional copy, current status, rationale, and explicit user-triggered actions.

**Acceptance criteria:**
- [x] The screen refreshes both statuses in `onResume`, shows storage as required for scan/tag/file operations, and shows external-player access as optional.
- [x] Storage opens the app-specific all-files-access page with a guarded general-page fallback; external-player access opens the listener detail page when supported with a guarded general-page fallback.
- [x] The mixed `PERMISSIONS_ALL` request and its `any { granted }` success condition are removed; unrelated Bluetooth, internet, and obsolete storage permissions are not requested by this flow.

**Verification:**
- [ ] Run focused activity/UI tests for denied, partially granted, fully granted, cancel, and return-from-Settings states.
- [ ] Run permission-screen screenshot and accessibility checks at default and 200% text.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug`.

**Dependencies:** Task 13.

**Files likely touched:** `PermissionActivity.kt`, `PermissionScreen.kt`, `PermissionUtils.java`, `strings.xml`, `SupportScreensAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 15: Add contextual recovery and live external-player refresh

**Description:** Connect access recovery to the features that need it. Folder setup requests storage only after the user starts that operation, while the player picker explains optional external-player integration and can open the focused access screen. Make service-side listener registration idempotently synchronize after grants or revocations.

**Acceptance criteria:**
- [x] App launch and resume silently validate state but never automatically display a permission prompt or open Settings.
- [x] A denied folder scan opens System Access focused on storage; the player picker remains usable for local/DLNA targets and exposes an optional external-player enable action.
- [x] Returning after a listener grant registers active-session monitoring and refreshes player targets immediately; revocation unregisters/clears external targets without requiring an app or service restart.

**Verification:**
- [x] Run unit tests for feature-gating and idempotent listener register/unregister transitions.
- [ ] Run connected tests for grant, deny/cancel, revoke, activity recreation, and service-already-running scenarios.
- [ ] Manually verify folder setup and player discovery on API 36 or newer.

**Dependencies:** Tasks 13-14.

**Files likely touched:** `MainActivity.java`, `PlayerPickerDialog.kt`, `MusicMateServiceImpl.java`, `MediaNotificationListener.java`, `SystemAccessIntegrationTest.kt`.

**Estimated scope:** Medium (5 files).

## Task 16: Consolidate and polish the drawer access destination

**Description:** Replace the two raw Settings shortcuts with one System Access drawer destination that reports a concise current summary and opens the status-driven screen, while preserving drawer selection and Back behavior.

**Acceptance criteria:**
- [x] The drawer contains one `System Access` row instead of separate Storage Access and Notification Access rows, with a truthful `Ready`, `Storage needed`, or `Optional access off` summary.
- [x] Copy says `External Player Access`, never implying that listener access controls MusicMate's own notifications; status and action semantics are announced once by TalkBack.
- [x] Drawer state updates after returning from Settings and survives rotation/resize without marking the external activity as a selected library destination.

**Verification:**
- [x] Run drawer navigation, Back-policy, semantics, and capability-summary tests.
- [ ] Run compact, expanded, and 200% text screenshot verification; obtain human approval before updating references.
- [ ] Exercise grant/revoke and drawer reopen behavior on API 36 or newer.

**Dependencies:** Tasks 13-15.

**Files likely touched:** `MainActivity.java`, `MainScaffold.kt`, `menu_ids.xml`, `strings.xml`, `MainLibraryAccessibilityTest.kt`.

**Estimated scope:** Medium (5 files).

## Checkpoint E: Contextual system access

- [x] Launch/resume validation has no automatic prompt or Settings redirect.
- [x] Storage and external-player access have independent, cancellable, contextual recovery paths.
- [ ] Grant and revocation state appears immediately in the screen and drawer.
- [ ] External-player discovery begins or stops correctly without restarting MusicMate.
- [ ] Unit, connected, accessibility, screenshot, build, and diff checks pass.

**Evidence (2026-09-29):** Focused state-policy unit tests, debug assembly, screenshot verification, and permission/drawer Compose tests passed on Android 16 (`SM-S931B`). Live special-access grant/revoke journeys were not performed. The full core test gate is blocked by the unrelated untracked `FileRepositoryCoverArtTest.java`, which references two missing methods; project lint remains red on its existing 33-error baseline.

## Task 17: Run and document the release UX matrix

**Description:** Complete automated and live-device validation, record evidence, and update user/developer documentation to match shipped interactions.

**Acceptance criteria:**
- [ ] The matrix covers launch/discovery, navigation/search, playback handoff, Music Center, queue edit/undo, tag editing, Settings, permissions, and media server.
- [ ] Phone portrait/landscape, 200% text, tablet/foldable resize, TalkBack, keyboard, and pointer results are recorded.
- [ ] Known limitations and follow-up observations are explicit; no unverified behavior is described as complete.

**Verification:**
- [ ] Run `./gradlew :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.
- [ ] Run screenshot verification, focused connected tests, lint for changed UI files, and `git diff --check`.
- [ ] Review generated screenshot diffs before updating any golden files.

**Dependencies:** Tasks 10-16 and Checkpoints D-E.

**Files likely touched:** `UI.md`, `USER_GUIDE.md`, `CHANGELOG.md`, `tasks/todo.md`, `tasks/ux-validation.md`.

**Estimated scope:** Medium (5 files).

## Checkpoint F: Complete

- [ ] All acceptance criteria above are checked with evidence.
- [ ] Automated suites, debug build, focused lint, and diff hygiene pass.
- [ ] Human visual review approves reference-image updates.
- [ ] Device findings and remaining limits are documented.
# App functionality and interface review (2026-09-30)

- [x] Inspect current functionality, UI flows, pending changes, and differences against main.
- [x] Run relevant automated checks and inspect available device/runtime evidence.
- [x] Record confirmed defects, usability findings, and verification limits in tasks/app-review-2026-09-30.md.

Evidence: 142 unit tests and debug assembly pass. Screenshot comparisons fail for 21/26 previews; lint reports 33 errors. Five fresh previews inspected visually. No connected device; live interaction checks remain outstanding in the report.
# MQA display review (2026-09-30)

- [x] Trace MQA detection and native/Web UI display paths.
- [x] Verify display inconsistencies against source, relevant tests, and available runtime evidence.
- [x] Report confirmed findings with file references and verification limits in tasks/mqa-display-review-2026-09-30.md. Six focused tests passed; no device connected.

## Task 18: Unify DSD Audio Badge Styling (Cyan)

**Description:** Unify the DSD color styling across the app. Currently, the legacy XML views (using `TagUtils.java` and `BadgeView`) misclassify DSD as either Gold (`quality_hd`) or generic grey (`mm_label_lossless`), while the modern Compose UI uses a distinct Cyan (`#00E5FF`). We will adopt Cyan as the single source of truth, update `TagUtils.java` to support tiered badge colors, and clean up duplicate XML color definitions.

**Acceptance criteria:**
- [x] Define definitive Cyan color tokens for DSD (`badge_dsd_text`, `badge_dsd_bg`) in `colors.xml`.
- [x] Clean up abandoned/duplicate `resolution_dsd` and `quality_dsd` colors in `colors.xml`.
- [x] Upgrade `TagUtils.getCodecColor()` and `TagUtils.getCodecBgColor()` to return specific tiered colors (Cyan for DSD, Purple for MQA, Gold for Hi-Res, Blue for CD) instead of a binary lossy/lossless check.
- [x] Fix `TagUtils.getResolutionColor()` to return the Cyan DSD color.
- [x] Ensure `TagUtils.formatCodec()` formats DSD compactly (e.g., "DSD64") rather than just the file extension.

**Verification:**
- [x] Run `app` unit tests to ensure `TagUtils` logic isn't broken.
- [x] Run `:app:assembleDebug` to verify no AAPT color resource linking errors.

## Cover Art Architecture Fixes
- [x] Phase 1 (already in code, confirmed 2026-10-02): Implement `MediaMetadataRetriever` in `FileRepository` to replace `FFMpegHelper` for embedded cover extraction.
- [x] Phase 2 (already in code, confirmed 2026-10-02; scan always sets albumArtFilename and extraction writes the file the lookup re-checks): Fix Lazy Extraction bug in `FileRepository.getCoverArt()` so embedded art is properly extracted and returned to Coil when missing.
- [x] Phase 3 (already in code): Leave unmanaged file hashing as `MD5(file path)` to support heterogeneous folders (e.g., Downloads).
- [x] Phase 4 (2026-10-02): Refactor `CoverartFetcher` to use native Coil `AssetImageSource` instead of copying the default cover to the disk cache.
- [x] Device (2026-10-02): a track with no art shows the default cover (user confirmed). The cached copy did come back: MusixMateApp.initDefaultAssets() copied the whole Covers asset folder at every startup (skipping existing files). Nothing read those copies, so that startup copy was removed; after relaunch neither no_cover.png nor no_cover3.png is recreated
- [ ] Unused asset: assets/Covers/no_cover3.png (about 1.2 MB) is referenced nowhere; remove it if it is not wanted. A local, untracked assets/Covers/.DS_Store is packaged into local builds

## Data integrity fixes (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P1-11 FileOperationTask: fail item when TagWriter.writeTagToFile fails
- [x] P1-12 Search & Match: stage downloaded cover, commit only on Save
- [x] P1-10 Organize: require Save of dirty edits first
- [x] P2-6 Extract/Remove embedded art: real result, confirm Remove
- [x] P2-7 Batch ops: surface per-file failures, no finish() on total failure

## HttpCore 5.5-beta3: bytecode patch instead of vendored classes (2026-10-01, N2)
- [x] patchHttpCore: ASM-rewrite jdk/net references in the original jar (getstatic -> aconst_null, Sockets.setOption -> pops, supportedOptions -> emptySet)
- [x] Fail the build if any jdk/net reference survives
- [x] Delete vendored SingleCoreIOReactor.java and ReflectionUtils.java
- [x] Verify: build, javap scan of patched jar, unit tests (ReflectionUtilsTest), assembleDebug

## Batch completion race (2026-10-01, N1/N3/N4 from tasks/app-review-2026-10-01-delta.md)
- [x] FileOperationTask: delete/move/measureDR complete exactly once, after every item's final status (finishItem in finally)
- [x] measureDR analyses a copy, so a failed write leaves the displayed track unchanged
- [x] TagsActivity Organize: Measure DR completion runs on the UI thread
- [x] FileOperationTaskCompletionTest (200 rounds x 64 items, 8 threads); app unit tests 95/95; assembleDebug

## P1-1 Removing the playing track skips its follower (2026-10-01)
- [x] QueueManager: removing the playing track records its follower; Next/Previous continue from it until playback moves
- [x] Removing that follower too moves on to the next one; Repeat All wraps; last track ends the queue
- [x] setPlaybackTrack no longer re-enqueues the removed, still-playing track (part of P1-3)
- [x] onTrackDeleted plays the follower or stops, instead of replaying the previous track when the last one is deleted
- [x] skipUnplayable bounded so Repeat All with all-missing files cannot spin
- [x] 8 new QueueManagerTest cases (red then green); core 61/61, app 95/95, assembleDebug

## P1-5 Shuffle reshuffles on every queue edit (2026-10-01)
- [x] Shuffle order kept by track id (shuffleIds); edits merge instead of reshuffling
- [x] Play Next goes directly after the playing track; enqueued/re-added tracks slot in randomly among upcoming ones; played tracks do not return
- [x] Full reshuffle only on queue replace/load and when shuffle is turned on
- [x] Play Next / enqueue after the playing track was removed continue from the right place (P1-1 follow-up)
- [x] 5 new QueueManagerTest cases (red then green, 5 repeated runs); core 66/66, app 95/95, assembleDebug

## P1-3 Stale gapless preload after queue edits (2026-10-01)
- [x] QueueManager change listener (queue edits, Repeat, Shuffle); not fired by navigation
- [x] Service re-checks the handed-over follower on change (coalesced on the scheduler); replaces or clears it
- [x] DLNA: setNextTrack(null) clears with empty NextURI; a rejected clear keeps preload state for transition matching
- [x] Local ExoPlayer: setNextTrack is relative to the playing item (old removeMediaItem(1) removed the playing track after an auto transition); null clears
- [x] lastPreloadedTrackId reset on track start
- [x] QueueManagerTest listener case; core/app/server-jupnp tests, assembleDebug
- [ ] Device: local gapless across 3+ tracks; edit queue / toggle Repeat after preload (local + verified-gapless DLNA)

## P1-6 Sleep "end of track" (2026-10-01)
- [x] Manual Next no longer pauses; the timer stays armed for the new track
- [x] While armed, no gapless follower is handed to the player (and an already-handed one is cleared), so every track end passes the check
- [x] DLNA fallback timer honours the armed timer; disarming re-preloads when playing
- [x] app tests, assembleDebug (no service unit-test harness; logic verified by reading)
- [ ] Device: arm end-of-track on local and verified-gapless DLNA; press Next once (keeps playing), then let the track end (pauses)

## P1-2 Session Next/Previous ignore the queue on local playback (2026-10-01)
- [x] QueueAwareSessionPlayer (ForwardingSimpleBasePlayer) wraps ExoPlayer for the MediaLibrarySession
- [x] Always advertises Next/Previous; Next/Previous run the service queue skip (posted to the player looper); Previous past 3 s restarts the track
- [x] Verified Media3 1.11.1 dispatches handleSeek(-1, ...) when ExoPlayer has no next/previous item
- [x] app tests, assembleDebug (no Robolectric/Media3 test utils; no unit test)
- [ ] Device: notification, lock screen, wired/Bluetooth headset Next/Previous on local playback; queue sheet and now-playing stay in sync

## P1-4 Play after a paused target switch resumes an empty renderer (2026-10-01)
- [x] switchPlayer records PendingResume(target, track, position) when switching while paused
- [x] resumePlayer starts the track there instead: DLNA via playerPlaySong(udn, track, positionMs); local/external via playSong + seekTo
- [x] internalPlayOnDMRPlayer accepts a start position; any explicit playSong clears the pending resume
- [x] app tests, assembleDebug (service has no unit-test harness)
- [ ] Device: pause on local, switch to DLNA, Play (starts at the paused position); pause on DLNA, switch to local, Play

## P1-7/P1-8/P1-9 Media server lifecycle and status (2026-10-01)
- [x] P1-7 Hub tracks wantRunning: network recovery restarts only a server the user wants; Stop during STARTING applies when the start completes; Start during STOPPING restarts after the stop; network loss uses stopInternal
- [x] P1-7 Failed start/restart releases locks and reports ERROR
- [x] P1-8 Hub reports STARTING; the service mirrors the hub status flow (asLiveData) instead of posting RUNNING right after an async start
- [x] P1-9 Service restarts a running server when PREF_SERVER_ENGINE changes; Settings and Music Center only save the preference; a stopped server stays stopped
- [x] app/server-jupnp tests, assembleDebug (no hub/service unit-test harness)
- [ ] Device: Stop, then toggle Wi-Fi (stays stopped); Stop right after Start; start without network (ERROR); Wi-Fi off/on while running (STOPPED then RUNNING)

## P1-13/P1-14 Library query errors and paging (2026-10-01)
- [x] P1-13 TagRepository.findMusic propagates query failures (removed findMusicOrEmpty/EMPTY_LIST), so the list shows "Couldn't load music" + Retry instead of "No tracks"
- [x] P1-13 Related-tracks sheet catches the failure instead of crashing viewModelScope
- [x] P1-14 Paged TrackDao queries end with `id ASC` (16 queries), so page order is fully determined
- [x] P1-14 appendPage / loadUntilFound skip ids already shown (LazyColumn is keyed by track id)
- [x] TagRepositoryQueryErrorTest (red on old code), AppendPageTest; core 68/68, app 97/97, assembleDebug
- [ ] Device: scroll a large library while a scan runs; no crash, no duplicate rows

## P2 items (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P2-1 Sleep chip refreshes from getSleepTimerRemainingMs every second while visible; clears when the timer fires (SleepTimerFormatTest)
- [x] P2-2 Sleep fade reads the renderer volume (new MediaServerHub.playerGetVolume), fades 3/4-1/2-1/4, pauses, restores it; unknown volume pauses without fading
- [x] P2-3 Startup scan uses KEEP (never cancels a queued/running scan); user full rescan REPLACE; user incremental APPEND_OR_REPLACE
- [x] P2-4 Media server auto-start is opt-in (default false); Start/Stop still remember the choice
- [x] P2-5 Scan indicator shows "Scan waiting to start…" for ENQUEUED/BLOCKED; FAILED shows a toast; picks the active work among chained scans
- [x] P2-8 Convert keeps the embedded cover (attached_pic map); retries audio-only if the muxer rejects it
- [x] P2-9 Picked cover applies to every folder of the edited tracks (confirm when >1); ArtworkDraft supports several targets (ArtworkDraftTest)
- [x] P2-10 FastScrollbar reads latest totalItems/offset/range/label; drag accumulates from its start
- [x] P2-11 Artwork tap during selection toggles the row
- [x] P2-12 Folder Play/Queue: no premature toast; outcome (count, empty, error) reported after the work; unbound says so
- [x] P2-13 Scroll position kept per destination (musicListKey); drill-in starts at top, Back restores
- [x] app/core tests, assembleDebug
- [ ] Device: sleep chip countdown + DLNA fade/restore; startup during a pending full rescan; Convert keeps art (FLAC/MP3/ALAC/AIFF); multi-folder cover; fast scroll after paging; selection art tap; folder Play toast; drill-in/Back scroll

## Track menu (2026-10-01, open items from tasks/app-review-2026-10-01.md)
- [x] Convert Format in the track ⋮ menu (file-operations group, action_encoding_file)
- [x] Playback group hidden while no playback service is bound (MainScaffoldState.isPlaybackAvailable), per UI.md §A
- [ ] Device: open the ⋮ menu with and without a bound service; Convert Format opens the convert dialog

## P3 polish (2026-10-01, from tasks/app-review-2026-10-01-full.md)
- [x] P3-1 Folder picker reopens after storage access is granted; PermissionActivity opened for storage closes itself once granted
- [x] P3-2 Library destination (type, keyword, filter, search) saved in onSaveInstanceState and restored before the first load
- [x] P3-3 Folder Play/Queue buttons 48dp with folder-named labels; track row click/long-press labels follow tap mode and selection (MainScaffoldState.listenerTapMode)
- [x] P3-4 Removed unreferenced MySelectionTracker.java and the now-unused action_open_track string
- [x] app tests, assembleDebug (lint not run)
- [ ] Device: folder picker → grant storage → picker reopens; rotate/theme change inside a collection, search and filter; TalkBack labels in Listener and Curator modes

## SonicNIO to top grade (2026-10-01)
Goal: prove and improve SonicNIO with evidence; then decide whether Netty can be retired.
- [x] 1a. JVM soak test (NioHttpServerSoakTest): 16 clients/8 s and 64 clients/20 s, 0 errors, counters drain to 0, no pool duplicates. Baseline on a Mac: ~700 MB/s, range TTFB p50 4.6 ms / p95 24 ms (16 clients), 18.8 / 75 ms (64 clients)
- [x] 1b. On-device (2026-10-01, SM-S931B as hotspot, Mac client, track 2021715542, 261 MB FLAC, after f1438181). Both engines are bound by the Wi-Fi link (about 8-10 MB/s); no meaningful difference:
    - SonicNIO: single 8.0-9.1 MB/s, seek TTFB p50 28-36 ms / p95 62-134 ms, parallel(4) 7.7-8.4 MB/s, app CPU 5-6%
    - Netty: single 5.0-9.2 MB/s, seek TTFB p50 32-42 ms / p95 69-108 ms, parallel(4) 8.4-10.0 MB/s, app CPU 4%
    - Earlier SonicNIO "33 MB/s parallel" was an artifact: streams were cut at 30 s by the header-deadline bug and the script counted them as complete. One unreproduced run had 4 of 4 parallel streams fail (no server log entries; hotspot tethering errors at the time)
- [x] 2. Fuzz (NioHttpServerFuzzTest): 23 hostile requests, mutated HTTP corpus (1,500 per run; 20,000 in one hunt) and random WebSocket frames (40 per run; 1,000 in one hunt). Found and fixed one leak: a protocol error after a queued reply (PING then malformed frame) left the connection with no interest ops, never closed; it now always ends with a CLOSE frame. Regression test in NioHttpServerTest
- [x] 3a. Remove object pools: soak before 648-728 MB/s, TTFB p50 5.0-5.7 ms / p95 13-27 ms; after 685-752 MB/s, 4.3-4.9 ms / 6.8-23 ms (Mac JVM, 3 runs each)
- [x] 3b. Split NioHttpServer (2,405 -> 1,165 lines): FileResponse (behind StreamSlots), WebSocketSession, NioWebSocketConnection (cross-thread requests via requestWebSocketWrite/requestClose), SerialExecutor, WebSocketHandshake, BoundedByteArrayOutputStream, FileContentTypes; accurate class Javadoc. Fixed on the way: 304 evicting a stream, onOpen after pipelined frames; removed dead metrics, resetOld, isHttpState, locks
- [x] 4. Protocol gaps: Expect: 100-continue, pipelining (in order), HTTP/1.0 closes without keep-alive, 30 s header-read deadline (slowloris), idle sweep every 1 s. A stalled reader was already closed by the idle timeout (test added). 5 tests; 3 fail on the previous commit. Soak client retries connect on host ephemeral-port exhaustion (BindException)
- [x] 5. Observability: counters (streams, evictions, 429/503, bytes/s), Android Log tags, Music Center diagnostics line
    - [x] 5a. Logging: replace System.out/err in NioHttpServer and RateLimitingHandler with java.util.logging (logcat tag NioHttpServer); per-connection idle closes at FINE, evictions and limits at INFO, errors at WARNING with the exception
    - [x] 5b. Counters: NioHttpServer.getStats() snapshot (connections, streams, requests, bytes sent, evictions, 503 rejections, header timeouts, idle closes) plus RateLimitingHandler 429 count; unit-tested over a real socket
    - [x] 5c. Diagnostics line on the Music Center Server tab (ServerDiagnostics, 5 tests; WebServer.getDiagnostics). Compiles and unit-tested; not yet seen on device (phone locked)
    - [x] Device (2026-10-02): "1 stream • 3.0 MB/s • 6 requests" while the Mac downloaded at a 3 MB/s cap; updates every 2 s; wraps between values only
- [x] 6. Decision gate: Netty retired (ADR-037); see "Retire Netty and the engine selector" below
Out of scope: HTTP/2, TLS, chunked request bodies.

## Retire Netty and the engine selector (2026-10-01)
Decision: SonicNIO matched Netty on device (step 1b); Netty never actually ran before 4bd8d78b. User chose to remove the selector too.
- [x] Delete :server-jupnp-netty (settings.gradle, app/build.gradle, libs.versions.toml, Netty packaging/proguard rules)
- [x] Replace CompositeWebServer with NioWebServerImpl in ServerModule; delete CompositeWebServer
- [x] Remove engine selector: Settings (SettingsScreen, SettingsActivity, settings.xml, arrays.xml), Music Center (MediaServerPage, MediaServerState, AudioHubSheet, DialogInterop, MainOverlayHost, MainScaffoldCallbacks, MainActivity), service restart-on-engine-change listener
- [x] Remove PREF_SERVER_ENGINE/DEFAULT_SERVER_ENGINE; migration clears the stale preference
- [x] Update tests (accessibility, screenshot) and docs (ADR-037, README, DESIGN, UI, USER_GUIDE, WEBUI, others, CHANGELOG)
- [x] Verify: 215 unit tests, debug build, R8 release minify, androidTest compile; on device the Server header is SonicNIO and the saved "netty" preference was deleted. Rendered previews checked for Settings and the Server tab. Screenshot validation: the same 30 previews fail on unchanged HEAD (stale baselines), so no new failures; baselines not regenerated here

## UPnP ContentDirectory fixes (2026-10-02)
Found by an end-to-end check against the phone (description, SCPD, Browse, GetProtocolInfo, GENA all work; SSDP untestable from the Mac, its firewall drops the replies).
- [x] Paging: in-memory browsers ignored StartingIndex/RequestedCount ("Recently Added" returned 615 for 20). Page centrally in AbstractContentBrowser.browseChildren; DB-paged folders (album, artist, genre) opt out. SourceFolderBrowser drops its own paging (it stopped at maxResults, not first+max, and never paged subfolders)
- [x] Unknown ObjectID (or missing item metadata) returns error 701 No such object instead of an empty success
- [x] UPnP port: unsupported method (HEAD) gets 405 with Allow, bad URI 400, instead of 500 with the exception text
- [x] Root Browse: 843 ms cold, 20-50 ms cached (the 2 s was during a library scan). "All Songs" page 6.0 s -> 1.07 s by paging tracks before building DIDL items. Artists TotalMatches used a different query (2505 vs 2549 listed); fixed
- [x] Speed (device, 8305 tracks): server address resolved once per request instead of 2-3 network-interface scans per track ("All Songs" whole list 15.7 s -> 4.8 s); playlist looked up once per request instead of per track and one library scan shared by page and TotalMatches (collection page 0.65-1.8 s -> 0.5-0.7 s); Sources one scan instead of two (1.1 s -> 0.6 s)
- [ ] Open (low priority): uncached root Browse 0.8-1.6 s (each child folder's count loads the library; 30 s browse cache hides repeats); whole "All Songs" list still 4.8 s
- [x] Unit tests (BrowsePagingTest 6, UpnpRequestCheckTest 3); on device: paging consistent in 8 folder types, 701 for unknown id, HEAD / -> 405

## DLNA compliance batch (2026-10-02, from the DLNA server review)
- [x] 1. Search: implement ContentDirectory.Search (UPnP criteria: and/or/parentheses; =, !=, contains, doesNotContain, derivedfrom, exists; dc:title, upnp:artist, dc:creator, upnp:album, upnp:genre, upnp:class); "*" and audioItem class return all tracks; paged; unit-tested parser
- [x] 2. res@duration in H:MM:SS.mmm (was "04:23")
- [x] 3. dc:date: valid ISO date from the year field (was "2023-06-09-01-01")
- [x] 4. Advertise only real capabilities: X_DLNACAP and Samsung ProductCap
- [x] 5. DLNA.ORG_PN only for real DLNA profiles (MP3), same in DIDL and contentFeatures header
- [x] 6. res@bitrate in bytes per second
- [x] 7. dc:creator = artist
- [x] 8. Remove artistDiscographyURI -> /hires_badge (404)
- [x] 9. restricted="1" (read-only server)
- [x] Verify: 244 unit tests (UpnpSearchTest 10, DidlValuesTest 3); device: DIDL item fields, Search (383 for "love", 10 for audio by Miles, 8305 all audio, 0 for album class, 708 for malformed), empty caps, contentFeatures without PN for FLAC
- [x] 10/11 (2026-10-02): Server header "Android/16 UPnP/1.0 MusicMate/<ver> [SonicNIO/2.2]" (ServerHeaderTest); modelName "MusicMate". Verified on device on both ports
- [x] 13 (2026-10-02, user chose thumbnails): second albumArtURI /coverart/tn_<key> tagged JPEG_TN (CoverThumbnails, 3 tests); full-size cover stays first. Device: 160x160 JPEG, 86 ms first / 25 ms cached; missing cover gives a default thumbnail
- [x] Windows/Xbox X_MS_MediaReceiverRegistrar:1 (2026-10-02): MediaReceiverRegistrarService on jUPnP's base class, bound with the Microsoft service type (jUPnP's annotation type was wrong); test 2; device: listed, IsAuthorized/IsValidated return 1. Not tried with a real Windows PC or Xbox
- [x] Samsung (2026-10-02): X_GetFeatureList (BASICVIEW audio at root "0") and getMediaInfo.sec -> MediaInfo.sec: SEC_Duration=<ms> (formats checked against UMS source); ProductCap advertises getMediaInfo.sec only; tests SamsungFeatureListTest (2) + duration; verified over the network on device. Not tried with a real Samsung TV. X_SetBookmark (video resume) not needed

## Sony / LG / Toshiba (2026-10-02, evidence: UMS renderer profiles; no Toshiba profile exists)
- [x] 1a. Root container searchable="1" (Search covers the whole library)
- [x] 1b. SearchCaps add upnp:class, dc:creator (both supported)
- [x] 1c. SortCriteria (BrowseSort, 4 tests; device: title +/-, artist+title, date): sort by dc:title, upnp:artist, upnp:album, dc:date (+/-) for Browse and Search; advertise only those
- [x] 1d. SystemUpdateID changes (library version = count:max(id):sum(fileLastModified), checked at most every 30 s; bump on a real change not yet seen on device) when the library changes (was fixed at 1)
- [x] 1e. Client detection and log (ClientRegistry, 5 tests; device: Sony/LG headers named, logcat DlnaClients): User-Agent / X-AV-Client-Info per client, logged once with headers; "clients seen" on the Server tab (gives Toshiba evidence)
- [x] 2. Time-based seeking (2026-10-02; TimeSeek 7 tests, start-offset 2 tests; device: FLAC 120 s / 300 s land on seek points with frame sync and match the file, MP3 60 s proportional on a frame, DSD/M4A/WAV/AIFF 406 with OP=01, past end 416). Not tried on a real LG/Sony TV: TimeSeekRange.dlna.org (npt) -> byte offset (FLAC SEEKTABLE, WAV exact, MP3 proportional); DLNA.ORG_OP=11 only where supported; LG 2022+ seeks only by time
- [x] 3. Formats a TV cannot play (user chose: convert FLAC/ALAC to PCM, hide DSD). Not yet tried on a real Sony/LG/Toshiba TV
    - [x] 3a. ClientFormatProfile (5 tests; named so it does not clash with the existing, unused ProfileManager/ClientProfile) from User-Agent / X-AV-Client-Info: Sony TV (plays FLAC; convert ALAC), LG TV (FLAC only on 2022+ and the UA cannot tell the year: convert FLAC and ALAC), Toshiba TV (no evidence yet: hide DSD only); other clients unchanged
    - [x] 3b. Hide DSD for TV profiles (device: LG UA sees 8299 of 8305 in All Songs and Search, 0 DSD; BubbleUPnP all 8305; browse cache keyed by profile; container childCount still counts DSD) in Browse and Search, with paging and TotalMatches consistent (RemoteClientInfo injected into the actions)
    - [x] 3c. NioHttpServer streaming body (StreamingResponse; 4 tests incl. a no-busy-loop check that fails with parking removed; ADR-036 rule 7) (producer thread, bounded buffer, write interest paused while empty, no busy loop), tested
    - [x] 3d. FLAC -> WAV (FlacToWavTest 3; device: 2 FLACs with an MD5 (16- and 24-bit) MATCH the STREAMINFO MD5, 24/96 at 6.3x real time, first byte 75 ms, Range and time seek exact) (native bit depth and rate) with the pure-Java FlacDecoder; exact Content-Length from STREAMINFO; byte Range and time seek by sample-accurate seek; proof: MD5 of the WAV data equals the FLAC STREAMINFO MD5
    - [ ] Known flaky (2026-10-02, predates 3c): NioHttpServerTest fails about 1 run in 16, a different test each time (Broken pipe on POST, connection closed before headers, missing Content-Length): the server closes a connection early. Not reproduced in 15 targeted runs; capture with the loop script and fix
    - [x] 3e. ALAC -> WAV via FFmpegKit (MediaExtractor reports ALAC as audio/unknown on this Samsung, so MediaCodec cannot be used); device: 16- and 24-bit ALAC identical to Apple afconvert, exact length, Range and time seek match
    - [x] 3f (FLAC; ALAC with 3e). DIDL for converted tracks (device: LG sees audio/wav .wav CI=1, BubbleUPnP the original; size is an estimate from the duration) advertises audio/wav, PCM size and OP for that client

## Playback control vs monitor fixes (2026-10-02, from the playback review)
- [x] 1. isControllable: only a DLNA renderer (DMRPlayer) that MusicMate was asked to drive (switchPlayer(..., controlled=true)); no side effect. Passive streams (browser, BubbleUPnP-driven renderer) are followed, never driven
- [x] 2. Transport commands (next/previous/pause/resume/seek/stop) on a monitored stream do nothing instead of reaching the Android MediaController path
- [x] 3. External apps (UAPP, HiBy, Poweramp): when MusicMate sent the track and the app stops at its end, play the next queued track (ExternalTrackEnd: STOPPED within 3 s / PAUSED within 1 s of the end; 3 s grace after sending a track; tests 4). Device test blocked: HiBy was not running, MusicMate fell back to ACTION_VIEW, HiBy launched but did not play (no media session)
- [x] 4. Remove the unused RUNNING_MODE field; fix the stale "NO internal ExoPlayer" comment
- [x] Verified on device: passive HTTP requests now produce nowPlaying/playbackState broadcasts (none before); pause on a monitored stream is ignored. Controlled DMR path not exercised (no renderer on this network)
- [ ] Verify external continuation (fix 3) with HiBy/Poweramp on the phone
- [x] Decided (player priority, below): automatic switches never interrupt what is playing

## Player priority (2026-10-02, user: DLNA stream > DLNA renderer > local > external; UX rules agreed)
- [x] Explicit choice in MusicMate (switchPlayer controlled=true) always wins
- [x] Automatic switches (a TV/browser stream, an external app starting) never interrupt a target that is playing; when it is idle, the higher-ranked source wins: DLNA stream 4 > chosen DLNA renderer 3 > local 2 > external app 1
- [x] A passive stream counts as playing for the track's length after its request (it reports no state)
- [x] Now Playing follows a stream only if that stream is the current target (not a stream that lost to music already playing)
- [x] A controlled renderer's own pre-fetch (same device) is never a new stream
- [x] Startup / fallback default: last chosen player, else DLNA renderer > local > external
- [x] Tests (PlayerPriorityTest 4); device: idle + browser stream -> followed; local playing + stream -> local keeps priority (logged); local paused + stream -> stream takes over
- [x] Activity beats idleness (user "ok" after Poweramp was not followed): an idle target never blocks one that starts; rank only decides between two starting within 5 s (automaticSwitchAt). Device: Poweramp song followed while an idle renderer was selected
- [x] "Last player" saved only for the listener's choice, not automatic switches
- [x] Now Playing trackId 0 for an external app's song: TrackEntity.copy() did not copy the id (findMusic returns a copy). Fixed; TrackEntityTest fails without the fix
- [x] Device check: Now Playing shows the library id for an external app's song (trackId 2873483536, was 0)
- [x] A paused external app that resumes was not followed (no active-session change): per-session state watchers. Device: Poweramp paused, MusicMate started (local), Poweramp resumed -> followed
# SonicNIO metadata optimization and measurements (2026-10-03)

- [x] Preserve current FileResponse/runtime/APK baseline and add reproducible metadata and request benchmarks.
- [x] Replace unnecessary ETag hexadecimal formatting and per-response date formatter construction; verify byte identity, concurrency and existing HTTP behavior.
- [x] Run interleaved before/after host measurements and available phone checks; record all samples, allocation, throughput and latency without implying a Netty comparison.
- [x] Run core/UPnP tests and debug build, inspect the incremental diff and publish results in PERFORMANCE.md with evidence links.

Scope: implement the research report's first metadata optimization. Header-token caching, parser changes and buffer pooling remain separate experiments so measurements can be attributed to this change.

Result: [PERFORMANCE.md §11](../PERFORMANCE.md#11-metadata-encoding-follow-up), [complete report/evidence](performance/sonicnio-metadata-2026-10-03/REPORT.md). ETag/date allocation medians fall 94.3%/86.8%; 304/206 preparation times fall 13.1%/5.5%. Six interleaved phone samples show seek median 16.282 → 7.804 ms with essentially unchanged 39.43 MiB/s four-stream throughput. Host bulk throughput is unchanged. All 207 core/UPnP cases and debug build pass; bytes, metadata and installed APK identity verified. Optimized APK remains installed. Small-sample latency gains and absent Netty comparison remain explicit.
# Dock output label mismatch (2026-10-05)

- [x] Trace dock and Music Center output labels. Dock used the generic player label; Music Center resolved the Android output device.
- [x] Apply the smallest fix using the existing audio output model. Both local labels now use the same resolved device name; no new API or abstraction.
- [x] Run app unit tests and debug build, inspect the diff against main, and check whitespace.
- [x] Verify the fixed APK on the emulator: uninstalled old signature build, installed debug build, verified connected Android test `UsbBitPerfectSettingsTest` (100% pass), and confirmed matching resolved device label ("Phone Speaker") across both the player dock and Music Center. (Physical USB hardware validation still deferred to physical device).

USB hardware validation requires the physical phone; verified on emulator.
# Native USB bit-perfect playback (2026-10-05)

- [x] Ground playback, mixer APIs, settings and badge behavior; compare lifecycle designs.
- [x] Implement an opt-in USB mode with exact decoded-format matching, unity gain and safe fallback.
- [x] Replace capability-based badge with playback mode status; document the setting.
- [x] Run regression tests, debug build and available runtime checks; record physical USB verification limits.

Throughput checkpoint:
- Blocking first steps: verify Media3 1.11.1 sink extension points and Android mixer behavior before implementation.
- Independent workstreams: research lifecycle and settings/UI contracts read-only in parallel.
- Shared mutable state: one playback owner controls mixer preference and status; workers own disjoint files.
- Smallest safe decomposition: one worker implements the coupled sink/controller lifecycle; settings and integration follow its status contract.
# USB bit-perfect retry (2026-10-06)

- [x] Fix exact decoded/output mixer matching and unity gain before the USB request.
- [x] Verify accepted preference against the actual USB route; clear owned preferences on fallback and release.
- [x] Run meaningful format/lifecycle regression tests, app/core tests, and debug build. 129 app tests and 187 core tests passed; real Media3 sink checks preserve PCM16 bytes before and after trimming/seek and retain the normal output fade.
- [x] Verify settings and ordinary output on connected devices; distinguish physical USB checks from emulator checks. Android 16 emulator: settings toggle persists and restores its original value; normal sink fade and native PCM trim/seek checks pass. The first real-sink run exposed an empty-buffer drain crash, fixed with a regression test.
- [ ] Confirm an accepted native USB request on the physical Samsung + SNOWSKY TINY B. Only the Android 16 emulator is connected; it cannot establish hardware support.

Preserve the existing SNOWSKY name cleanup and picker details. Physical device: Samsung with SNOWSKY TINY B; model/Android version not yet observed.

# USB DAC hi-res output (2026-10-06), replaces USB bit-perfect

Evidence (Galaxy S25 SM-S931B, Android 16, SNOWSKY TINY B, measured over adb):
- No output mix port has AUDIO_OUTPUT_FLAG_BIT_PERFECT, so Android bit-perfect is impossible on this phone.
- Today a 24/96 file leaves MusicMate as 96k 16-bit and Android mixes it to USB at 48k (deep_buffer, PCM_8_24).
- getSupportedMixerAttributes (default behavior) offers 48k-384k at 16/24/32-bit. No 44.1k, no float.
- Media3 1.11.1 truncates high-res PCM to 16-bit unless float output is on; float mode skips custom processors (meters).

Plan:
- [x] Subtract: delete UsbBitPerfectSession, PcmStartFadeRestorer, their unit/instrumented tests, the USB bit-perfect setting, status plumbing (service, MainActivity, picker), docs and the libs.versions comment. Revert picker maxLines probe. Restore gapless preloading and ReplayGain on USB.
- [x] Keep 24-bit: enable Media3 float output. Move level metering from the processor chain to the output write path so VU meters work for 16-bit and float.
- [x] Native rate on USB: before each AudioTrack is created, if the route is a USB DAC and Android offers a default-behavior mixer at the exact track rate and channel mask, prefer it with the highest bit depth. Otherwise clear MusicMate's preference. Clear on stop, release, and DAC removal. Pure selection function with unit tests.
- [x] Picker shows the real USB output format (e.g. "USB 96 kHz / 32-bit output").
- [x] Verify on phone (dumpsys media.audio_flinger): 24/96 track is 24-bit packed at 96k on a 96 kHz 32-bit USB output; 24/48 at 48 kHz 32-bit; 16/44.1 stays 16-bit on the 48 kHz deep-buffer output.
- [x] Fix: Next in Music Center skipped two tracks per tap and stopped early. play() cleared the playlist before setting the new item; the empty playlist reached STATE_ENDED, which the listener treated as a natural track end. Pre-existing in 3.25.0. Verified on phone: 3 fast taps = 3 advances, 0 spurious completions, still playing.
- [x] Listener checks (user): hi-res and CD sound clean, VU meters, gapless album, DAC unplug.
- [ ] Listener check still open: a hi-res track over Bluetooth (24-bit integer now reaches A2DP).

# Sleep timer fixes (2026-10-15)
User report: "the sleep timer function does not work properly" (no symptom details, no phone attached).
- [x] Dialog: highlight the armed option from the service's armed minutes, not the countdown text ("29m" never matched "30m"); show the remaining time in the header. Pure matcher with a unit test.
- [x] Service: a generation counter so a fade that is already running cannot clear a timer set during it, and "Turn Off" stops a running fade.
- [x] Service: fade only a renderer MusicMate drives; a monitored stream cannot be paused, so it is left alone.
- [x] Service: `sleepTimerEndTimeMs` volatile.
- [x] Unit tests + `:app:assembleDebug` (JAVA_HOME temurin 21).
- [x] Service: a failed volume read/write no longer skips the pause.
- [x] User: HiBy R3 over DLNA. No fade for HIBY/SHANLING/FIIO (`DeviceProfile.canFadeVolume`): the fade's GetVolume + 3 SetVolume burst lands right before Pause on a stack documented to drop actions under SetVolume bursts (docs/technical/UI.md).
- [x] Resolved by the fade skip (sleep timer works on the R3 in 3.28.1). Watch item only: `playerPause` sets `isUserInitiatedStop` before Pause succeeds, so a dropped Pause leaves MusicMate thinking it is paused and it won't advance. A Stop fallback must call `mediaHub.playerStop` directly, not `internalStopOnDMRPlayer` (it clears the current track).
- [x] Phone check: user confirmed the sleep timer works with the HiBy R3 on 3.28.1 (2026-10-15); no Pause/SetVolume warnings from MusicMate in the log.
- [x] Ask the user which output was used: HiBy R3 over DLNA.

# Volume slider usability (2026-10-15)
User: "the volume slide is quite difficult to work with" (output in use: HiBy R3 over DLNA, MusicMate-driven assumed).
Found: on DLNA the slider shows the PHONE's STREAM_MUSIC level, not the renderer's; every drag frame sends a SetVolume (the flood ADR-015 recorded for HiBy R3); the +/- buttons on DLNA never move the slider; locally every drag frame pops the system volume panel (FLAG_SHOW_UI); the thumb is 12dp.
- [x] Service: trailing throttle on renderer SetVolume (at most one per ~300ms, last value always sent).
- [x] Service: read the renderer's real volume when a driven renderer becomes active; expose it so the slider shows it.
- [x] Activity: slider and +/- show the renderer level on DLNA; slider drags on the phone do not show the system panel.
- [x] Slider: hold the dragged value locally so a state update cannot yank the thumb; larger thumb (48dp touch height kept).
- [x] Unit test for the throttle; build; tests.
- [x] Studio Console fader: 48dp touch band, 150dp to 220dp wide (rail was ~68dp). Rendered via screenshot tests.
- [x] ADR-015 marked superseded in docs/technical/UI.md.
- [ ] Pre-existing (confirmed by rendering HEAD): at large text the Studio Console right column overflows and hides the transport row and fader. Screenshot references are stale from earlier changes; not updated.
- [x] Phone check: user confirmed the volume slider works with the HiBy R3 on 3.28.1 (2026-10-15).

# Studio Console overflow at large text (2026-10-15)
At fontScale 2 (900x480 preview) the right column's six rows overflow and the transport row and volume fader are clipped (present at HEAD before 3.28.1).
- [x] Title and spec chips in one block with `weight(1f, fill = false)` + `verticalScroll`, so they give way and the header, seek bar, transport and fader keep their size.
- [x] Render both full-screen previews (normal and large text) and compare with the earlier renders.
- [x] Unit tests + debug build.
- [x] dB readout: one line (`softWrap = false`, `widthIn(min = 34.dp)`); it wrapped to two lines at large text.

# Improve built-in playlist rules (2026-10-15)

- [x] Align smart playlist filters and descriptions with what the matcher can actually prove.
- [x] Tighten genre playlist rules around the stated listening moods and remove the duplicate title rule.
- [x] Validate JSON, preset vocabulary, matching examples, and the final diff against main.

# Audit CD wording on Android screens and Web UI (2026-10-15)

- [x] Find rendered CD labels and trace their data sources on Android and Web UI.
- [x] Change broad quality labels to Lossless while keeping genuinely CD-specific descriptions.
- [x] Verify affected UI paths, run focused tests or build checks, and inspect the final diff.

# Audit lossy wording on Android and Web UI (2026-10-15)

- [x] Trace the stored lossy code to Android and Web UI labels.
- [x] Replace ambiguous user-facing wording where the app means lossy audio.
- [x] Verify rendered labels, focused tests, build, and final diff.

# Make music list header stats consistent (2026-10-15)

- [x] Trace header summaries for library, search, and filtered song lists.
- [x] Use one compact song-list summary without intermittent file size.
- [x] Verify affected cases, build/tests, and final diff. Emulator showed count and duration in library and search; debug tests and build passed; signed release installed on phone; scoped diff check passed.

# Release 3.28.3 to main (2026-10-15)

- [x] Audit the uncommitted changes, release conventions, docs, and remote branch state.
- [x] Update user documentation, changelog, and Android version for the verified changes.
- [x] Run focused tests and release build, inspect the staged diff and signed APK. Web UI tests, playlist JSON validation, Android unit tests, release build, APK signature, and version metadata passed.
- [x] Commit the release changes, update `main`, tag `v3.28.3`, and confirm the GitHub release asset. Workflow succeeded; downloaded APK is signed by the MusicMate key and reports version code 154.

# DLNA player volume: safe start and a limit (2026-10-17)

Cause (code): while a newly picked network player's volume is unknown (5 s settle for portable players, 2 s read timeout, or a failed read) the slider shows the phone's own media volume, often 100%, and a touch sends that level to the player. Volume changed on the device is never followed. Every player is assumed to use 0 to 100.
Decisions (user, 2026-10-17): limit network players only; 80% default, adjustable in Settings (60% to 90%, or Off).
- [x] V1 Unknown volume: on switching to a controllable renderer, mark its volume unknown; the slider is disabled and shows "--" and nothing is sent (slider, volume buttons) until a read succeeds; one retry after a failed read; if still unknown, the slider stays off with a hint to use the player's own control.
- [x] V2 Volume range: read RenderingControl's Volume allowed range and convert percent to device units on get and set (0 to 100 assumed only when the range is missing).
- [x] V3 Limit: setting "Maximum volume" under Network players (60, 70, 80 default, 90 or Off); the service clamps every set (slider, buttons) to it; the slider stops at it and marks it. A player already above the limit is shown as it is and only moves down.
  Done (2026-10-17): `RendererVolume` (states, range conversion, clamp; 4 JVM tests), MediaServerHubImpl converts with RenderingControl's Volume range, the service marks a new player READING, retries a failed read once (3 s), then UNAVAILABLE, and ignores slider and buttons until the level is known (also for streams it does not drive). Music Center slider and Studio fader: disabled with "--" while unknown, stop at the limit, show a limit mark. Settings > Network players > Maximum volume (60, 70, 80, 90, Off; default 80). Emulator: card saves the choice; phone output slider unaffected (reaches 100%). Not testable on the emulator: anything with a real renderer.
- [ ] V4 Tests: JVM tests for range conversion and clamping; app builds; emulator check of the Settings entry and that phone volume is unchanged; on the phone with the user's renderer: start shows "--" then the real level, drag stops at the limit.

# DLNA player that loses power stays selectable (2026-10-17)

Cause (code): jUPnP drops a renderer only on its goodbye or when its advertisement expires (often 30 min); a player that loses power sends no goodbye, so it stayed listed and selected, and Play failed silently (state STOPPED, no message) after the 15 s connect timeout.
- [x] Hub: every 30 s discovery tick asks each known renderer for its description (2 s timeout); two misses in a row remove it from the registry. The player being cast to is skipped (its position polls watch it).
- [x] Hub: a SetAVTransportURI, Play or position poll that gets no answer at all removes the player at once.
- [x] Hub to service: `setOnRendererLostListener`, only while RUNNING (a restart clears the registry too).
- [x] Service: the current player lost: switch to this phone, paused at the same song and position, with a message ("X is not responding. Switched to this phone; press Play to continue here.").
- [x] Review fixes: jUPnP 3.0.5 `RegistryImpl.removeDevice` notifies listeners (checked in bytecode); stop/deactivate of the lost player run on the UPnP thread, not the main thread; no liveness checks while the phone has no server network (its own outage is not the players'); a round is skipped while the previous one runs; the listener's saved player is kept through the fallback so the next start looks for it again.
- [ ] Phone test with the user's renderer: power it off while idle (gone from the picker within about a minute) and while playing (falls back with the message; Play continues on the phone).

# UX/UI review fixes (2026-10-17)

Source: rendered review on Medium_Phone (API 36), GitHub debug build. Full token migration (278 hardcoded colors in 37 files) is out of scope; only the DR color mismatch is fixed here.
- [x] F1 Library tip banner scrolls with the list (inside the LazyColumn), so landscape and large text still show songs and the "Don't show" link. Same for the Queue tips.
- [x] F2 Tip card Dismiss X visible on the card (`weight(1f)` instead of `fillMaxWidth()`).
- [x] F3 Output picker: a Network players section that says how to find players when the server is off (start server action) and while searching.
- [x] F4 Queue: PLAY NEXT badge only on tracks inserted with Play Next.
- [x] F5 Tag editor: collapsed header clears the status bar; a back control stays reachable.
- [x] F6 Settings "Display Storage Space": remove the dead toggle.
- [x] F7 DR colors: one source; tokens match what the list shows.
- [x] F8 "DIRECT OUTPUT" on the phone speaker: verify the claim in code; reword if wrong.
- [x] F9 Maximum volume "Off" renamed "100%" (the stored value; "No limit" wrapped in its chip).
- [~] F10 "New" means not organized yet (`!isManaged`, the drawer's Incoming Tracks), not recently added. Only the TalkBack label changed ("Incoming track, not organized yet"); hiding the dot is a product decision for the user.
- [x] F11 Landscape nav rail: Playlists label no longer overlaps Settings.
- [x] F12 Mini player subtitle: marquee stops after one pass.
- [-] F13 Dropped: `MusicListScreen.kt` centers it on purpose (at the end it covered each row's menu button and the fast-scroll thumb).
- [x] F14 Tech Info dock: a divider before the red Remove Art; all dock buttons 48dp tall (Song Info row was 36dp). Icons were tried and dropped: at 411dp and 130% text "Remove Art" broke mid-word.
- [x] F15 "Maximum Volume" title case. Kept: the empty-state label (lessons: stable labels over dynamic ones) and the scan dialog's X plus Cancel (both dismiss; changing it touches three interop call sites).
- [x] F16 Row overflow menu: "Edit tags" entry.
- [x] Verify: app unit tests (both flavors), assembleGithubDebug, emulator screenshots of each fix (portrait, landscape, 130% font), advisor review of the diff.
  Done (2026-10-17): core 245, app 158 (each flavor) unit tests, 0 failures, incl. new `QueueManagerTest.isPlayNext_onlyTracksInsertedWithPlayNext`; `assembleGithubDebug`, `assemblePlayDebug`, androidTest compile. Emulator (Medium_Phone): tips scroll away and each has a visible X; landscape shows songs and the rail scrolls; auto-scroll to the playing song lands right with the tip item; menu has Edit Tags; Play Next marks only that track; picker says "Start the media server..." and after Start "Searching your Wi-Fi..."; badge "THIS PHONE"; collapsed editor bar with working back; Tech Info dock at 100% and 130%; Settings without the storage toggle. Instrumented (installed with adb, `am instrument`, app not uninstalled): SupportScreensAccessibilityTest 3, MainLibraryAccessibilityTest 8, MusicCenterAccessibilityTest 5, all OK. Review fix: `savePlayingQueue` and `loadPlayingQueue` also clear the Play Next set (test extended, red then green). `validateGithubDebugScreenshotTest`: 33 of 34 fail on this branch and identically on clean HEAD (detached worktree), so the references were stale before this work; not regenerated. `SettingsScreenshotTest` source updated for the removed parameter. Not run: Play flavor on screen.
- [ ] Left open: "UNTAGGED" ReplayGain pill breaks mid-word at 130% text (pre-existing, `TagsTechnicalPage.kt`); full color-token migration; stale screenshot references (33 of 34); Play store screenshots on this branch may still show "DIRECT OUTPUT".

# UI/UX update review (2026-10-18)

- [x] Review the latest UI/UX commit against its F1-F16 scope and affected interaction paths.
- [x] Verify suspected issues with focused tests or emulator checks and inspect the diff against its parent. `:app:assembleGithubDebug` and `:core:testDebugUnitTest` passed; QueueManagerTest ran 42 tests with no failures. Fresh emulator launch showed the empty-library state. Title editing and Play Next restart could not be exercised without a seeded library.
- [x] Report concrete issues with severity, file locations, and verification limits. Found a stale collapsed-editor title after a save and lost Play Next badges after queue reload; both follow from code paths and need a seeded-library runtime check.

throughput checkpoint: n/a, read-only investigation.

## Phone verification (192.168.1.54:42841)

- [x] Confirm the installed build, APK signature, and all-files permission before updating the phone. Installed base APK SHA-256 exactly matches `app-github-release.apk` built after HEAD; it is the GitHub flavor with all-files access. No update needed.
- [~] Reproduce the collapsed title and Play Next restart behavior on the phone using existing library data. The phone was playing to HiBy R3; changing a real tag or restarting playback would disturb the user's music, so neither state-change case was exercised.
- [x] Update the findings with observed phone behavior and leave the installed app/data intact. At font scale 1.0, the centered Go to Top button covers the fifth visible track's quality badge and details in `/private/tmp/musicmate-phone-review-20261018.png`. The app and queue were not changed.

# Fix reviewed UI issues (2026-10-18)

- [x] Persist Play Next provenance with the queue, including reload and removal cases. A new behavior test failed before the fix and passed afterward.
- [x] Keep the collapsed editor title in sync when the displayed track changes; an emulator tag save updated the collapsed title immediately.
- [x] Remove the obstructing Go to Top overlay; drag the fast scrollbar in both directions on the emulator.
- [x] Run focused tests, build the GitHub variant, inspect the final diff, and verify the UI on an emulator without changing the phone's playback or library. App and core unit tests: 406 passed, 0 failures; GitHub debug APK built and installed on the emulator. The Play Next badge appeared before restart, and its saved state survived force-stop and relaunch.

# Commit UI review fixes and documentation (2026-10-18)

- [x] Update the relevant release and UI documentation for the three verified fixes.
- [x] Re-run focused verification and check the staged diff against `main`. App and core unit tests: 406 passed, 0 failures; GitHub debug APK built; staged diff has no whitespace errors.
- [x] Commit only the fix and documentation files; leave unrelated working-tree changes untouched.

# Install reviewed UI fixes on phone (2026-10-18)

- [x] Build a signed GitHub release APK from commit `083024d9` and verify its package, permissions, and signing certificate against the installed phone APK. Package `apincer.android.mmate`, version `3.28.3-261018` (code 154), `MANAGE_EXTERNAL_STORAGE`, matching signer SHA-256 `b5757a1e…1d29ee`.
- [x] Install the APK as an update on `192.168.1.54:42841` without clearing app data. `adb install -r` succeeded; first install time stayed 2026-10-04 21:24:56.
- [x] Confirm the installed APK matches the build and the app launches with its library intact. Installed APK SHA-256 matches the build; version is `3.28.3-261018`; all-files access remains allowed; the library shows 8,094 tracks and playback is active to HiBy R3.

# Fix first-connect HiBy R3 volume display (2026-10-18)

- [x] Trace the initial DLNA volume request, response, and UI state on the phone and in code without changing playback volume. Phone log: `HiBy R3: 100 in [0, 100] = 100%`; the app displays exactly that UPnP response. The R3's physical output-port volume is a separate value not exposed by this response.
- [x] Fix the first incorrect state at its source and cover the observable first-connect behavior with a focused test. Exact HiBy R3 targets no longer read or set DLNA volume; other renderers retain remote volume control. Both player screens direct users to the R3 itself.
- [x] Build and test, then verify the live phone behavior before committing or installing an update. 408 app/core tests passed, signed GitHub 3.28.4 APK built and installed in place; installed APK hash matches the build, all-files access remains allowed, and both phone player screens show “Set volume on HiBy R3” with no 100% slider.

# Restore safe write-only HiBy R3 volume control (2026-10-18)

- [x] Separate readable volume from writable volume and define the unknown-first-connection state. HiBy R3 remains unreadable; the first write is capped at 5% and waits for the renderer's SetVolume acknowledgment.
- [x] Add an explicit low starting action in both player screens; enable normal controls only after the R3 acknowledges that value.
- [x] Add focused tests, run app/core tests and APK build, verify the UI and device behavior. 409 unit tests passed, signed GitHub release built. On the phone, HiBy showed an unknown level on first connection; an explicit 5% command was acknowledged and Music Center showed “Set 5%” with the slider. Studio Console showed the same sent level. Physical loudness cannot be measured through DLNA.
- [x] Update release/user docs, commit the fix, and install the verified update on the phone. Installed GitHub 3.28.5 (code 156) in place; signer and all-files permission verified, first install time retained.

# Car Audio DSP for the Android Auto route (2026-10-23)

Design agreed in a grilling session; spec in `docs/technical/CAR_AUDIO_DSP.md`. MusicMate stays out of the Android Auto UI.

- [ ] 1. Route logging: log routed device type, productName and id on every routing change; capture wired and wireless Android Auto in the MU-X. Check whether wired reports `TYPE_USB_ACCESSORY` and trips `UsbOutputFormat`. Media3 may not expose the AudioTrack routing listener; prefer `AudioManager.getAudioDevicesForAttributes()` + `AudioDeviceCallback` (verify against Media3 1.11).
  - Logger built (2026-10-23): `app/.../audio/MediaRouteLog.kt`, wired into `AndroidPlayerController` (device callback, play start, 5 s poll while playing). Media3 1.11 `AudioOutput` has no routed-device getter, so it uses `getAudioDevicesForAttributes`. Each change logs routed device, MusicMate's USB format request, UI mode and all outputs. 164 unit tests pass per flavor; emulator (API 36) writes the file, no crash.
  - Installed on the phone (SM-S931B) 2026-10-23 as an in-place update of 3.28.5 (code 156): release signer, all-files access still allowed, first install time kept. First log line: phone speaker, type 2, uiMode 1.
  - First car session logged 2026-10-23 22:52-22:53: media route `TYPE_REMOTE_SUBMIX` (25) `remote-submix`, 48 kHz / 16-bit, not on the USB DAC path, uiMode 1. Findings in the spec, section 4.
  - Owner 2026-10-23: wireless only; car = remote submix + a user-confirmed Bluetooth car link. Logger now also records HFP and A2DP connected devices.
  - Pending: reinstall, then one wireless drive to confirm the car's HFP link stays connected while the remote submix plays and whether the submix address repeats. Drive steps (wired no longer needed): connect Android Auto first, then play; pause and resume once; disconnect at the end. Read with `adb shell cat /sdcard/Android/data/apincer.android.mmate/files/media_route_log.txt`.
- [x] 2. DSP engine in `IntegerPcmAudioOutput`: double biquads per sample rate, auto preamp, soft-knee limiter, 30 ms crossfade, TPDF dither for int16, single-pass staging for partial writes; developer toggle with a test curve. Unit tests per spec section 7. Done in slice 2.
- [x] 3. Sound modes (Reference, Bass, Vocal, Drive, Custom) and the Now Playing "Car" chip on a flat correction. Done in slice 3.
- [x] 4. Calibration wizard: `RECORD_AUDIO` on entry, checklist, noise check, 5 log sweeps through the player with DSP bypassed, deconvolution, 1/6-octave average, PEQ fit within limits (cuts only below 100 Hz), graph, save profile JSON with route signature. Sweep plays outside the queue with ReplayGain neutral and speed 1.0, recording 2 s past the end. Update `docs/PRIVACY_POLICY.md` and the Play Data safety note: mic audio is processed on the device, never stored or sent. Done in slice 4.
- [ ] 5. Automatic activation by signature, verify sweep (+/-3 dB of the boost-capped target, 60 Hz to 8 kHz), in-car A/B listening check.

## Car DSP slice 2: DSP engine (2026-10-23, car not needed)
- [x] 2a. `audio/dsp`: RBJ biquads (peak, low shelf, high shelf) in double, per-channel state, magnitude response.
- [x] 2b. Chain: auto preamp from the combined response max on a log grid (never positive), bands in series, linked safety limiter (instant attack, about 50 ms release, -0.5 dBFS ceiling), final clamp.
- [x] 2c. `CarDsp` target (null = off) and a per-output processor: 30 ms crossfade on any change including on/off; bypass only once the fade to off completes.
- [x] 2d. `IntegerPcmAudioOutput`: float path processes before int24; int16 path stages processed TPDF-dithered int16 only while engaged, otherwise unchanged direct write; a block is processed once, at staging; flush resets filter state.
- [x] 2e. JVM tests: biquad gain within 0.1 dB at 44.1/48/96/192 kHz, sine through chain matches design, preamp rule, limiter ceiling, off path byte-identical (float and int16), partial writes equal full writes with DSP on, crossfade reaches the new chain after 30 ms.
- [x] 2f. Both flavors' unit tests, release build. 176 tests pass per flavor (12 Car DSP), release APK builds. Review fixes: USB guard fails closed and is also checked when each output opens; on-to-off returns to the untouched path; seek test; engagement logged under tag `CarDsp`. Not installed: the phone pass comes with slice 3. Device check comes with slice 3 (the chip is the on-device switch; no throwaway developer toggle).

## Car DSP slice 3: sound modes and the Now Playing chip (2026-10-23)
- [x] 3a. `CarSoundMode` (Reference, Bass, Vocal, Drive, Custom +/-3 dB trims) and `CarDspPreferences` (saved choice, StateFlow, applies `CarDsp.target`; flat correction until slice 4). Tests: at most 3 filters and +4 dB per mode, preamp absorbs each mode, trims clamp, Off means no target.
- [x] 3b. `CarDspChip` beside the output pill when playback is local and not USB (`NowPlayingState.carDspAvailable`, set in `MainActivity`); Compose dialog with Off, the five modes and Custom sliders. Design tokens only (ColorLiteralBudgetTest).
- [x] 3c. 180 tests pass per flavor; release APK installed on the phone in place (signer, all-files access, first install time verified).
- [ ] 3d. Phone pass (owner): chip visible and readable, Off is untouched, Drive audible with no clicks on switching, a 192 kHz track in Drive with no underruns (`dumpsys media.audio_flinger`), `CarDsp` engage/bypass lines in logcat.

## Car DSP slice 4: calibration (2026-10-23)
- [x] 4a. `audio/calibration` maths (pure Kotlin): radix-2 FFT, 10 s log sweep 20 Hz-20 kHz at 48 kHz with Farina inverse, deconvolution, IR peak, 0.5 s windowed IR to a 1/48-octave magnitude grid, 1/6-octave smoothing, power average across positions, per-band SNR from a noise window of the deconvolved output (reject below 20 dB in any 1/3-octave band, 30 Hz-8 kHz).
- [x] 4b. Target (low shelf +5 dB at 100 Hz, tilt to -3 dB at 20 kHz from 1 kHz), level alignment over 200 Hz-2 kHz, greedy PEQ fit (at most 10 bands, cuts to -10 dB, boosts to +3 dB), constraints on the combined curve (<= 0 dB at every point below 100 Hz, <= +3 dB anywhere), no boost into a narrow deep null (> 6 dB below target over < 1/3 octave), achievable target, verify rule (+/-3 dB, 60 Hz-8 kHz). The verify sweep is pre-filtered offline by the correction and deconvolved with the plain inverse. Done: 10 JVM tests (flat sweep 30 Hz-16 kHz, synthetic car at 0/0.3/1.0 s within 0.5 dB, rejections, fit limits, null, level independence, verify pass/fail); window starts 0.2 s before the peak; boost cap ramps 0 to +3 dB over 100-200 Hz. Review fixes: noise window bounded by the recording (late take rejected with its limit); only bands from 100 Hz reject a take, quiet bands below 100 Hz leave the bass uncorrected (`reliableFromHz`, `FitResult.fromHz`). 190 tests pass per flavor, release builds.
- [x] 4c. `CarProfile` JSON store (one file per car: name, date, Bluetooth name/address, bands, curves, mic source); the latest saved profile is active (Car Audio section can switch or delete); its correction feeds `CarDspChoice.settings()` under the mode. Done: `CarProfileStore` (temp file then rename, unreadable files skipped, newest first), `CarDspPreferences.profiles/setActiveProfile/deleteProfile`; 3 JVM tests.
- [x] 4d. Measurement engine: mic source `UNPROCESSED` when the property says so, else `VOICE_RECOGNITION` marked approximate; sweep on a dedicated 16-bit `AudioTrack` with the player's media attributes (same Android route as music, nothing enters the queue or history; ReplayGain and speed do not apply); pauses MusicMate playback first; records 0.5 s before and 3 s after the sweep. Built-in mic forced with `setPreferredDevice`; a take is rejected if the recorder is not on the built-in mic or the sweep track is not on the music route, if it clips (>= 0.99), has no IR peak, or the noise window does not fit. FFT work off the main thread, inverse spectrum computed once per session, screen kept on, transient exclusive focus per sweep. Done: `SweepRecorder` (streamed track from a writer thread, route check about 1.5 s in, logged under `CarCalibration`), `CalibrationWizard` (takes, fit, save, verify), `HeadsetLink` for the car's Bluetooth name and address. Playback pause comes from ExoPlayer's own focus handling (`setAudioAttributes(..., true)`), so music resumes between takes.
- [x] 4e. Wizard (Compose, full screen) from a new Settings "Car Audio" section: checklist, `RECORD_AUDIO` request, 5 positions with SNR feedback, graph (measured, target, predicted), name and save, verify sweep. Manifest gains `RECORD_AUDIO`. Done: `CarCalibrationActivity` (screen on, permission asked on Start, Measure and Save), `CarCalibrationScreen` (checklist with on-device note, positions with advice, Canvas graph on a log axis, name, verify, verdict), `CarAudioSettings` card in Settings (profiles, No correction, delete with confirm, Calibrate). Manifest: `RECORD_AUDIO`, activity not exported.
- [x] 4f. Privacy: `docs/PRIVACY_POLICY.md` and the Play Data safety note say mic audio is processed on the device, never stored or sent. Done: policy has a microphone paragraph and the permissions line; the Data safety note says no declaration (on-device only, not in scope per Google's help page).
- [x] 4g. Tests both flavors, release build, phone install; home dry run on the phone speaker or a Bluetooth speaker (owner). Progress 2026-10-24: 193 JVM tests pass per flavor, androidTest compiles, release APK built (3.28.5, code 156; signer b5757a1e; MANAGE_EXTERNAL_STORAGE and RECORD_AUDIO present; microphone feature not required). Advisor fixes: mic `uses-feature` optional and Calibrate hidden without a mic, Save guarded against a double tap, denied permission shows a toast, Bluetooth lookup times out after 2 s, each take logs its delay, privacy text names the stored Bluetooth name and address. Installed on the phone 2026-10-24 (update in place, app starts). Pending: owner dry run. Owner dry run done 2026-10-24: AL5125SH_BT calibrated at home (approximate, check sweep skipped).
