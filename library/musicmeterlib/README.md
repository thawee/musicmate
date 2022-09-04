use https://github.com/jiixyj/libebur128
FOR %a IN ("*.mp3") DO (ffmpeg -i "%a" -af ebur128=peak=true -f null - > %a-output.txt 2>&1)
Integrated loudness:
I:         -12.9 LUFS
Threshold: -23.3 LUFS
Loudness range:
LRA:        11.2 LU
Threshold: -33.3 LUFS
LRA low:   -20.7 LUFS
LRA high:   -9.5 LUFS
True peak:
Peak:        0.2 dBFS


LUFS (Loudness Units Full Scale, for absolute loudness measurements), or LU (Loudness Units, for relative loudness measurements).

LRA is a reading of dynamic range. The typical range is from 5 (very little dynamic range, like a commercial or aggressive club mix) to 15 (a live acoustic recording). I use this as a reality check to the LUFS reading. Most of my music has a dynamic range rating between 11 and 8, which leans toward a somewhat limited dynamic range so the music “pops” a little more. However, I noticed one song was a 7, so I looked at it further, even though, subjectively, it seemed to be the same level as the other tracks. It turned out that it was a dance mix without much of an inherent dynamic range, so I didn’t make any changes — but it’s helpful to get that kind of feedback.

TP stands for “true peak,” and it takes intersample distortion into account (i.e., the distortion that can occur when reconstructing the signal through a D/A converter’s smoothing filter — even though a peak meter that measures sample levels doesn’t show distortion). Well, I’m not a fan of distortion, so I want the TP reading for both channels to be -0.1 or lower. Although there were only a couple of tracks with elevated TP readings (e.g., +1.2dB or +0.6dB), the fix was reducing the track level and then readjusting the amount of maximization to bring up the perceived level a bit to compensate for the reduced overall level.

RMS gives the old-school RMS reading, which I still believe is important. It’s another way of gauging the signal’s dynamics, so I look for consistency among the various tracks. If anything deviates by more than a few dB, I check back to the original track to verify whether the difference is intentional or not.


===
Yes - LRA (Loudness Range) is the rough equivalent to the dynamic range number spit out by MAAT.

https://ask.audio/articles/demystifying-the-confusion-around-loudness-metering-levels

It uses a different algorithm so it doesn't always line up perfectly, but EBU R128 is generally considered a better standard.
