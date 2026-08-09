# Plan: 4 UI/UX Enhancements for Music Center Hub

## Overview
Implement 4 Lead UI/UX design refinements in `AudioHubBottomSheet`:
1. Remove nested "box-inside-box" background for a seamless frosted glass surface.
2. Apply 12dp floating elevation clearance above bottom dock.
3. Refine idle state typography & Audio Route Path placeholder chips.
4. Apply premium audiophile gold active tab styling.

---

## Tasks Checklist

- [x] **Phase 1: Seamless Surface Layout (`sheet_now_playing_queue.xml`)**
  - [x] Remove `android:background="@drawable/bg_rounded_surface_dark"` from `sheet_now_playing_card`.
  - [x] Set background to `@android:color/transparent` and remove inner card margins.

- [x] **Phase 2: Floating Clearance & Ambient Glow (`AudioHubBottomSheet.java`)**
  - [x] Update `bottomNavMargin` clearance: `+ (int)(12 * density)`.
  - [x] Apply `applyAmbientGlow` to root `sheet_audio_hub` view so artwork glow blends into the outer frosted glass surface.

- [x] **Phase 3: Refine Idle State Telemetry & Typography**
  - [x] Clean up duplicate idle subtext: Title `"Music Mate Ready"`, Subtitle `"Select a song to start playback"`.
  - [x] Set idle Audio Route Path placeholder chips: `[ Source ]` ➔ `[ Transport ]` ➔ `[ Select Player ▾ ]`.

- [x] **Phase 4: Tab Bar Styling & Build Verification**
  - [x] Apply active gold text tint to active tab pill.
  - [x] Verify build with `./gradlew assembleDebug`.
  - [x] Commit changes to Git.
