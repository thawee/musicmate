# Listening speech provenance

Voices: **Open Speech Repository** (VoIP Troubleshooter), American English Harvard sentences.
Retrieved 2026-10-29 from https://www.voiptroubleshooter.com/open_speech/american.html.

| Bundled asset | Original recording | Crop in source | Source SHA-256 |
| --- | --- | --- | --- |
| female_voice.pcm | [OSR_us_000_0010_8k.wav](https://www.voiptroubleshooter.com/open_speech/american/OSR_us_000_0010_8k.wav) (female) | 0.45–3.45 seconds | a4bf9becd046d7aedb6d05b6e12347a6294a44f74d263089c636fb0a2b1e6561 |
| male_voice.pcm | [OSR_us_000_0030_8k.wav](https://www.voiptroubleshooter.com/open_speech/american/OSR_us_000_0030_8k.wav) (male) | 1.95–4.95 seconds | d3e1cfba98a44ddfc6e99508e663794c8dc41bdb2baced224f779c453143b622 |

The source page's conditions permit use in testing, research, development, marketing and other reasonable applications; copying, downloading, broadcasting, modification and incorporation into websites or test equipment are expressly allowed. They require identification of the source as “Open Speech Repository”. This attribution is retained here and in the listening interface. No additional license name or public-domain claim is implied.

Each bundled recording is three seconds of mono signed PCM16 little-endian at 48,000 Hz (288,000 bytes; no header). Original recordings are mono PCM16 at 8,000 Hz. Processing crops the source, upsamples with 32-tap Hann-windowed sinc interpolation, fades both ends over 20 ms and normalizes the peak to 85% full scale. Playback adds a 10 ms fade and normalizes its source peak before applying the player's quiet output gain.

Upsampling preserves the original approximately 4 kHz bandwidth limit; voices are useful for speech body and presence, **not** as treble references. The original instrument phrases in `ListeningSamples.kt` synthesize directly at 48 kHz; shaker/cymbals provide upper-frequency content. No recording or sample is fetched at runtime.

To rebuild, download the linked originals into a local directory under their exact filenames, then run:

```sh
python3 scripts/build_listening_speech.py /path/to/downloaded/osr
```

The script uses only Python's standard library and validates source hashes before transforming them. It prints output hashes for reproducibility. The synthesized instrument compositions and synthesis code were created for MusicMate; they contain no external music recordings.

Bundled SHA-256: female `f5aae37ec727553af85ffcded17c61443f686482d5c54a7dcab209a6292205d5`; male `d47f70947a8c1e62398f83a321b266873bdb6700168a1404b7e0eb95a8f9ed0e`.
