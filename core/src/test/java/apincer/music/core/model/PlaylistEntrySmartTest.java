package apincer.music.core.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Proxy;

public class PlaylistEntrySmartTest {

    private Track createTrack(String encoding, int bitDepth, long sampleRate, double drScore, String fileType) {
        return (Track) Proxy.newProxyInstance(
                Track.class.getClassLoader(),
                new Class<?>[]{Track.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAudioEncoding".equals(name)) return encoding;
                    if ("getAudioBitsDepth".equals(name)) return bitDepth;
                    if ("getAudioSampleRate".equals(name)) return sampleRate;
                    if ("getDrScore".equals(name)) return drScore;
                    if ("getDynamicRange".equals(name)) return drScore;
                    if ("getFileType".equals(name)) return fileType;
                    if ("getPath".equals(name)) return "/music/test." + fileType;
                    if ("getQualityInd".equals(name)) return "";
                    if ("getTitle".equals(name)) return "Test Track";
                    if ("getArtist".equals(name)) return "Test Artist";
                    if (method.getReturnType().equals(boolean.class)) return false;
                    if (method.getReturnType().equals(int.class)) return 0;
                    if (method.getReturnType().equals(long.class)) return 0L;
                    if (method.getReturnType().equals(double.class)) return 0.0;
                    return null;
                }
        );
    }

    @Test
    public void testDrScoreSmartPlaylist() {
        PlaylistEntry drEntry = new PlaylistEntry();
        drEntry.setType(PlaylistEntry.TYPE_SMART);
        drEntry.setMinDrScore(12.0);

        Track dr14Track = createTrack("flac", 16, 44100, 14.0, "flac");
        Track dr12Track = createTrack("flac", 16, 44100, 12.0, "flac");
        Track dr8Track = createTrack("flac", 16, 44100, 8.0, "flac");

        assertTrue("DR14 should match DR12+ playlist", drEntry.isInPlaylist(dr14Track));
        assertTrue("DR12 should match DR12+ playlist", drEntry.isInPlaylist(dr12Track));
        assertFalse("DR8 should not match DR12+ playlist", drEntry.isInPlaylist(dr8Track));
    }

    @Test
    public void testHiResStudioMastersSmartPlaylist() {
        PlaylistEntry hiresEntry = new PlaylistEntry();
        hiresEntry.setType(PlaylistEntry.TYPE_SMART);
        hiresEntry.setHiresOnly(true);

        Track hiresFlac = createTrack("flac", 24, 96000, 11.0, "flac");
        Track studio48 = createTrack("flac", 24, 48000, 10.0, "flac");
        Track cdLossless = createTrack("flac", 16, 44100, 12.0, "flac");
        Track mp3Track = createTrack("mp3", 16, 44100, 9.0, "mp3");
        Track dsdTrack = createTrack("dsd", 1, 2822400, 13.0, "dsf");

        assertTrue("24/96 FLAC should match Hi-Res playlist", hiresEntry.isInPlaylist(hiresFlac));
        assertTrue("24/48 FLAC should match Hi-Res playlist", hiresEntry.isInPlaylist(studio48));
        assertTrue("DSD track should match Hi-Res playlist", hiresEntry.isInPlaylist(dsdTrack));
        assertFalse("16/44.1 FLAC should not match Hi-Res playlist", hiresEntry.isInPlaylist(cdLossless));
        assertFalse("MP3 should not match Hi-Res playlist", hiresEntry.isInPlaylist(mp3Track));
    }

    @Test
    public void testPureDsdArchiveSmartPlaylist() {
        PlaylistEntry dsdEntry = new PlaylistEntry();
        dsdEntry.setType(PlaylistEntry.TYPE_SMART);
        dsdEntry.setDsdOnly(true);

        Track dsdTrack = createTrack("dsd", 1, 2822400, 13.0, "dsf");
        Track dffTrack = createTrack("dsd", 1, 5644800, 14.0, "dff");
        Track pcm24_192 = createTrack("flac", 24, 192000, 15.0, "flac");

        assertTrue("DSF track should match Pure DSD playlist", dsdEntry.isInPlaylist(dsdTrack));
        assertTrue("DFF track should match Pure DSD playlist", dsdEntry.isInPlaylist(dffTrack));
        assertFalse("PCM 24/192 should not match Pure DSD playlist", dsdEntry.isInPlaylist(pcm24_192));
    }

    @Test
    public void testLosslessMasterVaultSmartPlaylist() {
        PlaylistEntry vaultEntry = new PlaylistEntry();
        vaultEntry.setType(PlaylistEntry.TYPE_SMART);
        vaultEntry.setLosslessOnly(true);

        Track flacTrack = createTrack("flac", 16, 44100, 10.0, "flac");
        Track alacTrack = createTrack("alac", 24, 96000, 12.0, "m4a");
        Track wavTrack = createTrack("wav", 16, 44100, 11.0, "wav");
        Track dsdTrack = createTrack("dsd", 1, 2822400, 14.0, "dsf");
        Track mp3Track = createTrack("mp3", 16, 44100, 8.0, "mp3");
        Track aacTrack = createTrack("aac", 16, 44100, 8.0, "m4a");

        assertTrue("FLAC should match Lossless Vault", vaultEntry.isInPlaylist(flacTrack));
        assertTrue("ALAC should match Lossless Vault", vaultEntry.isInPlaylist(alacTrack));
        assertTrue("WAV should match Lossless Vault", vaultEntry.isInPlaylist(wavTrack));
        assertTrue("DSD should match Lossless Vault", vaultEntry.isInPlaylist(dsdTrack));
        assertFalse("MP3 should not match Lossless Vault", vaultEntry.isInPlaylist(mp3Track));
        assertFalse("AAC should not match Lossless Vault", vaultEntry.isInPlaylist(aacTrack));
    }
}
