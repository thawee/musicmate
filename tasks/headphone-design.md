# Headphone profiles

The user uses USB DACs and Bluetooth. Profiles describe headphone corrections supplied by the user, independent of speaker measurements and the car target. No phone-microphone headphone calibration or bundled model database is implied.

## Design

Reuse the existing PCM DSP processor, crossfade, headroom and limiter with a separate headphone profile store and route selector. A second PCM pipeline would duplicate conversion, partial-write and transition logic; reusing speaker preferences would mix unrelated corrections. The selected design shares processing, not preferences or profiles.

- `HeadphoneProfile`: validated imported parametric filters, model/display name, source filename and requested preamp.
- `HeadphoneProfileParser.parse(text, name, source)`: imports AutoEq/Equalizer APO parametric text with PK/LSC/HSC filters. Rejects malformed/unsupported active filters, non-finite or excessive values and oversized input. No guessed target or fabricated measurements.
- `HeadphoneBinding`: selected profile, enabled flag and personal bass/treble per output. USB binding includes explicit EQ consent. Disabled or missing-profile bindings produce flat bypass, never speaker fallback.
- `HeadphoneLibrary`: profiles and output bindings in an independent atomic JSON file, with import/select/delete persistence.
- `HeadphoneDsp.current`: immutable route decision (`handlesRoute`, `settings`) plus endpoint UI state. Publish updates on route/preference changes; audio blocks read memory only.
- `CarDsp.effectiveTarget()`: route-bound headphone selection first, then existing eligible speaker target. USB keeps its default bypass unless a bound headphone profile and explicit USB EQ consent are enabled.
- Give every PCM output a dormant processor, including USB, so enabling/bypassing works during playback. Add packed 24/32-bit processing alongside existing 16-bit/float handling; bypass retains input bytes and partial-write semantics.
- Preserve imported preamp by using the more attenuating of requested preamp and automatic combined-response headroom. Personal shelves participate in automatic headroom. Never stack speaker correction/modes with headphone EQ.
- Settings: separate Headphone EQ card, import document picker, profile name/selection, current-output association, enable/bypass and bass/treble controls. Explain USB sample processing and headphone-mic limitations in context.

## Validation

Prove import errors, persistence, source/preamp retention, output isolation and bypass; exercise live 16/24/32-bit/float processing and partial writes; preserve existing speaker/USB tests. Verify the settings flow on the emulator. Hardware checks remain for real USB/Bluetooth routes. Signed release requires the unavailable configured keystore.

## DIY listening setups (2026-10-28)

Manual profiles store a type label, independent left/right bass/mids/treble and attenuation-only balance per named pair. Imported profiles retain their existing format; `manual` defaults to null. A manual profile has no imported bands or preamp. Its stereo settings build each ear’s tone filters, with shared headroom based on the largest boost on either side. Mono uses averaged tone values; other channel layouts retain shared EQ only.

The guided dialog edits an isolated draft. Tests use that draft or flat bypass, while music settings change only on Save. Saving a new pair selects it for the connected output without enabling EQ; editing retains enablement only for that same selected pair. Offline saved profiles can be reopened from the settings card.

The standalone test player serializes sessions with a mutex, requests transient exclusive focus, checks the exact selected headphone route during nonblocking writes, and releases its output on completion, cancellation, stop or error. Test sounds contain a bounded low-level multitone with fades. UI lifecycle cleanup stops tests on page/route changes, pause and dismissal; there is no microphone capture or system-volume increase.
