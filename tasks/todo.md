# Plan: Version Bump, Documentation Update, and Git Commit

## Overview
Bump application version to `3.18.13` (versionCode `121`), record detailed release notes in `CHANGELOG.md`, update project documentation, verify build, and commit all changes to git.

---

## Tasks

- [ ] **Phase 1: Application Version Bump (`app/build.gradle`)**
  - [ ] Increment `versionCode` from `120` to `121`.
  - [ ] Update `versionName` prefix to `"3.18.13-"`.

- [ ] **Phase 2: Changelog & Documentation Update (`CHANGELOG.md`)**
  - [ ] Add `[3.18.13] - 2026-08-08` section detailing:
    - Custom Audiophile Node Shapes (`shape_node_source`, `shape_node_transport`, `shape_node_target`, `shape_node_target_bitperfect`, `shape_node_connector`).
    - Playback Center Naming Symmetry (**`Audio Transport`** & **`Network Streamer`**).
    - Full-Width 1-Line Signal Path Flow redesign with compact node telemetry (`Net Streamer` / `Local Transport`).
    - Removal of legacy "Copy Report" button.

- [ ] **Phase 3: Build Verification**
  - [ ] Run `./gradlew assembleDebug` to verify compilation (`BUILD SUCCESSFUL`).

- [ ] **Phase 4: Git Commit**
  - [ ] Stage all modified & untracked files (`git add .`).
  - [ ] Commit with structured commit message (`git commit`).
