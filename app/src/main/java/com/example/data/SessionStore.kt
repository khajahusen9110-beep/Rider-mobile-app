package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persists the Supabase auth session. Backups are disabled in the manifest so tokens never leave the device. */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rider_session", Context.MODE_PRIVATE)

    val accessToken: String? get() = prefs.getString(KEY_ACCESS, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH, null)

    /** Epoch seconds when the access token expires. */
    val expiresAt: Long get() = prefs.getLong(KEY_EXPIRES_AT, 0L)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val phone: String? get() = prefs.getString(KEY_PHONE, null)

    val isLoggedIn: Boolean get() = !userId.isNullOrEmpty() && !refreshToken.isNullOrEmpty()

    /** The FCM token last registered with the backend, so logout can remove it. */
    var pushToken: String?
        get() = prefs.getString(KEY_PUSH_TOKEN, null)
        set(value) = prefs.edit { putString(KEY_PUSH_TOKEN, value) }

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: ("android-" + UUID.randomUUID().toString()).also {
            prefs.edit { putString(KEY_DEVICE_ID, it) }
        }

    /** Places the customer searched for recently, newest first. */
    var recentPlaces: List<Place>
        get() = runCatching {
            JSONArray(prefs.getString(KEY_RECENTS, "[]")).mapObjects { j ->
                Place(j.getDouble("lat"), j.getDouble("lng"), j.optString("address"))
            }
        }.getOrDefault(emptyList())
        set(value) = prefs.edit {
            val array = JSONArray()
            value.take(MAX_RECENTS).forEach {
                array.put(JSONObject().put("lat", it.lat).put("lng", it.lng).put("address", it.address))
            }
            putString(KEY_RECENTS, array.toString())
        }

    fun addRecentPlace(place: Place) {
        recentPlaces = listOf(place) + recentPlaces.filterNot { it.address == place.address }
    }

    fun saveSession(accessToken: String, refreshToken: String, expiresAt: Long, userId: String?, phone: String?) {
        prefs.edit {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_REFRESH, refreshToken)
            putLong(KEY_EXPIRES_AT, expiresAt)
            if (userId != null) putString(KEY_USER_ID, userId)
            if (phone != null) putString(KEY_PHONE, phone)
        }
    }

    /** Clears the login but keeps the install-level device id. */
    fun clear() {
        val device = deviceId
        prefs.edit {
            clear()
            putString(KEY_DEVICE_ID, device)
        }
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_PHONE = "phone"
        const val KEY_PUSH_TOKEN = "push_token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_RECENTS = "recent_places"
        const val MAX_RECENTS = 6
    }
}
