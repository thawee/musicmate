package apincer.android.mmate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.serialization.Serializable

private val DefaultLibraryRoute = MainRoute.Library(LibraryDestination.ALL_SONGS)

@Serializable
data class MainNavigationSnapshot(val backStack: List<MainRoute>)

/**
 * Owns the typed main stack while the existing Java/Compose callback bridge remains active.
 * A [rememberNavBackStack] supplies the production list, so every route is restored across
 * configuration change and process recreation by Navigation 3.
 */
class MainNavigationState internal constructor(
    private val mutableBackStack: MutableList<NavKey>
) {
    constructor() : this(mutableListOf(DefaultLibraryRoute))
    constructor(initialDestination: LibraryDestination) : this(
        mutableListOf(MainRoute.Library(initialDestination))
    )

    val backStack: List<MainRoute>
        get() = mutableBackStack.map { it as MainRoute }

    internal val navBackStack: List<NavKey>
        get() = mutableBackStack

    val selectedLibraryDestination: LibraryDestination
        get() = backStack.filterIsInstance<MainRoute.Library>()
            .firstOrNull()
            ?.destination
            ?: LibraryDestination.ALL_SONGS

    val overlayRoute: MainRoute?
        get() = backStack.lastOrNull()?.takeIf(MainRoute::isOverlay)

    val isMusicCenterOpen: Boolean
        get() = mutableBackStack.any { it is MainRoute.MusicCenter }

    val isAtLibraryRoot: Boolean
        get() = selectedLibraryDestination == LibraryDestination.ALL_SONGS && overlayRoute == null

    fun selectLibrary(destination: LibraryDestination) {
        mutableBackStack.clear()
        mutableBackStack.add(MainRoute.Library(destination))
    }

    fun openMusicCenter(tab: MusicCenterTab) {
        val route = MainRoute.MusicCenter(tab)
        val existingIndex = mutableBackStack.indexOfLast { it is MainRoute.MusicCenter }
        if (existingIndex >= 0) {
            mutableBackStack[existingIndex] = route
        } else {
            mutableBackStack.add(route)
        }
    }

    fun updateMusicCenterTab(tab: MusicCenterTab) {
        val index = mutableBackStack.indexOfLast { it is MainRoute.MusicCenter }
        if (index >= 0) {
            mutableBackStack[index] = MainRoute.MusicCenter(tab)
        }
    }

    fun openStudioConsole() {
        if (overlayRoute != MainRoute.StudioConsole) {
            mutableBackStack.add(MainRoute.StudioConsole)
        }
    }

    fun popOverlay(): Boolean {
        if (overlayRoute == null) return false
        mutableBackStack.removeAt(mutableBackStack.lastIndex)
        return true
    }

    fun dismissMusicCenter(): Boolean = dismissTopRoute<MainRoute.MusicCenter>()

    fun dismissStudioConsole(): Boolean = dismissTopRoute<MainRoute.StudioConsole>()

    fun snapshot(): MainNavigationSnapshot = MainNavigationSnapshot(backStack)

    companion object {
        fun restore(snapshot: MainNavigationSnapshot): MainNavigationState {
            val routes = snapshot.backStack
                .takeIf { candidate -> candidate.firstOrNull() is MainRoute.Library }
                ?: listOf(DefaultLibraryRoute)
            val restored: MutableList<NavKey> = routes.toMutableList()
            return MainNavigationState(restored)
        }
    }
}

private inline fun <reified T : MainRoute> MainNavigationState.dismissTopRoute(): Boolean {
    if (overlayRoute !is T) return false
    return popOverlay()
}

@Composable
fun rememberMainNavigationState(
    initialDestination: LibraryDestination = LibraryDestination.ALL_SONGS
): MainNavigationState {
    val backStack = rememberNavBackStack(MainRoute.Library(initialDestination))
    return remember(backStack) { MainNavigationState(backStack) }
}

object MainNavigationInterop {
    private var navigationState: MainNavigationState? = null

    internal fun attach(state: MainNavigationState) {
        navigationState = state
    }

    internal fun detach(state: MainNavigationState) {
        if (navigationState === state) navigationState = null
    }

    fun openMusicCenter(initialPage: Int) {
        navigationState?.openMusicCenter(MusicCenterTab.fromPage(initialPage))
    }

    fun closeMusicCenter() {
        navigationState?.dismissMusicCenter()
    }

    fun isMusicCenterOpen(): Boolean = navigationState?.isMusicCenterOpen == true

    @JvmStatic
    fun selectLibrary(destination: LibraryDestination) {
        navigationState?.selectLibrary(destination)
    }

    @JvmStatic
    fun isAtLibraryRoot(): Boolean = navigationState?.isAtLibraryRoot == true

    @JvmStatic
    fun isOverlayOpen(): Boolean = navigationState?.overlayRoute != null

    @JvmStatic
    fun popOverlay(): Boolean = navigationState?.popOverlay() == true
}

private fun MainRoute.isOverlay(): Boolean =
    this is MainRoute.MusicCenter || this is MainRoute.StudioConsole
