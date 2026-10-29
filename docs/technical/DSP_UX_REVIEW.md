# Playback DSP and Audio tuning UX review

Reviewed 2026-10-29 using UI/UX Pro Max, current Compose source, and fresh emulator captures of quick tuning, full tuning, and full tuning at 150% text. Findings below describe the pre-redesign implementation; source line numbers refer to that snapshot.

## Implementation follow-through

The approved redesign now shares status and mode controls across quick/full tuning and legacy speaker modes. Mode selection preserves bypass; USB processing still requires explicit enablement. No correction removes an assignment, while bypass preserves it. Playback has a 48dp tuning entry and wraps its output controls when space is tight. Full tuning puts mode selection before the EQ curve, moves creation type into calibration, and groups profile utilities under Manage profiles. Mode descriptions, Edit Custom, curve preview status and Night dynamics guidance are included.

Level-matched comparison and DLNA processing remain outside this change. See [Audio tuning](AUDIO_TUNING.md) for current behavior and `tasks/todo.md` for validation evidence.

## Recommendation

Make tuning feel like a listening console: show what is audible now, let users change it quickly, and keep profile creation within reach. The current quick/full split is sound. The biggest weaknesses are inconsistent activation behavior, ambiguous bypass language, and a full editor that puts the graph ahead of listening controls.

Keep Music Mate's charcoal surfaces and restrained gold accent. Clear hierarchy, legible labels and predictable controls will add more quality than decorative knobs, glow or extra animation.

## Prioritized findings

| Priority | Finding and evidence | Recommended behavior |
| --- | --- | --- |
| P0 | Selecting a legacy speaker mode enables processing, while selecting a headphone mode preserves its enabled state. See `CarDspControls.kt:94` and `audio/headphones/HeadphonePreferences.kt:81`. | Mode selection changes the selected mode on every output. A separate explicit enable/bypass control governs audibility. Preserve explicit USB EQ authorization. |
| P0 | The switch represents saved enablement while “EQ active/bypassed” represents effective processing. These can legitimately differ, but the reason is not shown (`QuickTuningControls.kt:50`). | Distinguish **Active**, **Bypassed**, and **Unavailable**. Show a reason such as “Select a profile” or “Connect a compatible output”; never imply selected settings are audible when they are not. |
| P1 | “Flat / bypass” clears the profile assignment and disables EQ (`CorrectionProfileSelector.kt:73`, `HeadphonePreferences.kt:60`). | Name profile removal “No correction”. Keep bypass separate and preserve the selected profile and mode for comparison. Explain that Neutral retains calibration; it does not mean unprocessed playback. |
| P1 | The playback chip uses an approximately 25dp visual height, 10–11sp labels and a 12dp icon; its off label hides the selected mode (`HeadphoneControls.kt:66`). | Use a readable “Sound · Vocal” entry with an explicit On/Bypassed state. Guarantee a 48dp touch area and check neighboring hit bounds. Existing merged accessibility semantics are a useful foundation. |
| P1 | The full editor puts its response graph before sound-mode controls (`HeadphoneControls.kt:407`). Calibration, imports and profile details extend the settings stack. | Put current output/profile/status first, then sound modes, then the curve. Group calibration and profile management below. Move deletion and import utilities into secondary management actions. |
| P2 | Quick mode chips have no explicit vertical row spacing, and selecting Custom offers no nearby adjustment shortcut (`QuickTuningControls.kt:59`). | Use consistent spacing, a clear selected treatment, and one line describing the selected mode. Add “Edit Custom” when applicable. Preserve wrapping and scroll access at large text sizes. |
| P2 | “Frequency response” shows calculated EQ settings, not a fresh acoustic measurement. The curve also cannot communicate Night's dynamics processing (`DspTuningComponents.kt:94`). | Label it “EQ curve”; mark it as a preview while bypassed. Give Night a dynamics description and identify averaged left/right curves. Keep measurement provenance distinct from the processing preview. |

The small playback entry needs touch-bound verification; its visual size alone does not prove its actual Compose touch target fails. No body-text contrast failure or inaccessible calibration row was observed in the inspected captures.

## Proposed playback flow

