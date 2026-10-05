package com.example.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.RiderApp
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Receives FCM pushes sent by the backend's send-push function and keeps the device token registered. */
class PushMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = application as RiderApp
        if (!app.repository.isLoggedIn) return
        scope.launch {
            runCatching { app.repository.registerDeviceToken(token) }
                .onFailure { Log.w(TAG, "Token registration failed: ${it.message}") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        showNotification(this, title, body, message.data["type"].orEmpty(), message.data["ride_id"])
    }

    companion object {
        private const val TAG = "Push"
        const val EXTRA_RIDE_ID = "ride_id"

        private val TRIP_TYPES = setOf(
            "ride_accepted", "driver_cancelled", "ride_completed", "ride_expired", "payment_completed",
            "balance_due", "sos", "scheduled_reminder",
        )

        fun isAvailable(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

        /** Registers this device for pushes. Does nothing until google-services.json is added to the build. */
        suspend fun register(context: Context) {
            if (!isAvailable(context)) return
            val app = context.applicationContext as RiderApp
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                if (token != app.session.pushToken) app.repository.registerDeviceToken(token)
            }.onFailure { Log.w(TAG, "Push registration failed: ${it.message}") }
        }

        /** Removes the local FCM token on logout so the next person on this phone gets a new one. */
        suspend fun unregister(context: Context) {
            if (!isAvailable(context)) return
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        }

        @SuppressLint("MissingPermission")
        fun showNotification(context: Context, title: String, body: String, type: String, rideId: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val isTrip = type in TRIP_TYPES || type.startsWith("ride") || type.startsWith("refund")
            val channel = if (isTrip) RiderApp.CHANNEL_TRIP else RiderApp.CHANNEL_GENERAL
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { if (rideId != null) putExtra(EXTRA_RIDE_ID, rideId) }
            val intent = PendingIntent.getActivity(
                context, rideId?.hashCode() ?: 0, open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_ride)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (isTrip) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(if (isTrip) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(intent)
                .build()
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        }
    }
}
