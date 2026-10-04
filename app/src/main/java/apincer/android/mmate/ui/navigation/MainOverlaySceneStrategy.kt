package apincer.android.mmate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

internal data class MainOverlayScene(
    override val key: Any,
    override val previousEntries: List<NavEntry<NavKey>>,
    override val overlaidEntries: List<NavEntry<NavKey>>,
    private val entry: NavEntry<NavKey>,
) : OverlayScene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val content: @Composable () -> Unit = { entry.Content() }
}

/** Keeps compact Music Center and Studio Console routes above their previous scenes. */
internal class MainOverlaySceneStrategy(
    private val useMusicCenterOverlay: Boolean = true,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>
    ): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        val route = entry.contentKey
        val handlesRoute = route is MainRoute.StudioConsole ||
            (useMusicCenterOverlay && route is MainRoute.MusicCenter)
        if (!handlesRoute) return null
        val previousEntries = entries.dropLast(1)
        return MainOverlayScene(
            key = route,
            previousEntries = previousEntries,
            overlaidEntries = previousEntries,
            entry = entry,
        )
    }
}
