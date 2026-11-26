package apincer.android.mmate.service

import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import apincer.music.core.model.AudioTag
import apincer.music.core.playback.ExternalAndroidPlayer
import apincer.music.core.playback.spi.PlaybackCallback
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackWithoutNotificationAccessTest {
    @Test
    fun localControllerPlaysPausesSeeksAndResumes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wave = File.createTempFile("local-playback-", ".wav", context.cacheDir)
        val sampleRate = 44100
        val pcmBytes = sampleRate * 12 * 2
        val data = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + pcmBytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmBytes)
        repeat(sampleRate * 12) { index ->
            data.putShort((1000 * kotlin.math.sin(2 * Math.PI * 440 * index / sampleRate)).toInt().toShort())
        }
        wave.writeBytes(data.array())
        val progress = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        var controller: AndroidPlayerController? = null
        try {
            instrumentation.runOnMainSync {
                controller = AndroidPlayerController(context).also { player ->
                    player.registerCallback(
                        ExternalAndroidPlayer.Factory.createLocalTarget(context, "This phone", "Local audio") as ExternalAndroidPlayer,
                        object : PlaybackCallback() {
                            override fun onPlaybackStateTimeElapsedSeconds(elapsedSeconds: Long) {
                                if (elapsedSeconds >= 1) progress.countDown()
                            }
                        }
                    )
                    player.play(AudioTag(98765).apply {
                        path = wave.absolutePath
                        title = "Local playback regression"
                        audioDuration = 12.0
                    })
                }
            }
            assertTrue("Local playback must advance", progress.await(15, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val player = controller!!
                player.pause()
                assertFalse(player.internalExoPlayer!!.playWhenReady)
                player.seekTo(4000)
                assertEquals(4000L, player.internalExoPlayer!!.currentPosition)
                player.internalExoPlayer!!.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) resumed.countDown()
                    }
                })
                player.resume()
            }
            assertTrue("Local playback must resume after seeking", resumed.await(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { controller?.release() }
            wave.delete()
        }
    }
}
