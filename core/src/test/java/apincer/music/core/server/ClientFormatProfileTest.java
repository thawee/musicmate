package apincer.music.core.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import apincer.music.core.model.AudioTag;
import apincer.music.core.model.Track;

public class ClientFormatProfileTest {

    private static Track track(String encoding, String path) {
        AudioTag t = new AudioTag();
        t.setAudioEncoding(encoding);
        t.setPath(path);
        return t;
    }

    private static final Track FLAC = track("FLAC", "/m/a.flac");
    private static final Track ALAC = track("ALAC", "/m/a.m4a");
    private static final Track DSD = track("DSF", "/m/a.dsf");
    private static final Track MP3 = track("MPEG", "/m/a.mp3");

    @Test
    public void detection() {
        assertEquals(ClientFormatProfile.SONY_TV, ClientFormatProfile.of("UPnP/1.0", "av=5.0; cn=\"Sony Corporation\"; mn=\"BRAVIA KD-55X85J\";"));
        assertEquals(ClientFormatProfile.LG_TV, ClientFormatProfile.of("Linux/4.4.84 UPnP/1.0 LG WebOSTV DLNADOC/1.50", null));
        assertEquals(ClientFormatProfile.TOSHIBA_TV, ClientFormatProfile.of("TOSHIBA-DTV/1.0 UPnP/1.0", null));
        assertEquals(ClientFormatProfile.DEFAULT, ClientFormatProfile.of("BubbleUPnP UPnP/1.1", null));
        assertEquals(ClientFormatProfile.DEFAULT, ClientFormatProfile.of(null, null));
    }

    @Test
    public void sony_playsFlac_convertsAlac_hidesDsd() {
        ClientFormatProfile p = ClientFormatProfile.SONY_TV;
        assertFalse(p.convertsToPcm(FLAC));
        assertTrue(p.convertsToPcm(ALAC));
        assertTrue(p.hides(DSD));
        assertFalse(p.hides(MP3));
    }

    @Test
    public void lg_convertsFlacAndAlac_hidesDsd() {
        // LG plays FLAC only from 2022 and the User-Agent does not say the model year
        ClientFormatProfile p = ClientFormatProfile.LG_TV;
        assertTrue(p.convertsToPcm(FLAC));
        assertTrue(p.convertsToPcm(ALAC));
        assertTrue(p.hides(DSD));
        assertFalse(p.convertsToPcm(MP3));
    }

    @Test
    public void toshiba_onlyHidesDsd_untilWeHaveEvidence() {
        assertTrue(ClientFormatProfile.TOSHIBA_TV.hides(DSD));
        assertFalse(ClientFormatProfile.TOSHIBA_TV.convertsToPcm(FLAC));
        assertFalse(ClientFormatProfile.TOSHIBA_TV.convertsToPcm(ALAC));
    }

    @Test
    public void otherClients_seeEverythingAsIs() {
        assertFalse(ClientFormatProfile.DEFAULT.hides(DSD));
        assertFalse(ClientFormatProfile.DEFAULT.convertsToPcm(ALAC));
    }
}
