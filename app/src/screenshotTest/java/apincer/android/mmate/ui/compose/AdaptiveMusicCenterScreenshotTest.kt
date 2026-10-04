package apincer.android.mmate.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Closed compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Closed medium", widthDp = 700, heightDp = 800, showBackground = true)
@Preview(name = "Closed foldable", widthDp = 840, heightDp = 720, showBackground = true)
@Preview(name = "Closed expanded", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
fun AdaptiveMusicCenterClosedPreview() {
    MainLibraryPreview(state = remember { UxPreviewFixtures.populatedLibrary() })
}

@PreviewTest
@Preview(name = "Open compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Open medium", widthDp = 700, heightDp = 800, showBackground = true)
@Preview(name = "Open foldable", widthDp = 840, heightDp = 720, showBackground = true)
@Preview(name = "Open expanded", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
fun AdaptiveMusicCenterOpenPreview() {
    val useSupportingPane = UiLayoutPolicy.useMusicCenterSupportingPane(
        LocalConfiguration.current.screenWidthDp
    )
    val libraryState = remember { UxPreviewFixtures.populatedLibrary() }
    val queueState = remember { UxPreviewFixtures.populatedQueue() }

    if (useSupportingPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
        ) {
            Box(modifier = Modifier.weight(3f)) {
                MainLibraryPreview(state = libraryState)
            }
            Box(modifier = Modifier.weight(2f)) {
                MusicCenterPreview(
                    initialTab = 1,
                    queueState = queueState,
                    presentation = MusicCenterPresentation.SUPPORTING_PANE,
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            MainLibraryPreview(state = libraryState)
            MusicCenterPreview(
                initialTab = 1,
                queueState = queueState,
                presentation = MusicCenterPresentation.PREVIEW,
            )
        }
    }
}
