package apincer.android.vivohifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import com.ting.mp3.android.R

class NotificationHelper @RequiresApi(api = Build.VERSION_CODES.O)
constructor(base: Context) : ContextWrapper(base) {
    private var notifManager: NotificationManager? = null

    private val manager: NotificationManager
        get() {
            if (notifManager == null) {
                notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            return notifManager!!
        }

    init {
        createChannels()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createChannels() {
        val notificationChannel = NotificationChannel(CHANNEL_ID,
                CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        notificationChannel.enableLights(true)
        notificationChannel.lightColor = Color.RED
        notificationChannel.setShowBadge(true)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(notificationChannel)

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun getNotification(title: String, body: String): Notification.Builder {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
    }

    fun notify(id: Int, notification: Notification.Builder) {
        manager.notify(id, notification.build())
    }

    companion object {

        //Set the channel’s ID//
        val CHANNEL_ID = "apincer.android.vivohifi.ch1"

        //Set the channel’s user-visible name//
        val CHANNEL_NAME = "VIVO Hi-Fi"
    }
}
