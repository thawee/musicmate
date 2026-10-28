# Headphone EQ

Headphone EQ supports personal listening setups for DIY pairs and imported model-specific parametric corrections. It does not measure headphones with the phone microphone. Speaker calibration and speaker sound modes remain separate.

## Create a DIY listening setup

1. Open Settings → Headphone EQ → Create DIY profile.
2. Name the pair and choose Over-ear, Earbuds or In-ear. These labels help identify the pair; they do not apply a guessed correction curve.
3. Lower the device volume. Play short left, right, alternating or centered test sounds and adjust balance until the centered sound feels centered. Balance attenuates the opposite ear, up to 12 dB.
4. Adjust bass, mids and treble within ±6 dB. Choose Both for linked adjustments, or Left/Right for independent adjustments. If the ears differ, changing a tone under Both sets that value on both sides.
5. Compare the draft with bypass using the test controls, then save. New profiles select the connected output but remain disabled until you enable its EQ switch. Editing a selected enabled profile retains its existing enablement.
6. Listen with familiar music and compare EQ on/off. Reopen Edit listening setup to refine the pair.

Each pair stores its own type, tone settings and balance, independent of the DAC. Creation and editing also work without a connected output; test sounds require a single recognized headphone output. Cancel discards the draft. Saving writes the profile atomically. Changing headphone types does not erase adjustments.

Test sounds use a low-level multitone signal with fades, limited to three seconds (four for alternating). They preview draft EQ through a separate stereo output and temporarily request exclusive audio focus. Test playback stops on cancellation, screen pause/dismissal, output change or focus loss. The app does not raise system volume. Digital signal level does not establish acoustic listening level, so start with low device volume.

Stereo music receives independent ear corrections and balance. Mono music uses the average of the two tone settings; independent balance cannot be applied to a single-channel output. Other channel layouts retain shared EQ only.

## Set up a headphone

1. Connect the USB DAC or Bluetooth headphone used for playback.
2. Open Settings → Headphone EQ, or tap the headphone indicator on the playback screen.
3. Import a parametric EQ text file, or paste its contents and enter the headphone model name. Use correction data for the exact model, pads and measurement target you intend to use.
4. Select the imported profile for the current output and enable headphone EQ. For USB, the enable control explicitly permits processing the samples.
5. Adjust bass and treble if needed. Disable EQ or select Flat to bypass headphone processing.

Selections, enablement and personal adjustments are saved per output. Bluetooth bindings use the output address; USB bindings use its reported address and product name. A DAC does not identify the headphone plugged into it: select another profile when changing headphones on the same DAC. If Android reports a different USB address after reconnecting, select the profile again. Bluetooth speakers remain eligible for speaker tuning until explicitly associated with a headphone profile.

## Import format

The importer accepts AutoEq/Equalizer APO parametric text with `Preamp` and active `PK`, `LSC` or `HSC` filters using `Fc`, `Gain` and `Q`. It preserves the entered source filename and preamp. Unsupported active commands and invalid values produce an error without replacing the saved library. Graphic EQ and convolution files are unsupported.

Files are limited to 64 KiB and 20 active filters. Preamp is limited to −30 through 0 dB, filter gain to −24 through +12 dB, frequency to 20–20,000 Hz, and Q to 0.1–12. Personal bass and treble range from −6 through +6 dB. Processing uses the more attenuating of the imported preamp and automatic EQ headroom, followed by the existing peak limiter.

## Playback and verification

Headphone EQ takes precedence over speaker correction, presets and speaker compression. A disabled headphone binding stays flat. USB defaults to DSP bypass; enabling EQ changes the samples. DSP bypass alone does not guarantee a bit-perfect Android output path.

Processing supports 16-bit, packed 24-bit, packed 32-bit and float PCM through the existing audio output pipeline. Unit tests cover import, persistence, output isolation, preamp, clipping and partial writes. Emulator checks cover the settings workflow. Real USB DAC and Bluetooth playback still require hardware listening and reconnection checks.
