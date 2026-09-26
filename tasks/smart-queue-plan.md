# Smart Queue — Scope and Repository Findings

## Confirmed requirements
- Keep the existing manual queue; add New Arrivals and Downloads sources.
- Downloads includes all tracks in download folders, not only unplayed tracks (user chose option 1).
- Replenish automatically, preserve current/immediate playback, prioritize manual Play Next, prevent automatic session repeats, and restore queue/source after restart.

## Findings requiring a product decision
- `Track` exposes file modification time but no library-added timestamp.
- Room `TrackDao.findRecentlyAdded` actually selects `isManaged = 0` and sorts by artist/album/track/title. It does not mean recently indexed.
- `TagUtils.isOnDownloadDir` currently means a path outside `/Music/` or containing `/Telegram/`. It is not a configured download-folder list.
- Therefore chronological New Arrivals and literal download-folder membership must not silently reuse these predicates.

## Implementation checklist
- [x] Trace current queue, database predicates, download classification, and shared queue UI.
- [x] User confirmed existing New/Downloads classification. New means unorganized; Downloads uses the current download predicate. Do not claim chronological arrival ordering.
- Source order: existing query order (New: artist/album/track; Downloads: library title order). Append up to 20 suggestions ahead; preserve all existing queued tracks. Manual Play Next/additions take priority over suggestions. Manual source freezes the queue. Clear ends the smart session. Source switches keep session exclusions until a manual queue replacement/clear.
- Persist source, supplied/suppressed track IDs, and playback anchor alongside the existing persisted queue. Automatic repeat is off for smart sources; user-requested manual playback remains possible.
- [x] Specify source ordering, session persistence, stable upcoming buffer, removal suppression, and manual priority using the confirmed definitions.
- [x] Implement and regression-test source selection and queue replenishment before integrating playback.
- [x] Expose source selector in shared queue UI (including both hosts) and persist/restore source plus session state through Room queue preferences.
- Refresh runs on a dedicated worker every 15 seconds while the service is alive; selecting a source triggers immediate refill. Queries/file checks run outside the queue lock; a revision check rejects stale results after clear/replacement/source changes. Existing playback is never started or interrupted by refresh.
- [x] Run core/app tests and debug assembly: 40 core + 51 app tests pass (six new smart-queue regressions), APK assembled, diff check clean.
- [ ] Verify playback, source-menu rendering, and queue restoration on a device. Required device-tool reference access is blocked in this session; unit coverage is not a substitute for runtime journey verification.

## Existing queue integration
- Core source of truth: `core/.../repository/QueueManager.java`.
- Shared presentation: `QueuePage.kt`, rendered from `AudioHubSheet.kt` and `DialogInterop.kt`.
- Existing manual mutations and gapless `getNextTrack()` must keep their current contracts.

## Next increment: listening history, Unplayed, and Rediscover
- User approved proceeding with the recommended next increment.
- Repository inspection found no existing completed-play count or last-played timestamp in core. PlaybackState provides track, state, position, and duration; service paths include local completion, renderer transitions, seeks, and fallback timers.
- [x] Locate playback/history integration points.
- [x] User approved defaults: 90% actual playback, Unplayed = zero recorded completions, Rediscover = completed previously and not started in 30 days.
- Proposed completion rule: accumulate actual playback time; count one completed listen per playback instance after 90% of known duration, excluding paused time and seek jumps. Unknown duration requires a reliable natural-completion event.
- Proposed Rediscover rule: at least one completed listen, no playback start in the past 30 days, oldest last-played first. Unplayed means zero completed listens recorded by MusicMate, not a claim about historical listening before tracking existed.
- [x] Persist history independently of queue state; preserve it when clearing/replacing the queue.
- [x] Implement/test playback accounting (pause, seek, repeated callbacks, renderer handoff, skip, natural completion).
- [x] Add Unplayed and Rediscover candidate queries and shared source menu descriptions.
- [x] Run core/app tests and debug assembly; report device-verification limits. 117 tests pass (50 core, 51 app, 16 server), 3 SQLite tests pass, debug APK built cleanly. Device runtime journeys remain pending.
