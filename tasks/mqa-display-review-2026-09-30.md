# MQA display review — 2026-09-30

Scope: current working tree, including pending edits. Reviewed relevant differences against `main`; this is a behavior review, not a review of a particular commit. No application code changed.

## Current behavior

- Detection uses `qualityInd` containing `MQA`; `MQA Studio` identifies Studio. The analyser persists these values and `mqaSampleRate`.
- Library rows below 390dp use `UnifiedAudioBadge`; wider rows use separate quality and resolution badges.
- Expanded track badges distinguish `MQA MASTER` and `MQA STUDIO`. Compact badges reduce both to `MQA` when MQA classification is reached.
- The Web UI receives `MQA` for both variants and uses green styling.

## Findings

1. **P2 — Compact rows hide MQA on 24-bit tracks.** `AudioBadges.kt:249` checks Hi-Res and PCM24 before MQA. A 24/44.1 MQA track displays `24-BIT 24/44.1`; a 24/96 MQA track displays `HI-RES 24/96`. `TrackListItem.kt:196` selects this path below 390dp. Prefer MQA before generic PCM tiers.
2. **P2 — Full-screen quality verdict drops MQA identity.** `MainActivity.java:235` only checks lossy, bit depth, and sample rate. `FullscreenStudioConsole.kt:664` displays that verdict directly, so MQA can appear as `CD Quality`, `24-BIT STUDIO QUALITY`, or `HI-RES STUDIO MASTER`, while the regular Now Playing badge correctly identifies MQA. Derive both from one classification policy.
3. **P2 — MQA badge color uses the generic PCM tier.** `AudioBadges.kt:77` and `:292` choose gold for Hi-Res/PCM24 before the MQA magenta branch. Expanded `MQA STUDIO`/`MQA MASTER` labels can therefore have a gold dot and border, inconsistent with the MQA tier in About and UI.md. Align color precedence with label precedence.
4. **P2 — Original MQA rate is omitted from native resolution displays.** `ResolutionBadge` (`AudioBadges.kt:200`), unified badges (`:258`), Now Playing anatomy (`NowPlayingPage.kt:499`), and full-screen specs (`FullscreenStudioConsole.kt:666`) use only `audioSampleRate`. Although `MainActivity.java:249` prepares both rates with `formatResolution`, the Compose track-based paths bypass it. A 24/44.1 file with `mqaSampleRate=192000` only shows 24/44.1. Display the encoded and original rates with explicit labels; neither proves the active output decoder's rate.

Studio distinction is also intentionally collapsed by compact labels and `BaseServer.java:1116`; native expanded badges preserve it. Treat this as a product consistency decision rather than a confirmed correctness defect.

## Verification

- Focused `:app:testDebugUnitTest --tests apincer.android.mmate.ui.compose.VUMeterAndBadgeTest` passed: six tests, zero failures. These cover other badge formats but no MQA cases.
- Inspected classification predicates, all active badge callers, metadata producer, Web API serialization, and native spec rendering.
- `git diff --check` passed.
- ADB reported no connected devices. Live MQA rendering and decoder/output state were not verified; findings are source-confirmed.

Recommended regression matrix: MQA and MQA Studio at 16/44.1, 24/44.1, and 24/96; differing and unavailable original sample rates; widths below/above 390dp; regular/full-screen players and Web UI.

## Fix follow-up

The four native findings are addressed by shared `AudioPresentation` label/color/resolution helpers, reused by badges and both player modes. Compact badges preserve encoded resolution; player details and accessible badge descriptions expose the known original MQA rate with explicit labels. Library metadata and full-screen spec rows wrap at large text. Web styling/Studio label choices are unchanged.

Verification: 94 app unit tests pass, debug APK builds, and eight MQA previews render and were visually inspected. New previews have no approved reference images, so screenshot comparison remains pending. The final APK installed/launched on a connected phone; a real MQA Studio preview confirmed the magenta badge and encoded-rate accessibility description. Full live-player/original-rate/resize verification remains pending. See `tasks/mqa-display-fix-plan.md` for detailed evidence.
