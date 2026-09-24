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
- [x] Run unit tests and deploy debug build to physical device (`RFCY21CLTDY`)

