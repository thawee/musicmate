package apincer.android.mmate.audio.headphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadphoneTonePresetTest {
    @Test fun everyPresetIsAValidToneForBothEars() {
        HeadphoneTonePreset.entries.forEach { preset ->
            ManualHeadphoneTuning(left = preset.tone, right = preset.tone).validate()
        }
    }

    @Test fun ofFindsTheMatchingPresetAndNoneAfterAnEdit() {
        HeadphoneTonePreset.entries.forEach { preset -> assertEquals(preset, HeadphoneTonePreset.of(preset.tone)) }
        assertEquals(HeadphoneTonePreset.FLAT, HeadphoneTonePreset.of(HeadphoneTone()))
        assertNull(HeadphoneTonePreset.of(HeadphoneTonePreset.V_SHAPE.tone.copy(bassDb = 4.5)))
    }
}
