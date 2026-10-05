package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import java.util.UUID

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("goride_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val KEY_IS_SUSPENDED = "is_suspended"
        private const val KEY_SUSPENSION_REASON = "suspension_reason"
        private const val KEY_SUSPENDED_UNTIL = "suspended_until"
        private const val KEY_ACTIVE_RIDE_ID = "active_ride_id"
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = "and_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putString(KEY_FULL_NAME, session.fullName)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .apply()
    }

    fun updateProfile(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_PHONE, profile.phone)
            .putBoolean(KEY_IS_SUSPENDED, profile.isSuspended)
            .putString(KEY_SUSPENSION_REASON, profile.suspensionReason)
            .putString(KEY_SUSPENDED_UNTIL, profile.suspendedUntil)
            .putString(KEY_REFERRAL_CODE, profile.referralCode)
            .apply()
    }

    fun getSession(): UserSession? {
        val uid = prefs.getString(KEY_USER_ID, null) ?: return null
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val name = prefs.getString(KEY_FULL_NAME, "Customer") ?: "Customer"
        val token = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        return UserSession(userId = uid, phone = phone, fullName = name, accessToken = token)
    }

    fun getProfile(): UserProfile {
        val uid = prefs.getString(KEY_USER_ID, "") ?: ""
        return UserProfile(
            id = uid,
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            fullName = prefs.getString(KEY_FULL_NAME, "Customer") ?: "Customer",
            isSuspended = prefs.getBoolean(KEY_IS_SUSPENDED, false),
            suspensionReason = prefs.getString(KEY_SUSPENSION_REASON, null),
            suspendedUntil = prefs.getString(KEY_SUSPENDED_UNTIL, null),
            referralCode = prefs.getString(KEY_REFERRAL_CODE, "") ?: ""
        )
    }

    var activeRideId: String?
        get() = prefs.getString(KEY_ACTIVE_RIDE_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_RIDE_ID, value).apply()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFERRAL_CODE)
            .remove(KEY_IS_SUSPENDED)
            .remove(KEY_SUSPENSION_REASON)
            .remove(KEY_SUSPENDED_UNTIL)
            .remove(KEY_ACTIVE_RIDE_ID)
            .apply()
    }
}
