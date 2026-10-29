package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import apincer.android.mmate.audio.headphones.HeadphoneKind
import apincer.android.mmate.audio.headphones.HeadphonePreferences
import apincer.android.mmate.audio.headphones.HeadphoneProfileStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ManualHeadphoneFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun speakerListeningSaveBindsTheSpeakerAndShowsItInTheSharedDropdown() {
        val name = "Speaker flow ${System.nanoTime()}"
        var savedId: String? = null
        try {
            compose.setContent {
                MusicMateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HeadphoneAudioSettings(apincer.android.mmate.audio.calibration.CalibrationOutput.SPEAKER)
                    }
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Listening").fetchSemanticsNodes()
                    .any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            compose.onNodeWithText("Listening").performScrollTo().performClick()
            compose.onNodeWithText("Speaker name").performTextInput(name)
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithContentDescription("Bass impact adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
            compose.onNodeWithText("Save profile").performClick()
            compose.waitUntil(5_000) { HeadphonePreferences.state.value.profiles.any { it.name == name } }
            val profile = HeadphonePreferences.state.value.profiles.single { it.name == name }
            savedId = profile.id
            assertEquals(apincer.android.mmate.audio.calibration.CalibrationOutput.SPEAKER, profile.output)
            assertEquals(-2.0, profile.manual!!.left.bassDb, 0.01)
            val endpoint = apincer.android.mmate.audio.headphones.HeadphoneDsp.state.value.endpoint!!
            val binding = HeadphonePreferences.state.value.bindings.getValue(endpoint.key)
            assertEquals(profile.id, binding.profileId)
            org.junit.Assert.assertTrue(binding.enabled)
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("Speaker · Listening · Selected").assertExists()
        } finally {
            savedId?.let { HeadphonePreferences.delete(compose.activity, it) }
        }
    }

    @Test fun guidedSavePersistsAndReopensTheSamePair() {
        val name = "Workshop flow ${System.nanoTime()}"
        var savedId: String? = null
        try {
            compose.setContent {
                MusicMateTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HeadphoneAudioSettings(apincer.android.mmate.audio.calibration.CalibrationOutput.HEADPHONE)
                    }
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Listening").fetchSemanticsNodes()
                    .any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            compose.onNodeWithText("Listening").performScrollTo().performClick()
            compose.onNodeWithText("Pair name").performTextInput(name)
            compose.onNodeWithText("In-ear").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithContentDescription("Left-right balance").performSemanticsAction(SemanticsActions.SetProgress) { it(-2f) }
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithContentDescription("Bass impact adjustment").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            compose.onNodeWithText("Save profile").performClick()
            compose.waitUntil(5_000) { HeadphonePreferences.state.value.profiles.any { it.name == name } }
            val saved = HeadphonePreferences.state.value.profiles.single { it.name == name }
            savedId = saved.id
            val persisted = HeadphoneProfileStore(File(compose.activity.filesDir, "headphone_profiles.json"))
                .load().profiles.single { it.id == saved.id }
            assertEquals(saved, persisted)
            assertEquals(HeadphoneKind.IN_EAR, saved.manual!!.kind)
            assertEquals(2.0, saved.manual!!.left.bassDb, 0.01)
            assertEquals(saved.manual!!.left, saved.manual!!.right)
            assertEquals(-2.0, saved.manual!!.balanceDb, 0.01)
            compose.onNodeWithText("Saved listening profiles").performScrollTo()
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Balance: -2.0 dB").assertExists()
            compose.onNodeWithText("Cancel").performClick()
        } finally {
            savedId?.let { HeadphonePreferences.delete(compose.activity, it) }
        }
    }
}
