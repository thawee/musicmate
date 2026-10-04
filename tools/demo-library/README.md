# Demo library

Generates the music library used for the README and User Guide screenshots. The artists, music and covers are all made by these scripts, so screenshots of them can be published without any rights question and without showing a personal library.

The library has 19 tracks in 6 albums. It covers CD (16/44.1), 24-bit (24/48 ALAC, 24/88.2) and Hi-Res (24/96, 24/192) files, with loud, medium and wide dynamics. In the app they measure about DR7, DR10 to 12 and DR12 to 15.

## Requirements

- macOS, for `afconvert` (FLAC and ALAC encoding) and `swift` (covers).
- Python with `numpy` and `mutagen`.

## Usage

```bash
python3 -m venv /tmp/demo-venv && /tmp/demo-venv/bin/pip install numpy mutagen
/tmp/demo-venv/bin/python make_demo_library.py /tmp/demo-library
adb -s emulator-5554 push /tmp/demo-library/. "/sdcard/Music/MusicMate Demo/"
```

Then scan in MusicMate and wait for analysis to finish before taking screenshots.

Crop the 63 px status bar from emulator screenshots and resize them to 540 px wide (1000 px for the landscape Studio Console).
