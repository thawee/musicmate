# Plan: Update README.md and CHANGELOG.md for Bluetooth Audio Suite

## Overview
Update `README.md` and `CHANGELOG.md` to document the newly added Bluetooth Audio Playback Suite features:
1. Bluetooth device product name resolution & live A2DP codec detection (`LDAC`, `AAC`, `SBC`) in Audio Route Path telemetry.
2. 1-Tap System Audio Output Switcher (`Bluetooth / System Output...`) in player target picker.
3. Auto-pause on Bluetooth/Headphone disconnect (`becomingNoisyReceiver`).

---

## Tasks Checklist

- [ ] **Phase 1: Update `README.md`**
  - [ ] Add **📶 Bluetooth Audio Playback Suite** subsection under Key Features.
  - [ ] Highlight live Bluetooth codec telemetry (`BT • LDAC`), product name resolution, 1-tap System Output Switcher, and auto-pause on disconnect.

- [ ] **Phase 2: Update `CHANGELOG.md`**
  - [ ] Update `[3.18.14] - 2026-08-09` release entry to document Bluetooth Audio Suite features.

- [ ] **Phase 3: Verification & Commit**
  - [ ] Verify build with `./gradlew assembleDebug`.
  - [ ] Commit documentation changes to Git.
