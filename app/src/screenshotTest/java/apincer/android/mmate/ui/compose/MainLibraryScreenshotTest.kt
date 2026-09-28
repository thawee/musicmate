package apincer.android.mmate.ui.compose

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Compact portrait", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Compact 200% text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Compact landscape", widthDp = 800, heightDp = 360, showBackground = true)
@Preview(name = "Expanded tablet", widthDp = 1000, heightDp = 700, showBackground = true)
@Composable
fun MainShellPopulatedPreview() {
    MainLibraryPreview(state = remember { UxPreviewFixtures.populatedLibrary() })
}

@PreviewTest
@Preview(name = "Empty library", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MainShellEmptyLibraryPreview() {
    MainLibraryPreview(state = remember { UxPreviewFixtures.emptyLibrary() })
}

@PreviewTest
@Preview(name = "Search no results", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MainShellSearchNoResultsPreview() {
    MainLibraryPreview(state = remember { UxPreviewFixtures.searchNoResults() })
}

@PreviewTest
@Preview(name = "Selection", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun MainShellSelectionPreview() {
    MainLibraryPreview(state = remember { UxPreviewFixtures.selectedLibrary() })
}

@Composable
internal fun MainLibraryPreview(state: MainScaffoldState) {
    MusicMateTheme {
        MainScaffold(
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
            state = state,
            showGestureHints = false,
            trackArtwork = { track -> PreviewTrackArtwork(track) }
        )
    }
}
