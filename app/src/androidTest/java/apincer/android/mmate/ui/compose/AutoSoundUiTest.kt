package apincer.android.mmate.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import apincer.android.mmate.audio.dsp.AutoSoundConfig
import apincer.android.mmate.audio.dsp.AutoSoundField
import apincer.android.mmate.audio.dsp.AutoSoundPreferences
import apincer.android.mmate.audio.dsp.AutoSoundRule
import apincer.android.mmate.audio.dsp.SoundMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoSoundUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var original: AutoSoundConfig

    @Before fun resetPreferences() {
        AutoSoundPreferences.init(context)
        original = AutoSoundPreferences.state.value.config
        AutoSoundPreferences.resetSession()
        AutoSoundPreferences.saveSettings(context, AutoSoundConfig(
            enabled = false, fallback = SoundMode.RELAX,
            rules = listOf(AutoSoundRule(AutoSoundField.GENRE, "Rock", SoundMode.LIVELY))))
    }

    @After fun restorePreferences() {
        AutoSoundPreferences.resetSession()
        AutoSoundPreferences.saveSettings(context, original)
    }

    @Test fun autoShowsMatchedReasonAndFallbackWithoutSavingAManualSelection() {
        val selections = mutableListOf<SoundMode>()
        AutoSoundPreferences.updateTrack("Rock", "")
        showModes { selections.add(it) }
        compose.onNodeWithText("Manual").assertIsSelected()
        compose.onNodeWithText("Auto").performClick()
        compose.onNodeWithText("Auto").assertIsSelected()
        compose.onNodeWithText("Lively", substring = false).assertIsDisplayed()
        assertVisibleReason()
        compose.runOnIdle {
            assertEquals(SoundMode.LIVELY, AutoSoundPreferences.state.value.resolvedMode)
            AutoSoundPreferences.updateTrack("Unmatched genre", "")
        }
        compose.onNodeWithText("Relax", substring = false).assertIsDisplayed()
        assertVisibleReason()
        compose.runOnIdle {
            assertEquals(SoundMode.RELAX, AutoSoundPreferences.state.value.resolvedMode)
            assertTrue("Automatic resolution must not save a manual mode", selections.isEmpty())
        }
    }

    @Test fun keepForSessionFreezesModeUntilResume() {
        AutoSoundPreferences.updateTrack("Rock", "")
        showModes()
        compose.onNodeWithText("Auto").performClick()
        compose.onNodeWithText("Keep for this session").performScrollTo().performClick()
        compose.runOnIdle { AutoSoundPreferences.updateTrack("Unmatched genre", "") }
        compose.onNodeWithText("Lively", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Resume Auto").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(AutoSoundPreferences.state.value.held)
            assertEquals(SoundMode.LIVELY, AutoSoundPreferences.state.value.resolvedMode)
        }
        compose.onNodeWithText("Resume Auto").performClick()
        compose.onNodeWithText("Relax", substring = false).assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(AutoSoundPreferences.state.value.held)
            assertEquals(SoundMode.RELAX, AutoSoundPreferences.state.value.resolvedMode)
        }
    }

    @Test fun manualRestoresTheUsersSelectionAfterAuto() {
        val selections = mutableListOf<SoundMode>()
        AutoSoundPreferences.updateTrack("Rock", "")
        showModes { selections.add(it) }
        compose.onNodeWithText("Vocal").performScrollTo().performClick()
        compose.onNodeWithText("Auto").performScrollTo().performClick()
        compose.onNodeWithText("Lively", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Manual").performClick()
        compose.onNodeWithText("Vocal").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(SoundMode.VOCAL), selections) }
    }

    @Test fun settingsCanChangeFallbackAndAddEditRemoveAMatch() {
        AutoSoundPreferences.saveSettings(context, AutoSoundConfig(
            enabled = true, fallback = SoundMode.RELAX, rules = emptyList()))
        AutoSoundPreferences.updateTrack("", "Ambient")
        showModes()
        compose.onNodeWithText("Auto settings").performScrollTo().performClick()
        compose.onNodeWithText("Relax · Change").performScrollTo().performClick()
        compose.onNodeWithText("Night", substring = false).performClick()
        compose.runOnIdle { assertEquals(SoundMode.NIGHT, AutoSoundPreferences.state.value.config.fallback) }
        compose.onNodeWithText("Add match").performScrollTo().performClick()
        compose.onNodeWithText("Save match").assertIsNotEnabled()
        compose.onNodeWithText("Style", substring = false).performClick()
        compose.onNodeWithText("Tag value").performTextInput("Ambient")
        compose.onNodeWithText("Save match").performClick()
        compose.onNodeWithText("Ambient → Neutral").performScrollTo().performClick()
        compose.onNodeWithText("Neutral · Change").performScrollTo().performClick()
        compose.onNodeWithText("Vocal", substring = false).performClick()
        compose.onNodeWithText("Save match").performClick()
        compose.runOnIdle {
            assertEquals(listOf(AutoSoundRule(AutoSoundField.STYLE, "Ambient", SoundMode.VOCAL)),
                AutoSoundPreferences.state.value.config.rules)
            assertEquals(SoundMode.VOCAL, AutoSoundPreferences.state.value.resolvedMode)
        }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Vocal", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Auto settings").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Night", substring = false).assertIsDisplayed()
        compose.runOnIdle { assertTrue(AutoSoundPreferences.state.value.config.rules.isEmpty()) }
    }

    @Test fun compactLargeTextKeepsAutoAndManualActionsReachable() {
        showModes(fontScale = 1.5f)
        compose.onNodeWithText("Auto").performScrollTo().performClick()
        compose.onNodeWithText("Keep for this session").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Resume Auto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Auto settings").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Add match").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        compose.onNodeWithText("Manual").performScrollTo().performClick()
        compose.onNodeWithText("Custom", substring = false).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Custom", substring = false).assertIsSelected()
    }

    @Test fun matchTagDropdownFiltersDefaultsAndSwitchesVocabulary() {
        AutoSoundPreferences.saveSettings(context, AutoSoundConfig(enabled = true, rules = emptyList()))
        showModes()
        compose.onNodeWithText("Auto settings").performScrollTo().performClick()
        compose.onNodeWithText("Add match").performScrollTo().performClick()
        compose.onNodeWithText("Tag value").performClick()
        compose.onNodeWithText("Jazz", substring = false).assertExists()
        compose.onNodeWithText("Tag value").performTextInput("elec")
        compose.onNodeWithText("Electronic", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Jazz", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Electronic", substring = false).performClick()
        compose.onNodeWithText("Style", substring = false).performClick()
        compose.onNodeWithText("Tag value").performTextClearance()
        compose.onNodeWithText("Tag value").performTextInput("ambi")
        compose.onNodeWithText("Ambient", substring = false).assertIsDisplayed().performClick()
        compose.onNodeWithText("Save match").performClick()
        compose.runOnIdle {
            assertEquals(listOf(AutoSoundRule(AutoSoundField.STYLE, "Ambient", SoundMode.REFERENCE)),
                AutoSoundPreferences.state.value.config.rules)
        }
    }

    private fun assertVisibleReason() {
        val reason = AutoSoundPreferences.state.value.reason
        assertTrue("Auto should explain its selection", reason.isNotBlank())
        compose.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
    }

    private fun showModes(fontScale: Float = 1f, onSelect: (SoundMode) -> Unit = {}) {
        compose.setContent {
            var manual by androidx.compose.runtime.remember { mutableStateOf(SoundMode.BASS) }
            MusicMateTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Box(Modifier.size(320.dp, 640.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            AutoTuningSoundModes(manual) { manual = it; onSelect(it) }
                        }
                    }
                }
            }
        }
    }
}
