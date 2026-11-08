# Audio tuning UX review — 2026-11-06

Scope: current Compose quick/full tuning, correction selection and Auto/manual sound modes. Source review, not a branch/change review. No application code changed.

Evidence: QuickTuningControls.kt, CorrectionProfileSelector.kt, TuningListeningControls.kt, AutoSoundControls.kt, DspTuningComponents.kt, HeadphoneControls.kt; docs/technical/AUDIO_TUNING.md and project lessons. Material 3, MusicMate tokens and adaptive mode cards are already used.

## Ranked recommendations

1. Clarify the profile prerequisite. QuickTuningControls.kt:29-31,46 disables modes without a compatible profile; CorrectionProfileSelector.kt:77 offers No correction. Explain why modes are unavailable. Consider permitting sound modes without correction as a separate DSP behavior change requiring engine verification.
2. Give empty state one direct Create profile action, opening setup in the full editor. QuickTuningControls.kt:50 currently directs users through Manage tuning. Keep calibration forms out of quick tuning.
3. Make Auto understandable at a glance. AutoSoundControls.kt:27-46 switches between Auto result and manual cards. Show e.g. Auto → Vocal with the matching reason, rename Keep for this session to Lock this mode, and preserve Resume Auto. An optional temporary mode override would require explicit session semantics.
4. Simplify profile selection. CorrectionProfileSelector.kt:77-89 mixes compatible and disabled incompatible profiles in one menu. Show compatible profiles first, selected checkmark, full names, and a secondary Other profiles group. Keep incompatible profiles available for offline management.
5. Reduce full-editor competition. HeadphoneControls.kt:406 onward always presents calibration methods. Group under Create profile; keep response preview and profile management secondary. Use one Advanced tuning destination; Edit Custom should ideally focus its actual controls.
6. Clarify state wording. TuningListeningControls.kt:33-40 uses Active/Bypassed/Unavailable. Prefer EQ on / EQ off / Unavailable plus the existing actionable reason. USB enable must retain explicit consent and sample-processing explanation.

## Preserve

Mode descriptions before selection, checkmark plus radio semantics, adaptive single-column layout at larger text, fixed close/manage controls, shared profile model and explicit USB consent.

## Verification limits

adb devices -l returned no attached devices. No rendered layout, contrast, TalkBack, motion, clipping or touch behavior verified. Source shows minimum targets, semantic selection, named close controls and scrollable content. Runtime-dependent domains remain unverified. No tests run because this is a read-only product review; user changes in the working tree were preserved.

Suggested next implementation: empty-state creation action, clearer status labels and compatible-first profile selection; then validate on Android at narrow width and large text. Treat mode-without-profile support as a separate functional change.

## Implemented follow-up

Implemented recommendations 2, 4 and the status wording in 6. Quick empty state opens a creation-method chooser; compatible profiles come first with selected checkmarks, and selected names wrap. Status labels use EQ on/off in quick/full tuning and the playback entry. No DSP behavior or USB consent changes. Runtime follow-up passed on API 34, including creation/cancellation at 150% text; see tasks/todo.md for evidence.
