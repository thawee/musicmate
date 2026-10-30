package apincer.android.mmate.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSoundModeTest {
    private val rules = listOf(
        AutoSoundRule(AutoSoundField.STYLE, "Hard Rock", SoundMode.LIVELY),
        AutoSoundRule(AutoSoundField.GENRE, "Rock", SoundMode.BASS)
    )
    private val config = AutoSoundConfig(enabled = true, rules = rules)

    @Test fun matchesWholeTokensAndPreservesRulePriority() {
        assertEquals(SoundMode.REFERENCE, AutoSoundMode.resolve(config, "Baroque", null).mode)
        assertEquals(SoundMode.BASS, AutoSoundMode.resolve(config, "Jazz; ROCK / Pop", null).mode)
        assertEquals(SoundMode.LIVELY, AutoSoundMode.resolve(config, "Rock", " Hard   Rock ").mode)
        assertEquals(SoundMode.REFERENCE, AutoSoundMode.resolve(config, null, null).mode)
    }

    @Test fun fieldsDoNotBleedIntoEachOtherAndFallbackIsExplicit() {
        assertEquals(SoundMode.NIGHT, AutoSoundMode.resolve(config.copy(fallback = SoundMode.NIGHT), null, "Rock").mode)
        assertEquals(SoundMode.REFERENCE, AutoSoundMode.resolve(config, "Hard Rock", null).mode)
    }

    @Test fun settingsRoundTripPreservesOrderAndRejectsMalformedData() {
        assertEquals(config, AutoSoundPreferences.decode(AutoSoundPreferences.encode(config)))
        assertEquals(AutoSoundConfig(), AutoSoundPreferences.decode("broken json"))
        assertEquals(AutoSoundConfig(), AutoSoundPreferences.decode("{\"fallback\":\"FUTURE\"}"))
    }
}
