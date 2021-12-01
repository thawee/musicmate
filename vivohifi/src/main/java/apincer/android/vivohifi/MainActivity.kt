package apincer.android.vivohifi

import android.app.ActivityManager
import android.app.ActivityManager.RunningServiceInfo
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ting.mp3.android.R

class MainActivity : AppCompatActivity() {
    internal var context: Context = this
   // internal var preferences: SharedPreferences? = null
    internal var serviceEnabled = false
    internal var notifyToggled = true

    private fun isRunning(var1: Class<*>): Boolean {
        val activities = (this.getSystemService(Service.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(Integer.MAX_VALUE).iterator()
        while (activities.hasNext()) {
            val serviceInfo = activities.next() as RunningServiceInfo
            val className = serviceInfo.service.className
            if (var1.name == className) {
                return true
            }
        }

        return false
    }

    private fun getPreferences(): SharedPreferences {
        return this.getSharedPreferences("mainPreferences", 0)
    }

    private fun savePreferences() {
        try {
            val pref = getPreferences().edit()
            pref.putBoolean("serviceEnabled", this.serviceEnabled)
            pref.putBoolean("notifyToggled", this.notifyToggled)
            pref.commit()
        } catch (var3: Exception) {
        }

    }

    protected override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        this.setContentView(R.layout.activity_main)
        //getPreferences() = this.getSharedPreferences("mainPreferences", 0)
        if (bundle == null) {
            this.serviceEnabled = getPreferences().getBoolean("serviceEnabled", this.serviceEnabled)
            this.notifyToggled = getPreferences().getBoolean("notifyToggled", this.notifyToggled)
        } else {
            this.serviceEnabled = bundle.getBoolean("serviceEnabled", this.serviceEnabled)
            this.notifyToggled = bundle.getBoolean("notifyToggled", this.notifyToggled)
        }

        val var2 = this.findViewById(R.id.toolbar) as Toolbar
        var2.setTitle("HI-FI on VIVO Music Phone")
        this.setSupportActionBar(var2)
        val btnServiceEnabled = this.findViewById(R.id.btn_enable) as Switch
        val btnNotify = this.findViewById(R.id.btn_notify) as Switch
        if (this.serviceEnabled) {
            btnServiceEnabled.isChecked = true
            btnNotify.isEnabled = true
            startDACService()
        } else {
            btnNotify.isEnabled = false
        }

        btnServiceEnabled.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                btnNotify.isEnabled = true
                serviceEnabled = true
                startDACService()
            } else {
                btnNotify.isEnabled = false
                serviceEnabled = false
                if (MainActivity.isRunning(this@MainActivity, DACService::class.java)) {
                    stopService(Intent(context, DACService::class.java))
                }
            }
            savePreferences()
        }
        btnNotify.isChecked = this.notifyToggled
        btnNotify.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                notifyToggled = true
            } else {
                notifyToggled = false
            }
            savePreferences()
        }
    }

    private fun startDACService() {
        if (!this.isRunning(DACService::class.java) && DACService.isWiredHeadsetOn(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(context, DACService::class.java))
            } else {
                startService(Intent(context, DACService::class.java))
            }
        }
    }

    override fun onPause() {
        this.savePreferences()
        super.onPause()
    }

    companion object {

        internal fun isRunning(var0: MainActivity, var1: Class<*>): Boolean {
            return var0.isRunning(var1)
        }
    }
}
