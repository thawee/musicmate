package apincer.music.server.jupnp.content;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BrowsePagingTest {

    private static final List<Integer> ALL = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    @Test
    public void requestedCount_limitsThePage() {
        assertEquals(Arrays.asList(0, 1, 2), AbstractContentBrowser.page(ALL, 0, 3));
    }

    @Test
    public void startingIndex_skipsEarlierEntries() {
        assertEquals(Arrays.asList(4, 5, 6), AbstractContentBrowser.page(ALL, 4, 3));
    }

    @Test
    public void requestedCountZero_meansEverythingFromTheStart() {
        // UPnP ContentDirectory: RequestedCount 0 returns all remaining entries
        assertEquals(ALL, AbstractContentBrowser.page(ALL, 0, 0));
        assertEquals(Arrays.asList(8, 9), AbstractContentBrowser.page(ALL, 8, 0));
    }

    @Test
    public void lastPage_isShort() {
        assertEquals(Arrays.asList(8, 9), AbstractContentBrowser.page(ALL, 8, 5));
    }

    @Test
    public void startPastTheEnd_isEmpty() {
        assertEquals(List.of(), AbstractContentBrowser.page(ALL, 10, 5));
        assertEquals(List.of(), AbstractContentBrowser.page(ALL, 1_000_000, 0));
    }

    @Test
    public void hugeRequestedCount_doesNotOverflow() {
        // RequestedCount is a ui4; clients send 4294967295 for "everything"
        assertEquals(Arrays.asList(5, 6, 7, 8, 9), AbstractContentBrowser.page(ALL, 5, 4_294_967_295L));
    }
}
