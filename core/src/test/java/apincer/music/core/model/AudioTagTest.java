package apincer.music.core.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class AudioTagTest {

    @Test
    public void copy_createsDistinctCloneWithAllFieldsPreserved() {
        AudioTag original = new AudioTag();
        original.setId(101L);
        original.setTitle("Test Title");
        original.setArtist("Test Artist");
        original.setAlbum("Test Album");
        original.setGenre("Jazz");
        original.setMood("Relaxing");
        original.setStyle("Bebop");
        original.setOrigin("US");
        original.setBpm(120.0);
        original.setFileLastModified(1700000000000L);
        original.setAudioSampleRate(96000);
        original.setAudioBitsDepth(24);
        original.setDrScore(14.0);

        Track clone = original.copy();

        assertNotNull(clone);
        assertNotSame(original, clone);
        assertEquals(original.getId(), clone.getId());
        assertEquals("Test Title", clone.getTitle());
        assertEquals("Test Artist", clone.getArtist());
        assertEquals("Test Album", clone.getAlbum());
        assertEquals("Jazz", clone.getGenre());
        assertEquals("Relaxing", clone.getMood());
        assertEquals("Bebop", clone.getStyle());
        assertEquals("US", clone.getOrigin());
        assertEquals(120.0, clone.getBpm(), 0.001);
        assertEquals(1700000000000L, clone.getFileLastModified());
        assertEquals(96000, clone.getAudioSampleRate());
        assertEquals(24, clone.getAudioBitsDepth());
        assertEquals(14.0, clone.getDrScore(), 0.001);
    }
}
