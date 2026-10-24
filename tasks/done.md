# Done

Sections moved from todo.md once every item was ticked.

## Car Audio UI/UX pass (2026-10-24)
Phone screenshots showed: Off mixed into the mode list, selection by highlight only, no sign of the active car correction in the dialog, wizard is a wall of text with the action at the end of the scroll, no position picture or countdown, grey Delete.
- [x] U1. Car DSP dialog: on/off switch in the header, mode rows with radio marks (dimmed while off; picking one turns DSP on), curve of the applied EQ, car correction row with Calibrate, Done button. Done; curve scale ±6 dB, wider only when needed; slider tick dots hidden.
- [x] U2. Wizard: top bar with back arrow, 4-step strip (Prepare, Measure, Review, Check), actions pinned at the bottom, top-down picture of the 5 positions, countdown bar while measuring, graph axis labels, name prefilled with the car's Bluetooth name. Done; Result, Verifying and Done screens compile but were not seen on the phone (they need real sweeps).
- [x] U3. Settings card: radio marks, destructive Delete icon and confirm in the theme's error colour. Done with a new `Status.Destructive` token; rows not seen on the phone yet (no saved car).
- [x] U4. Tests both flavors, release build, install, screenshot every screen. Done 2026-10-24: 193 tests per flavor, release built, installed, dialog (Reference, Drive, Custom), checklist, position 1 and the empty Settings card screenshotted. Review fixes: back arrow and system back ask before dropping measured spots or an unsaved result (Stop in the destructive colour); closing mid-sweep cancels the capture (emulator: recording stopped after 8 s, not 13.5 s); dimmed saved mode announces "Saved, Car DSP off"; at font scale 2 the stage labels became one "Step n of 4 · Name" line and the number circles grow with the text. Emulator: `SupportScreensAccessibilityTest` 3/3 (needed "Don't keep activities" off on the AVD); Settings rows and delete confirm seen with two fake profiles. Not seen: Result, Verifying and Done (need real sweeps).

## Several cars (2026-10-24)
Owner has several cars. Today one profile is active for every car, the chip shows only the mode, and cars swap only in Settings.
- [x] C1. Each car remembers its own sound mode and Custom trims (preferences keyed by profile id; "No correction" keeps the global ones; a new car starts from the current mode). On/off stays global. Done; `CarDspPreferences.select/load/save` take SharedPreferences so they are JVM-tested.
- [x] C2. Car DSP dialog: a CAR list (No correction plus every saved car, radio rows) to swap cars, and "Calibrate a car". Done; picking a car turns Car DSP on, like picking a mode.
- [x] C3. Chip shows the active car's name: "MU-X · DRIVE"; "CAR · DRIVE" with no car; long names cut with an ellipsis. Done; the chip takes a smaller share of the row than the target pill (weights 0.5 and 1), so a long name is cut first.
- [x] C4. Auto-switch: when a Bluetooth HFP device connects whose address matches a saved car, that car becomes active (also checked at start). No match changes nothing, since an unknown device may be headphones; the "unknown car falls back to No correction" rule needs slice 5's car confirmation. Built, not yet seen working (`CarAutoSelect`, started and released with the player; emulators have no Bluetooth). Review fixes: checks BLUETOOTH_CONNECT and logs when missing, receiver exported (protected broadcast from the Bluetooth stack), no crash if the proxy callback lands after release. Test without a car: calibrate with a hands-free Bluetooth speaker, pick No correction, reconnect the speaker, watch `logcat -s CarDsp`; then kill and reopen with it connected.
- [x] C5. JVM tests (per-car mode keys, address match), both flavors, release build, install, screenshots. 196 tests per flavor (2 new per-car, 1 address); Medium_Phone emulator with sample cars: list, swap, MU-X Drive and Mazda Vocal kept, long name, Settings follows. Installed on the phone 2026-10-24 (update in place, signer and all-files access verified, BLUETOOTH_CONNECT granted, app starts).
- [x] C6. Phone test of auto-switch with AL5125SH_BT as stand-in car, 2026-10-24. `media_route_log.txt`: 12:30:53 "disconnected, back to No correction", 12:31:01 "connected, its correction is now active"; chip and output pill follow. Disconnect rule added at the owner's request (also applied at start-up when the active car is not connected).

## Rename to Speaker Tuning (2026-10-24)
Owner chose "Speaker Tuning": the feature now serves any measured speaker (car or Bluetooth speaker), not only cars. Only on-screen and doc wording changes; class names, the `car_dsp` preferences and `car_profiles` folder stay so saved speakers and modes survive.
- [x] S1. Now Playing chip and dialog: "Speaker Tuning", section SPEAKER, "Tune a speaker"; chip fallback TUNING. Seen on the emulator.
- [x] S2. Settings card SPEAKER TUNING; wizard copy generic (car or room checklist, Front instead of Windscreen, speaker name); error hints. Wizard start screen seen on the emulator; Settings card not opened.
- [x] S3. Privacy policy and CAR_AUDIO_DSP.md note the new name.
- [x] S4. 196 tests per flavor, release built and installed on the phone 2026-10-24 (signer and all-files access verified).

## Rename a tuned speaker (2026-10-24)
Owner wants a friendly, editable name per speaker. The profile already has a `name` (set at save, prefilled with the Bluetooth name) but it cannot be changed afterwards.
- [x] N1. `CarDspPreferences.renameProfile` saves the new name and updates the chip at once when that speaker is active; JVM test `renameKeepsTheModeAndUpdatesTheActiveName`.
- [x] N2. Settings card: a Rename button per speaker opens a dialog with the name (1 to 40 characters) and the Bluetooth device name underneath. Opens focused with the old name selected; emulator rename to "Weekend MU-X" saved to the profile file.
- [x] N3. Speaker rows in Settings and the Now Playing dialog show the Bluetooth name when it differs from the friendly name. Seen in Settings on the emulator.
- [x] N4. 197 tests per flavor, release built and installed on the phone 2026-10-24 (signer, all-files access verified); screenshots from the emulator since the phone was asleep.
