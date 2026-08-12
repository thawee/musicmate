# Option 1: Replace Telemetry Arrows with Distinct Vector Icons

## Objectives
Replace text arrow (`➔`) indicators in the Now Playing telemetry badges (`sheet_now_playing_queue.xml`) with dedicated vector icons (`ic_round_audio_file_24` for Source, `ic_round_speaker_24` for Target).

## Tasks
- [x] 1. Update `sheet_now_playing_queue.xml`:
  - [x] Replace Source badge `TextView` arrow with `ImageView` (`ic_round_audio_file_24`, `@color/colorGold`).
  - [x] Replace Target badge `TextView` arrow with `ImageView` (`ic_round_speaker_24`, `#00E5FF`).
- [x] 2. Verify compilation (`./gradlew compileDebugSources`).
- [x] 3. Document results in `tasks/todo.md`, update `DESIGN.md`, `CHANGELOG.md`, and `tasks/lessons.md`.

## Review & Results
- **Vector Icon Badges Applied**: Replaced raw text arrows (`➔`) in telemetry badges with 14dp vector icons: `ic_round_audio_file_24` tinted in Gold for Source input format, and `ic_round_speaker_24` tinted in Cyan for Target output renderer.
- **Verification**: Verified compilation cleanly with `./gradlew compileDebugSources` (`BUILD SUCCESSFUL`).
