package apincer.android.mmate.ui.compose

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Compose colours belong in MusicMateDesignTokens. Each file may keep at most the hex
 * `Color(0x...)` literals it had when the budget was set; new files get none. When you
 * move literals to tokens, lower the file's budget so it cannot creep back up.
 */
class ColorLiteralBudgetTest {

    private val budget = mapOf(
        "AboutScreen.kt" to 41,
        "ActionFilesDialog.kt" to 12,
        "AnalogVUMeter.kt" to 23,
        "AudioBadges.kt" to 24,
        "AudioHubSheet.kt" to 24,
        "AudioPresentation.kt" to 8,
        "CreateSmartPlaylistDialog.kt" to 17,
        "DynamicRangeMeters.kt" to 1,
        "FastScrollbar.kt" to 4,
        "FolderListItem.kt" to 55,
        "FormatFilesDialog.kt" to 15,
        "FullscreenStudioConsole.kt" to 53,
        "MainScaffold.kt" to 45,
        "MediaServerPage.kt" to 39,
        "MusicFoldersDialog.kt" to 12,
        "MusicListScreen.kt" to 9,
        "MusicMateTheme.kt" to 2,
        "NowPlayingPage.kt" to 50,
        "PermissionScreen.kt" to 10,
        "PlayerPickerDialog.kt" to 23,
        "PlaylistPickerDialog.kt" to 21,
        "QualityPieChart.kt" to 7,
        "QueuePage.kt" to 48,
        "ReelToReelTapeDeck.kt" to 37,
        "RelatedTracksSheet.kt" to 20,
        "SearchMatchDialog.kt" to 31,
        "SettingsScreen.kt" to 17,
        "TagsEditorPage.kt" to 10,
        "TagsFromFilenameSheet.kt" to 27,
        "TagsTabPillSwitcher.kt" to 14,
        "TagsTechnicalPage.kt" to 21,
        "TrackListItem.kt" to 19,
        "TrackListItemShimmer.kt" to 4,
        "Waveform.kt" to 2,
    )

    @Test
    fun composeFilesStayWithinTheirColorLiteralBudget() {
        val dir = File("src/main/java/apincer/android/mmate/ui/compose")
        assertTrue("compose sources not found at ${dir.absolutePath}", dir.isDirectory)
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("no Kotlin files scanned", files.size > 10)

        val over = files.filter { it.name != "MusicMateDesignTokens.kt" }.mapNotNull { f ->
            val count = Regex("""Color\(0x""").findAll(f.readText()).count()
            val allowed = budget[f.name] ?: 0
            if (count > allowed) "${f.name}: $count literals, budget $allowed" else null
        }
        if (over.isNotEmpty()) {
            fail("Use MusicMateDesignTokens instead of new Color(0x...) literals:\n" + over.joinToString("\n"))
        }
    }
}
