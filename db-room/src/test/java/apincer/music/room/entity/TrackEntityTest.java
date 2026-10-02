package apincer.music.room.entity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import apincer.music.core.model.Track;

public class TrackEntityTest {

    @Test
    public void copy_keepsTheLibraryId() {
        TrackEntity original = new TrackEntity();
        original.setId(3355664299L);
        original.setTitle("Title");

        Track copy = original.copy();

        assertEquals(3355664299L, copy.getId());
        assertEquals("Title", copy.getTitle());
    }
}
