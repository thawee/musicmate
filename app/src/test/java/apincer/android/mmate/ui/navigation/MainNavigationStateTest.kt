package apincer.android.mmate.ui.navigation

import apincer.android.mmate.R
import apincer.android.mmate.ui.compose.MainScaffoldState
import apincer.android.mmate.ui.compose.UiLayoutPolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationStateTest {

    @Test
    fun routesRoundTripWithoutAndroidResourceIdentity() {
        val routes: List<MainRoute> = listOf(
            MainRoute.Library(LibraryDestination.ALL_SONGS),
            MainRoute.Library(LibraryDestination.RECENTLY_ADDED),
            MainRoute.Library(LibraryDestination.SIMILAR_TRACKS),
            MainRoute.Library(LibraryDestination.AUDIO_QUALITY),
            MainRoute.Library(LibraryDestination.PLAYLISTS),
            MainRoute.Library(LibraryDestination.GENRES),
            MainRoute.Library(LibraryDestination.ARTISTS),
            MainRoute.MusicCenter(MusicCenterTab.NOW_PLAYING),
            MainRoute.MusicCenter(MusicCenterTab.QUEUE),
            MainRoute.MusicCenter(MusicCenterTab.MEDIA_SERVER),
            MainRoute.StudioConsole,
            MainRoute.Settings,
            MainRoute.About,
            MainRoute.TagEditor(listOf(41L, 73L))
        )

        val encoded = Json.encodeToString(routes)

        assertEquals(routes, Json.decodeFromString<List<MainRoute>>(encoded))
        assertFalse(encoded.contains("menu_"))
    }

    @Test
    fun snapshotRestoresSelectedLibraryAndOverlayStack() {
        val state = MainNavigationState()
        state.selectLibrary(LibraryDestination.ARTISTS)
        state.openMusicCenter(MusicCenterTab.QUEUE)
        state.openStudioConsole()

        val encoded = Json.encodeToString(state.snapshot())
        val restored = MainNavigationState.restore(
            Json.decodeFromString<MainNavigationSnapshot>(encoded)
        )

        assertEquals(LibraryDestination.ARTISTS, restored.selectedLibraryDestination)
        assertEquals(MainRoute.StudioConsole, restored.overlayRoute)
        assertEquals(
            listOf(
                MainRoute.Library(LibraryDestination.ARTISTS),
                MainRoute.MusicCenter(MusicCenterTab.QUEUE),
                MainRoute.StudioConsole
            ),
            restored.backStack
        )
    }

    @Test
    fun poppingOverlayRevealsPreviousOverlayThenRoot() {
        val state = MainNavigationState()
        state.openMusicCenter(MusicCenterTab.MEDIA_SERVER)
        state.openStudioConsole()

        assertTrue(state.popOverlay())
        assertEquals(
            MainRoute.MusicCenter(MusicCenterTab.MEDIA_SERVER),
            state.overlayRoute
        )
        assertTrue(state.popOverlay())
        assertEquals(null, state.overlayRoute)
        assertFalse(state.popOverlay())
        assertEquals(
            listOf(MainRoute.Library(LibraryDestination.ALL_SONGS)),
            state.backStack
        )
    }

    @Test
    fun reopeningMusicCenterUpdatesItsTabWithoutDuplicatingTheRoute() {
        val state = MainNavigationState()

        state.openMusicCenter(MusicCenterTab.NOW_PLAYING)
        state.openMusicCenter(MusicCenterTab.QUEUE)

        assertEquals(
            listOf(
                MainRoute.Library(LibraryDestination.ALL_SONGS),
                MainRoute.MusicCenter(MusicCenterTab.QUEUE)
            ),
            state.backStack
        )
        assertTrue(state.isMusicCenterOpen)
    }

    @Test
    fun musicCenterTabUpdateIsSavedUnderStudioConsole() {
        val state = MainNavigationState()
        state.openMusicCenter(MusicCenterTab.NOW_PLAYING)
        state.openStudioConsole()

        state.updateMusicCenterTab(MusicCenterTab.MEDIA_SERVER)
        val restored = MainNavigationState.restore(state.snapshot())

        assertEquals(MainRoute.StudioConsole, restored.overlayRoute)
        assertEquals(
            MainRoute.MusicCenter(MusicCenterTab.MEDIA_SERVER),
            restored.backStack[1]
        )
        assertTrue(restored.dismissStudioConsole())
        assertEquals(
            MainRoute.MusicCenter(MusicCenterTab.MEDIA_SERVER),
            restored.overlayRoute
        )
        assertTrue(restored.dismissMusicCenter())
        assertFalse(restored.isMusicCenterOpen)
    }

    @Test
    fun legacyJavaBridgeDelegatesToNavigationStack() {
        val state = MainNavigationState()
        MainNavigationInterop.attach(state)
        try {
            MainScaffoldState.openAudioHub(1)
            assertEquals(MainRoute.MusicCenter(MusicCenterTab.QUEUE), state.overlayRoute)
            assertTrue(MainScaffoldState.isAudioHubOpen())

            MainScaffoldState.closeAudioHub()
            assertFalse(MainScaffoldState.isAudioHubOpen())
        } finally {
            MainNavigationInterop.detach(state)
        }
    }

    @Test
    fun libraryMenuCompatibilityMappingCoversOnlyTypedLibraryDestinations() {
        val expected = mapOf(
            R.id.menu_library_all_songs to LibraryDestination.ALL_SONGS,
            R.id.menu_library_recently_added to LibraryDestination.RECENTLY_ADDED,
            R.id.menu_library_similar_songs to LibraryDestination.SIMILAR_TRACKS,
            R.id.menu_sound_grade to LibraryDestination.AUDIO_QUALITY,
            R.id.menu_collection to LibraryDestination.PLAYLISTS,
            R.id.menu_tag_genre to LibraryDestination.GENRES,
            R.id.menu_tag_artist to LibraryDestination.ARTISTS,
        )

        expected.forEach { (menuItemId, destination) ->
            assertEquals(destination, LibraryDestinationMenuMapping.fromMenuItemId(menuItemId))
            assertEquals(menuItemId, LibraryDestinationMenuMapping.toMenuItemId(destination))
        }
        assertEquals(null, LibraryDestinationMenuMapping.fromMenuItemId(R.id.menu_settings))
    }

    @Test
    fun selectingLibraryDestinationClearsOverlaysAndDefinesRootState() {
        val state = MainNavigationState()
        state.openMusicCenter(MusicCenterTab.QUEUE)

        state.selectLibrary(LibraryDestination.ARTISTS)

        assertFalse(state.isAtLibraryRoot)
        assertEquals(
            listOf(MainRoute.Library(LibraryDestination.ARTISTS)),
            state.backStack
        )
        state.selectLibrary(LibraryDestination.ALL_SONGS)
        assertTrue(state.isAtLibraryRoot)
    }

    @Test
    fun javaBridgeCanInspectAndChangeTheTypedLibraryRoute() {
        val state = MainNavigationState()
        MainNavigationInterop.attach(state)
        try {
            MainNavigationInterop.selectLibrary(LibraryDestination.GENRES)

            assertEquals(LibraryDestination.GENRES, state.selectedLibraryDestination)
            assertFalse(MainNavigationInterop.isAtLibraryRoot())

            MainNavigationInterop.selectLibrary(LibraryDestination.ALL_SONGS)
            assertTrue(MainNavigationInterop.isAtLibraryRoot())
        } finally {
            MainNavigationInterop.detach(state)
        }
    }

    @Test
    fun initialLibraryDestinationAndOverlayBackAreAvailableToTheJavaBridge() {
        val state = MainNavigationState(LibraryDestination.PLAYLISTS)
        MainNavigationInterop.attach(state)
        try {
            assertEquals(LibraryDestination.PLAYLISTS, state.selectedLibraryDestination)
            assertFalse(MainNavigationInterop.isOverlayOpen())

            state.openMusicCenter(MusicCenterTab.NOW_PLAYING)
            assertTrue(MainNavigationInterop.isOverlayOpen())
            assertTrue(MainNavigationInterop.popOverlay())
            assertFalse(MainNavigationInterop.isOverlayOpen())
        } finally {
            MainNavigationInterop.detach(state)
        }
    }

    @Test
    fun musicCenterRouteAndTabSurviveWindowClassChangesAndRestoration() {
        val state = MainNavigationState()
        state.openMusicCenter(MusicCenterTab.QUEUE)

        assertFalse(UiLayoutPolicy.useMusicCenterSupportingPane(windowWidthDp = 839))
        assertEquals(MainRoute.MusicCenter(MusicCenterTab.QUEUE), state.overlayRoute)

        val restored = MainNavigationState.restore(state.snapshot())

        assertTrue(UiLayoutPolicy.useMusicCenterSupportingPane(windowWidthDp = 840))
        assertEquals(MainRoute.MusicCenter(MusicCenterTab.QUEUE), restored.overlayRoute)
        assertEquals(state.backStack, restored.backStack)
    }
}
