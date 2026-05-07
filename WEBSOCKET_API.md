# MusicMate WebSocket API

The MusicMate WebSocket API enables real-time, bidirectional communication between the Web UI and the Android server. It is hosted at `ws://[ip]:9000/ws`.

## 1. Request/Response Pattern
The client sends JSON commands to the server. Some commands trigger a direct JSON response, while others trigger state updates broadcast to all connected clients.

### Library Commands

#### `browse`
Retrieves a list of items (folders or songs) for a given path.
*   **Request**: `{ "command": "browse", "path": "Library/Artists" }`
*   **Response (`browseResult`)**:
    ```json
    {
      "type": "browseResult",
      "path": "Library/Artists",
      "items": [
        { "type": "folder", "name": "Artist Name", "path": "Library/Artists/Artist Name" },
        { "type": "song", "trackId": 123, "title": "...", "artist": "...", "artUrl": "..." }
      ]
    }
    ```

#### `getTrackDetails`
Fetches basic metadata for a specific track.
*   **Request**: `{ "command": "getTrackDetails", "trackId": "123" }`
*   **Response (`trackDetailsResult`)**:
    ```json
    {
      "type": "trackDetailsResult",
      "track": { "trackId": 123, "title": "...", "artist": "...", ... }
    }
    ```

#### `getTrackMetadata`
Fetches heavy metadata, including waveform peaks and artist/album biographies.
*   **Request**: `{ "command": "getTrackMetadata", "trackId": 123 }`
*   **Response (`trackMetadata`)**:
    ```json
    {
      "type": "trackMetadata",
      "trackId": 123,
      "waveform": [0.1, 0.5, -0.2, ...],
      "info": {
        "artistBio": "...",
        "albumBio": "...",
        "genres": ["Jazz", "Fusion"],
        "credits": { "composer": "..." }
      }
    }
    ```

### Playback Control Commands

| Command | Payload | Description |
| :--- | :--- | :--- |
| `play` | `{ "trackId": "123" }` | Play a specific track immediately. |
| `playFromContext`| `{ "trackId": "123", "path": "..." }` | Play track and set the queue based on the browse path. |
| `togglePlayPause`| None | Toggle between Play and Pause states. |
| `next` | None | Skip to the next track in the queue. |
| `previous` | None | Skip to the previous track. |
| `setShuffle` | `{ "enabled": true }` | Enable or disable shuffle mode. |
| `setRepeatMode` | `{ "mode": "none\|all\|one" }` | Set repeat behavior. |

### Queue & Renderer Commands

#### `getQueue`
*   **Request**: `{ "command": "getQueue" }`
*   **Response**: Returns `updateQueue` broadcast.

#### `addToQueue` / `emptyQueue`
*   **Request**: `{ "command": "addToQueue", "trackId": "123" }` or `{ "command": "emptyQueue" }`.
*   **Response**: Triggers `updateQueue` broadcast.

#### `setRenderer`
Switches the playback target (e.g., to a UPnP speaker).
*   **Request**: `{ "command": "setRenderer", "udn": "unique-device-id" }`
*   **Response**: Triggers `playerStatus` broadcast.

---

## 2. Broadcast Messages (Server to Client)
These messages are pushed to all clients whenever the system state changes.

### `nowPlaying`
Sent when the active track changes.
```json
{
  "type": "nowPlaying",
  "track": {
    "trackId": 123,
    "title": "Song Title",
    "artist": "Artist Name",
    "quality": "Hi-Res",
    "artUrl": "/coverart/...",
    "drs": 12
  }
}
```

### `playbackState`
Sent frequently (every second) or on state change.
```json
{
  "type": "playbackState",
  "trackId": 123,
  "state": "playing",
  "elapsed": 45,
  "duration": 180
}
```

### `updateQueue`
Sent when the playing queue is modified.
```json
{
  "type": "updateQueue",
  "path": "Playing Queue",
  "queue": [ { "trackId": 123, "title": "..." }, ... ]
}
```

### `availableRenderers` / `playerStatus`
Reports discovered UPnP devices and the currently selected target.
```json
{
  "type": "availableRenderers",
  "renderers": [ { "name": "Living Room Speaker", "targetId": "..." } ]
}
```

### `statsUpdate`
Provides library-wide statistics.
```json
{
  "type": "statsUpdate",
  "stats": {
    "songs": 1500,
    "totalSize": "45.2 GB",
    "totalDuration": "4d 12h 30m"
  }
}
```
