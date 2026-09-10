package apincer.music.server.jupnp;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MediaServerHubTimeParsingTest {

    @Test
    public void testFormatDurationForDidl() {
        // 238 seconds -> 238,000 milliseconds
        long durationMs = 238_000L;
        String formatted = MediaServerHubImpl.formatDurationForDidl(durationMs);
        assertEquals("0:03:58.000", formatted);

        // 1 hour, 23 minutes, 45 seconds, 678 millis
        long complexMs = (1 * 3600 + 23 * 60 + 45) * 1000L + 678L;
        assertEquals("1:23:45.678", MediaServerHubImpl.formatDurationForDidl(complexMs));

        // 0 millis
        assertEquals("0:00:00.000", MediaServerHubImpl.formatDurationForDidl(0L));
    }

    @Test
    public void testParseTimeToSeconds_standardFormats() {
        // Standard HH:MM:SS
        assertEquals(238, MediaServerHubImpl.parseTimeToSeconds("00:03:58"));
        assertEquals(3665, MediaServerHubImpl.parseTimeToSeconds("01:01:05"));

        // MM:SS format
        assertEquals(238, MediaServerHubImpl.parseTimeToSeconds("03:58"));
        assertEquals(58, MediaServerHubImpl.parseTimeToSeconds("00:58"));
    }

    @Test
    public void testParseTimeToSeconds_fractionalSeconds() {
        // HH:MM:SS.mmm format from UPnP renderers
        assertEquals(238, MediaServerHubImpl.parseTimeToSeconds("00:03:58.238"));
        assertEquals(58, MediaServerHubImpl.parseTimeToSeconds("00:00:58.999"));

        // MM:SS.mmm format
        assertEquals(238, MediaServerHubImpl.parseTimeToSeconds("03:58.500"));
    }

    @Test
    public void testParseTimeToSeconds_edgeCases() {
        assertEquals(0, MediaServerHubImpl.parseTimeToSeconds(null));
        assertEquals(0, MediaServerHubImpl.parseTimeToSeconds(""));
        assertEquals(0, MediaServerHubImpl.parseTimeToSeconds("NOT_IMPLEMENTED"));
        assertEquals(0, MediaServerHubImpl.parseTimeToSeconds("invalid"));
    }
}
