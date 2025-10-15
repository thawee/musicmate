package apincer.music.core.model;

import java.util.List;

// A simple record to hold the final, aggregated track information.
public record TrackInfo(
        String artistBio,
        String highResArtUrl,
        List<String> genres
) {
    // You can add more fields here as needed, like album review, tracklist, etc.
}