1. Playback shows **Sound · Vocal · On**, or **Sound · Vocal · Bypassed**.
2. Tapping opens quick tuning with the connected output, actual processing status and enable switch at the top.
3. A saved-profile selector shows the profile name and a small Speaker/Headphone label. Keep both types in the same library; disable incompatible choices with a reason.
4. Descriptive cards show all seven modes and their sound character before selection, with a longer explanation for the selected mode. Custom exposes an edit shortcut.
5. Compact “Manage tuning” in the fixed header opens the editor; creation stays in the editor. Profile creation offers Microphone, Listening and AutoEQ with applicability explained for the chosen output.

Opening tuning, switching a mode, or bypassing must preserve playback position and the user's selected profile. Use one explicit bypass control for comparison; do not clear the profile. A future level-matched comparison would require DSP work and validation, rather than merely a new UI button.

## Proposed full-editor hierarchy

1. **Current sound:** output, profile, audibility status and bypass control.
2. **Sound mode:** the same mode names and activation behavior as quick tuning, with Custom controls inline when selected.
3. **EQ curve:** correction plus mode, clear preview state, and concise headroom/dynamics information where relevant.
4. **Profile:** calibration source, relevant measurement limitations, rename/manage actions and a prominent Create profile action.
5. **Advanced:** imports, filter details and destructive actions behind deliberate secondary entry points.

Speaker/headphone should describe the profile or a library filter, rather than force a separate top-level workflow before the user sees the current sound. Creation still needs an output type because microphone suitability and correction behavior differ.

## Interaction and accessibility requirements

- Keep the fixed header, explicit close action and stable scrolling already covered by sheet tests. Do not reintroduce sheet dragging that competes with body scrolling.
- Guarantee 48dp Android touch targets, 8dp spacing between wrapped mode rows, and readable helper text. Avoid using 10sp eyebrow labels for essential state.
- Expose mutually exclusive mode selection and selected state to accessibility services. Announce meaningful state changes without repeating announcements during slider movement.
- Never rely on gold alone to indicate active processing. Pair it with “Active” text; selected mode and audible processing are separate states.
- Preserve USB's explicit “changes samples” disclosure. Do not imply bit-perfect output while EQ is active.
- Keep unsupported external routes truthful. The current playback tuning entry is hidden for DLNA/external-app playback; this review does not propose claiming DSP support on those routes.

## Delivery order and acceptance checks

1. Unify activation and status semantics, including bypass/profile separation. Verify speaker, wired headphone and USB behavior with EQ on/off, missing profile, disconnected output and incompatible profiles.
2. Polish the playback entry and quick panel. Confirm saved-profile and mode changes are reachable without opening calibration, with clear feedback while bypassed.
3. Reorder the full editor and group profile management. Verify Custom, all three creation methods, long profile names, narrow screens, TalkBack and 150% text.

Review verification: `QuickTuningUiTest` and `AudioTuningSheetUiTest` each passed in separate emulator runs (four tests each). Fresh captures were inspected at normal text and at 150% text for the full editor. These checks establish the reviewed layouts and existing behavior; they do not validate the proposed redesign or physical audio quality.

Skill search note: preset-selection searches did not return a relevant database match. Recommendations use the skill's native accessibility/interaction checklist and verified repository behavior instead.

## Descriptive-card implementation

Quick and full tuning share sound cards with visible tonal descriptions, a restrained gold border/tint and a checkmark for selection. Cards use two columns where space permits and one at larger text sizes. Current sound groups the output, correction profile and effective processing state. Quick tuning keeps a compact Manage tuning action in the fixed header and omits profile creation from its body. Night explicitly describes reduced quiet/loud differences; Neutral preserves the selected calibration.

Validation: 353 unit tests and 24 focused UI tests pass; all three signature checks also pass with system text at 150%. Fresh emulator quick/full/large-text captures inspected. Installed on the physical phone with data retained. After unlock, fresh quick/full captures confirmed readable descriptions and selected-state styling; the fixed editor action retained identical bounds before and after scrolling. Full-editor calibration methods and profile management were reachable, and the close action stayed pinned. Returned to playback with the existing profile, Vocal mode and Active state preserved.

Hierarchy refinement: removed the prominent creation row and large full-editor footer after physical-phone review. Quick tuning focuses on saved profiles and sound modes; one compact header action opens the full editor for creation and management.

Physical-phone follow-up: inspected the simplified quick panel after unlock. No Create profile row or Full audio tuning footer remains. Manage tuning stays at the same header position during scrolling and opens the full editor. The saved AL5125SH_BT profile, Vocal mode and active processing were preserved.
