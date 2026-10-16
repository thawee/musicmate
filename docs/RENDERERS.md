# Renderer compatibility

Which network players (DLNA/UPnP renderers) and control apps have been tried with MusicMate's media server, and how they did. Each row is a report from someone who tried it; nothing is listed from specifications alone.

To add a result, [open a renderer report](https://github.com/thawee/musicmate/issues/new?template=renderer-report.yml). Results from the Google Play closed test are added here as they come in.

## How to read the table

- **Played from:** "MusicMate" means the player was picked in MusicMate's player picker; otherwise the control app named (for example a renderer's own app) browsed MusicMate's library.
- **Formats:** what played correctly. A format missing from the list was not tried, not necessarily a failure.
- **Gapless, Seek, Cover art:** Yes, No, or blank when not tried.

## Renderers

| Renderer | Firmware | MusicMate | Played from | Formats | Gapless | Seek | Cover art | Notes |
|---|---|---|---|---|---|---|---|---|
| HiBy R3 (DLNA renderer mode) | | 3.26.1 and later | | | | | | Streams; skipping songs could use up the server's stream slots before 3.26.1 (see the [changelog](../CHANGELOG.md)). Other details not yet recorded. |

## Known limits

- Sony and LG TVs: ALAC, and FLAC on LG models before 2022, are converted to WAV while they stream, at the original sample rate and bit depth. DSD is not offered to Sony, LG and Toshiba TVs. Other players get the original files.
- On Android 17, the server needs the Local network access permission, which MusicMate asks for when the server starts.
