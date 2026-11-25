# Limiter intervention measurements

Measured on 2026-11-25 with `LimiterInterventionTest` against the unchanged
`TruePeakLimiter`: -1 dBTP nominal ceiling, -2.2 dB detector ceiling (1.2 dB
reconstruction allowance), linked gain and 5 ms lookahead plus FIR context.

The 48 kHz mono fixtures contain 4,800 frames (100 ms), start immediately, and
are fully drained. Tones use zero starting phase except the 12 kHz tone, which
uses π/4 to create intersample peaks above its sample peaks. The impulse has
amplitude 2 at frame 2,400. Reconstruction includes zero-extended stream edges
and uses a separate 16-phase, 192-tap Blackman-windowed sinc evaluator, including
the original sample peaks. It differs from the production four-times detectors.

| Fixture | Input sample dBFS | Output sample dBFS | Reconstructed output dBTP | Max applied attenuation dB | Frames attenuated >0.01 dB |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 kHz, -6 dBFS | -6.0000 | -6.0000 | -5.9984 | 0.0000 | 0.00% |
| 1 kHz, -3 dBFS | -3.0000 | -3.0000 | -2.9984 | 0.0000 | 0.00% |
| 1 kHz, -2.2 dBFS | -2.2000 | -2.2067 | -2.2051 | 0.0067 | 0.00% |
| 1 kHz, -1 dBFS | -1.0000 | -2.2067 | -2.2051 | 1.2067 | 100.00% |
| 1 kHz, 0 dBFS | 0.0000 | -2.2067 | -2.2051 | 2.2067 | 100.00% |
| 12 kHz intersample tone | -3.0103 | -5.2623 | -2.2013 | 2.2896 | 100.00% |
| +6.0206 dBFS impulse | 6.0206 | -2.2000 | -2.2000 | 8.2206 | 56.00% |

Attenuation is measured from each emitted frame's actual limiter gain, including
drain. The fraction counts silent frames too, so the impulse's 56% describes
envelope duration rather than the proportion of audible material changed.

The -6 and -3 dBFS 1 kHz fixtures pass sample-for-sample unchanged. Additional
-12 dBFS tones at 1 kHz, one-quarter sample rate, and 0.45 sample rate pass
bit-exactly at 44.1, 48, 96, and 192 kHz, including the drained tail (20 ms each).
The -1 dBFS 1 kHz fixture demonstrates approximately 1.2 dB attenuation caused
by the conservative allowance even though its reconstructed input is near the
nominal ceiling. These results quantify that tradeoff; they do not justify
reducing the allowance across arbitrary audio.

Verification: both tests passed with no failures or errors using JDK 21 and:

```sh
./gradlew :app:testGithubDebugUnitTest --tests '*LimiterInterventionTest' --console=plain
```

JUnit evidence: `app/build/test-results/testGithubDebugUnitTest/TEST-apincer.android.mmate.audio.dsp.LimiterInterventionTest.xml`.
The test asserts exact quiet/headroom preservation, complete frame drainage,
intervention on overload fixtures, and reconstructed output at or below -1 dBTP.

Both the production detector and the independent evaluator have finite filters
and phase counts. This deterministic fixture set is neither a mathematical
bound on ideal sinc reconstruction nor a representative music-library survey.
These JVM measurements do not exercise Android devices, downstream resampling,
DAC reconstruction, or lossy encode/decode overshoot. Device and listening
validation remain necessary before changing the production safety margin.
