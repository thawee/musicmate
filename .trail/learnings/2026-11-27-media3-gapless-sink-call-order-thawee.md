---
title: Media3 gapless sink call order
author: Thawee
date: 2026-11-27
branch: play-storage
cost: { minutes: 1, tokens: 0 }
id: l-28cf8c00
scope: repo
status: active
src: agent
touches:
  - app/src/main/java/apincer/android/mmate/audio/LookaheadAudioSink.kt
---

At a gapless track change Media3 1.11.1 calls the sink in this order: handleDiscontinuity, then configure with the same format (encoderDelay and padding 0) about 28 ms later while the limiter still holds about 6 ms of frames, then the first new-track handleBuffer. StreamGain already holds the new track's gain when handleDiscontinuity arrives. So LookaheadAudioSink defers that configure and, if the format is unchanged, continues the stream instead of draining and resetting the EQ and limiter, which clicked. GaplessSinkSequenceProbe confirms it for 16-bit FLAC, 24-bit/96 kHz FLAC and 16-bit WAV on the API 36 emulator. Lossy formats with encoder delay still take the reset path.
