package apincer.music.core.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class TagRepositoryPaginationTest {
    @Test
    public void unpagedFolderOrPlaylistReturnsOnlyRequestedPage() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 1003; i++) items.add(i);

        assertEquals(items.subList(0, 500), TagRepository.pageUnpaged(items, 0, 500));
        assertEquals(items.subList(500, 1000), TagRepository.pageUnpaged(items, 500, 500));
        assertEquals(items.subList(1000, 1003), TagRepository.pageUnpaged(items, 1000, 500));
        assertTrue(TagRepository.pageUnpaged(items, 1500, 500).isEmpty());
        assertEquals(items, TagRepository.pageUnpaged(items, 0, 0));
    }
}
