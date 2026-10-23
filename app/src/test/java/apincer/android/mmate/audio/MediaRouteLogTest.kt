package apincer.android.mmate.audio

import android.app.UiModeManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaRouteLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val manager = mockk<AudioManager>(relaxed = true)
    private val attributes = mockk<AudioAttributes>()
    private val speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel")
    private val car = device(31, AudioDeviceInfo.TYPE_USB_ACCESSORY, "MU-X")
    private val uiMode = mockk<UiModeManager> { every { currentModeType } returns Configuration.UI_MODE_TYPE_CAR }
    private var usbFormats = mapOf<Int, AudioFormat>()

    private val carBluetooth = object : MediaRouteLog.Links {
        override fun describe() = "hfp=[\"CAR2-4BF4\"@AA:BB] a2dp=[]"
    }

    private fun routeLog(file: java.io.File) = MediaRouteLog(manager, uiMode, attributes, { usbFormats[it] }, carBluetooth, file)

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) } returns arrayOf(speaker, car)
    }

    @After
    fun unmockLog() = unmockkStatic(Log::class)

    @Test
    fun logsEachRouteChangeOnceWithTheCarSignature() {
        val file = folder.newFile()
        val log = routeLog(file)
        every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(speaker)

        log.check("devices added")
        log.check("playing")
        every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(car)
        log.check("devices added")

        val lines = file.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].contains("devices added: " + MediaRouteLog.describe(speaker) + " | usbFormat=android default"))
        assertTrue(lines[0], lines[0].contains("| uiMode=${Configuration.UI_MODE_TYPE_CAR} | outputs=[2:\"Pixel\"#2, 12:\"MU-X\"#31] | bt=hfp=[\"CAR2-4BF4\"@AA:BB] a2dp=[]"))
        assertTrue(lines[1], lines[1].contains("type=${AudioDeviceInfo.TYPE_USB_ACCESSORY} (USB Accessory)"))
        assertTrue(lines[1], lines[1].contains("product=\"MU-X\" id=31"))
        assertTrue(lines[1], lines[1].contains("usbDacPath=true"))
    }

    @Test
    fun logsNoneWhenAndroidReportsNoMediaDevice() {
        val file = folder.newFile()
        every { manager.getAudioDevicesForAttributes(attributes) } returns emptyList()

        routeLog(file).check("devices removed")

        assertTrue(file.readText(), file.readText().contains("devices removed: none | usbFormat=android default"))
    }

    @Test
    fun keepsOnlyTheLatestLines() {
        val file = folder.newFile()
        file.writeText((1..MediaRouteLog.MAX_LINES).joinToString("\n", postfix = "\n") { "old $it" })
        every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(car)

        routeLog(file).check("playing")

        val lines = file.readLines()
        assertEquals(MediaRouteLog.MAX_LINES, lines.size)
        assertEquals("old 2", lines.first())
        assertTrue(lines.last().contains("playing: "))
    }

    @Test
    fun reportsTheFormatMusicMateAskedTheRoutedDeviceFor() {
        val file = folder.newFile()
        val format = mockk<AudioFormat> {
            every { sampleRate } returns 96000
            every { encoding } returns AudioFormat.ENCODING_PCM_24BIT_PACKED
        }
        usbFormats = mapOf(31 to format)
        every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(car)

        routeLog(file).check("playing")

        assertTrue(file.readText(), file.readText().contains("usbFormat=96 kHz / 24-bit"))
    }

    @Test
    fun pollWaitsBetweenChecks() {
        every { manager.getAudioDevicesForAttributes(attributes) } returns listOf(speaker)
        val log = routeLog(folder.newFile())

        log.check("devices added")
        log.poll()

        verify(exactly = 1) { manager.getAudioDevicesForAttributes(attributes) }
    }

    @Test
    fun marksOnlyUsbTypesAsTheUsbDacPath() {
        assertTrue(MediaRouteLog.describe(speaker).endsWith("usbDacPath=false"))
        assertTrue(MediaRouteLog.describe(car).endsWith("usbDacPath=true"))
    }

    private fun device(deviceId: Int, deviceType: Int, product: String) = mockk<AudioDeviceInfo> {
        every { id } returns deviceId
        every { type } returns deviceType
        every { productName } returns product
        every { address } returns ""
        every { sampleRates } returns intArrayOf(48000)
        every { encodings } returns intArrayOf()
    }
}
