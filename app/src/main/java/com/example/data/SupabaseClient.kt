package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An error with a message that is safe to show to the customer. */
open class ApiException(message: String, val httpCode: Int = 0) : Exception(message)

class SessionExpiredException : ApiException("Your session has expired. Please log in again.", 401)

/**
 * Thin Supabase client over OkHttp: GoTrue phone auth with refresh-token renewal,
 * PostgREST tables and RPCs, Edge Functions and Storage uploads.
 */
class SupabaseClient(private val session: SessionStore) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private val refreshMutex = Mutex()
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the refresh token is rejected and the customer must log in again. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    // ---------- Auth ----------

    /** Sends an SMS code. New numbers are created as customers through the signup metadata. */
    suspend fun sendOtp(phone: String, fullName: String?) {
        val data = JSONObject()
            .put("role", "customer")
            .put("phone", phone)
            .put("device_id", session.deviceId)
        if (!fullName.isNullOrBlank()) data.put("full_name", fullName.trim())
        val body = JSONObject()
            .put("phone", phone)
            .put("create_user", true)
            .put("data", data)
        execute(post("$AUTH/otp", body), auth = false)
    }

    suspend fun verifyOtp(phone: String, code: String) {
        val body = JSONObject().put("phone", phone).put("token", code).put("type", "sms")
        saveSession(JSONObject(execute(post("$AUTH/verify", body), auth = false)), phone)
    }

    suspend fun signOut() {
        val token = session.accessToken
        if (token != null) {
            runCatching {
                execute(post("$AUTH/logout", JSONObject()).header("Authorization", "Bearer $token"), auth = false)
            }
        }
        session.clear()
    }

    /** Returns an access token that is valid for at least another minute, refreshing it if needed. */
    suspend fun validAccessToken(): String {
        val token = session.accessToken ?: throw SessionExpiredException()
        if (session.expiresAt - nowSeconds() > 60) return token
        return refresh(token)
    }

    private suspend fun refresh(staleToken: String?): String = refreshMutex.withLock {
        // Another caller may have refreshed while this one waited.
        val current = session.accessToken
        if (current != null && current != staleToken && session.expiresAt - nowSeconds() > 60) {
            return@withLock current
        }

        val refreshToken = session.refreshToken ?: run { expire(); throw SessionExpiredException() }
        val request = post("$AUTH/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken))
        try {
            saveSession(JSONObject(execute(request, auth = false)), null)
        } catch (e: ApiException) {
            if (e.httpCode in 400..499) {
                expire()
                throw SessionExpiredException()
            }
            throw e
        }
        session.accessToken ?: throw SessionExpiredException()
    }

    private fun expire() {
        session.clear()
        _sessionExpired.tryEmit(Unit)
    }

    private fun saveSession(json: JSONObject, phone: String?) {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isEmpty() || refresh.isEmpty()) throw ApiException("Login failed. Please try again.")
        val expiresAt = json.optLong("expires_at").takeIf { it > 0 }
            ?: (nowSeconds() + json.optLong("expires_in", 3600))
        val user = json.optJSONObject("user")
        session.saveSession(access, refresh, expiresAt, user?.optString("id")?.ifEmpty { null }, phone)
    }

    // ---------- PostgREST ----------

    suspend fun rpc(function: String, params: JSONObject = JSONObject()): String =
        execute(post("$REST/rpc/$function", params))

    suspend fun rpcObject(function: String, params: JSONObject = JSONObject()): JSONObject? =
        parseFirst(rpc(function, params))

    /** GET `/rest/v1/<pathAndQuery>`, for example `rides?status=eq.requested&select=*`. */
    suspend fun select(pathAndQuery: String): JSONArray {
        val text = execute(Request.Builder().url("$REST/$pathAndQuery").get())
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    suspend fun insert(table: String, body: JSONObject, returnRows: Boolean = true): JSONArray {
        val request = post("$REST/$table", body)
            .header("Prefer", if (returnRows) "return=representation" else "return=minimal")
        val text = execute(request)
        return if (text.isBlank()) JSONArray() else JSONArray(text)
    }

    /** Calls an Edge Function with the user's token and returns its JSON body. */
    suspend fun function(name: String, body: JSONObject): JSONObject =
        JSONObject(execute(post("$FUNCTIONS/$name", body)).ifBlank { "{}" })

    suspend fun remove(table: String, filter: String) {
        execute(Request.Builder().url("$REST/$table?$filter").delete())
    }

    suspend fun update(table: String, filter: String, body: JSONObject) {
        val request = Request.Builder()
            .url("$REST/$table?$filter")
            .patch(body.toString().toRequestBody(JSON))
            .header("Prefer", "return=minimal")
        execute(request)
    }

    // ---------- Storage ----------

    suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String) {
        val request = Request.Builder()
            .url("$STORAGE/object/$bucket/$path")
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .header("x-upsert", "true")
        execute(request)
    }

    suspend fun signedUrl(bucket: String, path: String, expiresInSeconds: Int = 3600): String {
        val body = JSONObject().put("expiresIn", expiresInSeconds)
        val json = JSONObject(execute(post("$STORAGE/object/sign/$bucket/$path", body)))
        val signed = json.optString("signedURL").ifEmpty { json.optString("signedUrl") }
        if (signed.isEmpty()) throw ApiException("Could not open the file.")
        return if (signed.startsWith("http")) signed else STORAGE + signed
    }

    // ---------- Plumbing ----------

    private fun post(url: String, body: JSONObject): Request.Builder =
        Request.Builder().url(url).post(body.toString().toRequestBody(JSON))

    private suspend fun execute(builder: Request.Builder, auth: Boolean = true): String {
        builder.header("apikey", SupabaseConfig.ANON_KEY)
        if (!auth) {
            if (builder.build().header("Authorization") == null) {
                builder.header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            }
            return send(builder)
        }
        val token = validAccessToken()
        return try {
            send(builder.header("Authorization", "Bearer $token"))
        } catch (e: ApiException) {
            if (e.httpCode != 401) throw e
            // The token was revoked or expired early: refresh once and retry.
            send(builder.header("Authorization", "Bearer ${refresh(token)}"))
        }
    }

    private suspend fun send(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(errorMessage(text, response.code), response.code)
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("No internet connection. Check your network and try again.")
        }
    }

    companion object {
        private const val REST = SupabaseConfig.REST_URL
        private const val AUTH = SupabaseConfig.AUTH_URL
        private const val STORAGE = SupabaseConfig.STORAGE_URL
        private const val FUNCTIONS = SupabaseConfig.FUNCTIONS_URL
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun nowSeconds(): Long = System.currentTimeMillis() / 1000

        fun parseFirst(text: String): JSONObject? {
            val trimmed = text.trim()
            return when {
                trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        }

        /** Turns Supabase / PostgREST / GoTrue error bodies into a readable sentence. */
        fun errorMessage(body: String, code: Int): String {
            val json = runCatching { JSONObject(body) }.getOrNull()
            val raw = json?.let {
                listOf("message", "msg", "error_description", "error")
                    .firstNotNullOfOrNull { key -> it.optString(key).takeIf { v -> v.isNotBlank() && v != "null" } }
            }
            return when {
                raw != null && raw.contains("Token has expired or is invalid", ignoreCase = true) ->
                    "That code is wrong or has expired. Request a new one."
                raw != null && raw.contains("rate limit", ignoreCase = true) ->
                    "Too many attempts. Please wait a minute and try again."
                raw != null && raw.contains("JWT expired", ignoreCase = true) -> "Session expired"
                raw != null && raw.contains("duplicate key value") -> "This already exists."
                raw != null && raw.contains("violates row-level security") -> "You are not allowed to do this."
                raw != null -> raw
                code == 401 || code == 403 -> "You are not allowed to do this."
                code >= 500 -> "Server error. Please try again shortly."
                else -> "Request failed ($code)."
            }
        }
    }
}
