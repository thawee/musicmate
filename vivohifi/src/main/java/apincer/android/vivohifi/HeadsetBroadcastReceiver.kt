package apincer.android.vivohifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build

class HeadsetBroadcastReceiver : BroadcastReceiver() {
    internal var started = true

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.HEADSET_PLUG") {
            if (this.started) {
                this.started = false
                return
            }

            val pref = context.getSharedPreferences("mainPreferences", 0)
            val serviceEnabled = pref.getBoolean("serviceEnabled", false)
            if (serviceEnabled) {
                when (intent.getIntExtra("state", -1)) {
                    0 -> context.stopService(Intent(context, DACService::class.java))
                    1 -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(Intent(context, DACService::class.java))
                        } else {
                            context.startService(Intent(context, DACService::class.java))
                        }
                        return
                    }
                }
            }
        }
    }
}
