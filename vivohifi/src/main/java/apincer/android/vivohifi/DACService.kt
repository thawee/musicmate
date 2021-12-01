package apincer.android.vivohifi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.widget.Toast

import com.ting.mp3.android.R

class DACService : Service() {
    internal var context: Context = this
    //  AudioManager audioManager;

    private var notificationHelper: NotificationHelper? = null

    private val serviceEnabledPreference: Boolean
        get() = this.getSharedPreferences("mainPreferences", 0).getBoolean("serviceEnabled", true)

    private val notifyToggledPreference: Boolean
        get() = this.getSharedPreferences("mainPreferences", 0).getBoolean("notifyToggled", true)

    override fun onBind(var1: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        try {
            if (player != null) {
                val notifyToggled = notifyToggledPreference
                player!!.stop()
                player!!.release()
                if (notifyToggled) {
                    Toast.makeText(context, "HI-FI Disabled", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } catch (var2: Exception) {
        }

    }

    override fun onStartCommand(var1: Intent, var2: Int, var3: Int): Int {
        //this.audioManager = (AudioManager)this.getSystemService(Service.AUDIO_SERVICE);
        enableHifiMode()
        return Service.START_STICKY
    }

    private fun enableHifiMode() {
        try {
            val notifyToggled = notifyToggledPreference
            val serviceEnabled = serviceEnabledPreference
            val wiredHeadsetOn = isWiredHeadsetOn(this)
            if (player != null) {
                player!!.release()
            }

            if (serviceEnabled && wiredHeadsetOn) {
                player = MediaPlayer.create(context, R.raw.silence_song)
                player!!.setVolume(0.0f, 0.0f)
                player!!.isLooping = true
                player!!.start()
                player!!.setOnCompletionListener { enableHifiMode() }
                if (notifyToggled) {
                    Toast.makeText(context, "HI-FI Enabled", Toast.LENGTH_SHORT).show()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (notificationHelper == null) {
                        notificationHelper = NotificationHelper(this)
                    }
                    val notificationBuilder = notificationHelper!!.getNotification("Hi-Fi output mode", "Hi-Fi output mode for all applications")
                    // notificationHelper.notify(20000, notificationBuilder);
                    startForeground(2000, notificationBuilder.build())
                }
            }
        } catch (ex: Exception) {
            if (player != null) {
                player!!.release()
            }
        }

    }

    companion object {
        private var player: MediaPlayer? = null


        fun isWiredHeadsetOn(context: Context): Boolean {
            val audioManager = context.getSystemService(Service.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return audioManager.isWiredHeadsetOn
            } else {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (i in devices.indices) {
                    val device = devices[i]
                    if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
