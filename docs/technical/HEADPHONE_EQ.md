# Headphone EQ

Settings → Audio tuning → Open audio tuning and the Now Playing tuning chip open the same [audio tuning sheet](AUDIO_TUNING.md). One dropdown lists saved speaker and headphone/earphone corrections, labeled by output type and method. The Speaker and Headphone tabs choose the correction type. Dropdown entries use the full Headphone / earphone label. Bluetooth and USB identify the transport, so choose the type of device attached to it. A built-in phone speaker cannot preview a headphone setup.

The calibration methods are Microphone, Listening and AutoEQ. Microphone and Listening support both output types. AutoEQ searches measured headphone models; for speakers its button imports a measured Equalizer APO correction. The headphone database does not contain speaker room profiles. Existing speaker measurements remain in their original files and appear alongside listening and imported profiles.

## Measure headphones with a microphone

1. Choose Headphone → Microphone. Connect the headphone output and a suitable microphone/fixture.
2. Enter the fixture/coupler name and confirm the fixture is fitted. Select the recording microphone. A phone microphone held near an earcup is not a suitable measurement setup.
3. Import the target response for that fixture and microphone chain. Use CSV or TSV with two columns, `frequencyHz,targetDb`, increasing frequencies and coverage from at least 100 Hz through 8 kHz. Targets are limited to 64 KiB. Do not substitute the speaker room target. See the [miniDSP explanation of fixture targets](https://docs.minidsp.com/product-manuals/ears-pro/technical/target-curves.html).
4. Start and measure five repeatable reseats. Keep the same input and output throughout; a route change rejects the capture. This produces a shared fixture correction, not independent acoustic calibration of both ears. Listening creates a separate profile with per-ear preferences.
5. Save and check the result. The saved profile records the fixture, target filename and microphone. USB processing still requires its explicit EQ switch.

Headphone fitting starts at 100 Hz or the measurement's reliable lower limit, whichever is higher. Built-in, USB and wired microphones are selectable. Physical fixture accuracy and microphone routing need validation on the actual hardware; emulator tests do not establish acoustic accuracy.

## Create a DIY listening setup

1. Open Settings → Audio tuning → Open audio tuning → Headphone → Listening. Choose Speaker → Listening for a speaker setup.
2. Name the pair and choose Over-ear, Earbuds or In-ear. These labels help identify the pair; they do not apply a guessed correction curve.
3. Lower the device volume. Play short left, right, alternating or centered test sounds and adjust balance until the centered sound feels centered. Balance attenuates the opposite ear, up to 12 dB.
4. Guided tones progress through bass (100 Hz), mids (1 kHz) and treble (8 kHz). Play left, right, alternating or centered at each step and adjust the selected ear within ±6 dB. Recheck the headphone fit before compensating a difference. Replay or skip any step; skip a faint or inaudible tone rather than raising the volume to chase it. Manual controls show all three bands and optional starting templates. Choose Both for linked adjustments, or Left/Right for independent adjustments. If the ears differ, changing a tone under Both sets that value on both sides.
5. Compare the draft with bypass using the test controls, then save. Saving with a compatible connected output selects the profile and enables EQ for non-USB outputs. USB requires enabling its EQ switch to permit processing; editing an already enabled USB profile retains its enablement.
6. Listen with familiar music and compare EQ on/off. Choose Edit listening setup below Sound mode to refine the pair.

Each profile stores its output type, tone settings and balance, independent of the DAC. Creation and editing also work without a connected output; test sounds require a single compatible local output. Speaker tests adjust channels from the listening position; a mono speaker cannot give separate left/right comparisons. Cancel discards the draft. Saving writes the profile atomically. Changing headphone types does not erase adjustments.

Test sounds use a low-level single-frequency signal in Guided tones and a multitone signal in Manual controls and the balance check. Signals fade in and out and last three seconds (four for alternating). They preview draft EQ through a separate stereo output and temporarily request exclusive audio focus. Test playback stops on cancellation, screen pause/dismissal, output change or focus loss. The app does not raise system volume. Digital signal level does not establish acoustic listening level, so start with low device volume.

Compare the ears at the same frequency, not the loudness of different frequencies. This guide records listening preferences, not a measured flat response or a hearing threshold. Human equal-loudness judgments depend on frequency and level; see [ISO 226](https://www.iso.org/standard/83117.html). Speaker microphone calibration remains separate; headphone acoustic measurement requires suitable measurement equipment or an imported model correction.

Stereo music receives independent ear corrections and balance. Mono music uses the average of the two tone settings; independent balance cannot be applied to a single-channel output. Other channel layouts retain shared EQ only.

## Set up a headphone

1. Connect the USB DAC or Bluetooth headphone used for playback.
2. Open Settings → Audio tuning → Open audio tuning → Headphone, or tap the tuning indicator on the playback screen.
3. Import a parametric EQ text file, or paste its contents and enter the headphone model name. Use correction data for the exact model, pads and measurement target you intend to use.
4. Select the imported profile for the current output and enable headphone EQ. For USB, the enable control explicitly permits processing the samples.
5. Choose a Sound mode over the saved correction. Neutral keeps the correction alone; Bass, Vocal, Lively, Relax and Night add the same curves used for speakers; Night also compresses louder passages. Custom uses the saved bass and treble sliders. Disable EQ or select Flat / bypass in the profile selector to bypass headphone processing.

The shared modes are Neutral, Bass, Vocal, Lively, Relax, Night and Custom. Sound modes are saved per output and apply to both imported corrections and listening setups. They preserve the correction's shared/per-ear bands, mono tuning, balance and imported preamp. Automatic headroom accounts for the combined correction and mode. Neutral is different from bypass: the selected correction remains active. Changing a mode never enables EQ or grants USB processing consent. Existing installations retain their custom trims until a named mode is selected; selecting Custom restores those trims. The unified output profiles use +/-6 dB Custom trims; the legacy speaker mode controls retain +/-4 dB. Starting templates remain separate from sound modes.

Selections, enablement and personal adjustments are saved per output. Bluetooth bindings use the output address; USB bindings use both the reported address and product name (`usb:<address>:<name>`). A DAC does not identify the headphone plugged into it: select another profile when changing headphones on the same DAC. If Android reports a different USB address after reconnecting, select the profile again. Bluetooth speakers remain eligible for speaker tuning until explicitly associated with a headphone profile.

## Import format

The importer accepts AutoEq/Equalizer APO parametric text with `Preamp` and active `PK`, `LSC` or `HSC` filters using `Fc`, `Gain` and `Q`. It preserves the entered source filename and preamp. Unsupported active commands and invalid values produce an error without replacing the saved library. Graphic EQ and convolution files are unsupported.

Files are limited to 64 KiB and 20 active filters. Preamp is limited to −30 through 0 dB, filter gain to −24 through +12 dB, frequency to 20–20,000 Hz, and Q to 0.1–12. Personal bass and treble range from −6 through +6 dB. Processing uses the more attenuating of the imported preamp and automatic EQ headroom, followed by the existing peak limiter.

## Playback and verification

Headphone EQ takes precedence over speaker correction and speaker mode preferences; only the headphone Night mode enables compression on that route. A disabled headphone binding stays flat. USB defaults to DSP bypass; enabling EQ changes the samples. DSP bypass alone does not guarantee a bit-perfect Android output path.

Processing supports 16-bit, packed 24-bit, packed 32-bit and float PCM through the existing audio output pipeline. Unit tests cover import, persistence, output isolation, preamp, clipping and partial writes. Emulator checks cover the settings workflow. Real USB DAC and Bluetooth playback still require hardware listening and reconnection checks.
