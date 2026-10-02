package apincer.music.server.jupnp.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import apincer.music.core.model.AudioTag;
import apincer.music.server.jupnp.transport.DLNAHeaderHelper;

public class DidlValuesTest {

    private static AudioTag file(String path, String encoding) {
        AudioTag t = new AudioTag();
        t.setPath(path);
        t.setAudioEncoding(encoding);
        return t;
    }

    private static final String TAIL = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";
    // OP=11: seek by time (TimeSeekRange.dlna.org) and by byte range; FLAC and MP3 only
    private static final String TAIL_TIME_SEEK = "DLNA.ORG_OP=11;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000";

    @Test
    public void dlnaProfile_onlyForFormatsDlnaDefines() {
        assertEquals("DLNA.ORG_PN=MP3;" + TAIL_TIME_SEEK, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.mp3", "MPEG")));
        assertEquals("DLNA.ORG_PN=AAC_ISO;" + TAIL, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.m4a", "AAC")));
        assertEquals("DLNA.ORG_PN=AAC_ADTS;" + TAIL, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.aac", "AAC")));
        // DLNA has no FLAC, ALAC, DSD or WAV profile: an invented name makes strict renderers refuse the file
        assertEquals(TAIL_TIME_SEEK, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.flac", "FLAC")));
        assertEquals(TAIL, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.m4a", "ALAC")));
        assertEquals(TAIL, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.dsf", "DSF")));
        assertEquals(TAIL, DLNAHeaderHelper.getDLNAContentFeatures(file("/m/a.wav", "WAVE")));
    }

    @Test
    public void samsungMediaInfo_isTheDurationInMilliseconds() {
        AudioTag t = file("/m/a.flac", "FLAC");
        t.setAudioDuration(263.25);
        assertEquals("SEC_Duration=263250", DLNAHeaderHelper.getSamsungMediaInfo(t));
        t.setAudioDuration(0);
        assertNull(DLNAHeaderHelper.getSamsungMediaInfo(t)); // unknown length: no header
    }

    @Test
    public void didlDate_isAnIsoDate() {
        assertEquals("2023-06-09", AbstractContentBrowser.didlDate("2023-06-09"));
        assertEquals("1959-01-01", AbstractContentBrowser.didlDate("1959"));
        assertEquals("1959-01-01", AbstractContentBrowser.didlDate(" 1959 (remaster) "));
        assertNull(AbstractContentBrowser.didlDate("unknown"));
        assertNull(AbstractContentBrowser.didlDate(""));
        assertNull(AbstractContentBrowser.didlDate(null));
    }

    @Test
    public void didlBitrate_isBytesPerSecond() {
        assertEquals(373_250L, AbstractContentBrowser.didlBitrate(2_986_000L)); // bits per second
        assertEquals(40_000L, AbstractContentBrowser.didlBitrate(320L));        // some tags hold kbps
        assertEquals(0L, AbstractContentBrowser.didlBitrate(0L));
    }
}
