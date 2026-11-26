package apincer.android.mmate.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import apincer.android.mmate.utils.AudioOutputHelper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class AudioOutputReceiverTest {
    private lateinit var service: MusicMateServiceImpl
    private val context = mockk<Context>()

    @Before
    fun setUp() {
        mockkStatic(Log::class, AudioOutputHelper::class)
        every { Log.d(any(), any()) } returns 0
        every { AudioOutputHelper.clearCachedBluetoothCodec() } just Runs
        every { AudioOutputHelper.refreshBluetoothCodecStatus(any()) } just Runs
        // Run the real receiver without starting Android Service infrastructure.
        service = spyk(mockk<MusicMateServiceImpl>(), recordPrivateCalls = true)
        every { service["isPlaying"]() } returns true
        every { service["isLocalTarget"]() } returns true
        every { service["refreshLocalOutput"]() } returns Unit
        every { service.pausePlayer() } just Runs
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun bluetoothDisconnectRefreshesDevicesWithoutPausingLocalPlayback() {
        receive(BluetoothDevice.ACTION_ACL_DISCONNECTED)

        verify(exactly = 0) { service.pausePlayer() }
        verify(exactly = 1) { AudioOutputHelper.clearCachedBluetoothCodec() }
        verify(exactly = 1) { AudioOutputHelper.refreshBluetoothCodecStatus(context) }
        verify(exactly = 1) { service["refreshLocalOutput"]() }
    }

    @Test
    fun becomingNoisyPausesPlayingLocalAudio() {
        receive(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        verify(exactly = 1) { service.pausePlayer() }
    }

    @Test
    fun becomingNoisyDoesNotPauseRemotePlayback() {
        every { service["isLocalTarget"]() } returns false

        receive(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        verify(exactly = 0) { service.pausePlayer() }
    }

    @Test
    fun becomingNoisyDoesNotPauseIdlePlayback() {
        every { service["isPlaying"]() } returns false

        receive(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        verify(exactly = 0) { service.pausePlayer() }
    }

    private fun receive(action: String) {
        val intent = mockk<Intent>()
        every { intent.action } returns action
        service.AudioOutputReceiver().onReceive(context, intent)
    }
}
