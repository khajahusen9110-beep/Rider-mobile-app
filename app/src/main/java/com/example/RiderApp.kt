package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.Geocoding
import com.example.data.RealtimeClient
import com.example.data.RiderRepository
import com.example.data.SessionStore
import com.example.data.SupabaseClient
import org.osmdroid.config.Configuration
import java.io.File

/** Builds the shared app objects once, for the activity and the push service. */
class RiderApp : Application() {

    lateinit var session: SessionStore
        private set
    lateinit var repository: RiderRepository
        private set
    lateinit var realtime: RealtimeClient
        private set
    lateinit var geocoding: Geocoding
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
        val client = SupabaseClient(session)
        repository = RiderRepository(client, session)
        realtime = RealtimeClient(client.http) { client.validAccessToken() }
        geocoding = Geocoding(this)
        // OpenStreetMap tiles: identify the app and keep the tile cache in app storage.
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_TRIP, getString(R.string.channel_trip), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = getString(R.string.channel_trip_desc)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_GENERAL, getString(R.string.channel_general), NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    companion object {
        const val CHANNEL_TRIP = "trip_updates"
        const val CHANNEL_GENERAL = "general"
    }
}
