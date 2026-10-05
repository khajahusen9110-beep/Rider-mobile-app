package com.example.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** Which table changed, so the screen can reload just that data. */
enum class LiveTable { RIDES, NOTIFICATIONS }

/**
 * Supabase Realtime (Phoenix channels over a WebSocket) for the customer's rides and notifications.
 * RLS still applies, so the customer only hears about rows they may read.
 * It reconnects with backoff; the screens also poll, so a dropped socket only delays updates.
 */
class RealtimeClient(
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ref = AtomicInteger(1)
    private val _changes = MutableSharedFlow<LiveTable>(extraBufferCapacity = 16)
    val changes: SharedFlow<LiveTable> = _changes

    private var socket: WebSocket? = null
    private var loop: Job? = null
    private var userId: String? = null
    private var topic: String? = null

    @Volatile private var connected = false

    fun start(userId: String) {
        if (this.userId == userId && loop?.isActive == true) return
        stop()
        this.userId = userId
        topic = "realtime:rider-$userId"
        loop = scope.launch { runLoop() }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, null)
        socket = null
        connected = false
        userId = null
    }

    private suspend fun runLoop() {
        var backoffMs = 2_000L
        while (scope.isActive && userId != null) {
            val opened = runCatching { connectOnce() }.getOrDefault(false)
            if (opened) backoffMs = 2_000L
            // Keep the channel alive while the socket is up; refresh the token on the server as it rotates.
            var lastToken: String? = null
            while (connected) {
                send(JSONObject().put("topic", "phoenix").put("event", "heartbeat").put("payload", JSONObject()))
                val token = runCatching { tokenProvider() }.getOrNull()
                if (token != null && lastToken != null && token != lastToken) {
                    topic?.let { send(message(it, "access_token", JSONObject().put("access_token", token))) }
                }
                if (token != null) lastToken = token
                delay(HEARTBEAT_MS)
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        }
    }

    private suspend fun connectOnce(): Boolean {
        val uid = userId ?: return false
        val token = tokenProvider()
        val url = "${SupabaseConfig.REALTIME_URL}?apikey=${SupabaseConfig.ANON_KEY}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()
        socket?.cancel()
        socket = http.newWebSocket(request, Listener(uid, token))
        // Wait briefly for the open callback.
        repeat(20) {
            if (connected) return true
            delay(250)
        }
        return connected
    }

    private inner class Listener(private val uid: String, private val token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected = true
            val changes = JSONArray()
                .put(change("*", "rides", "customer_id=eq.$uid"))
                .put(change("INSERT", "notifications", "user_id=eq.$uid"))
            val config = JSONObject()
                .put("broadcast", JSONObject().put("self", false))
                .put("presence", JSONObject().put("key", ""))
                .put("postgres_changes", changes)
            val payload = JSONObject().put("config", config).put("access_token", token)
            topic?.let { send(message(it, "phx_join", payload)) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (json.optString("event") != "postgres_changes") return
            val table = json.optJSONObject("payload")?.optJSONObject("data")?.optString("table")
            when (table) {
                "rides" -> _changes.tryEmit(LiveTable.RIDES)
                "notifications" -> _changes.tryEmit(LiveTable.NOTIFICATIONS)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket == socket) connected = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Realtime disconnected: ${t.message}")
            if (webSocket == socket) connected = false
        }
    }

    private fun change(event: String, table: String, filter: String) = JSONObject()
        .put("event", event).put("schema", "public").put("table", table).put("filter", filter)

    private fun message(topic: String, event: String, payload: JSONObject): JSONObject {
        val r = ref.getAndIncrement().toString()
        return JSONObject().put("topic", topic).put("event", event).put("payload", payload)
            .put("ref", r).put("join_ref", if (event == "phx_join") r else JSONObject.NULL)
    }

    private fun send(json: JSONObject) {
        if (!json.has("ref")) json.put("ref", ref.getAndIncrement().toString())
        socket?.send(json.toString())
    }

    private companion object {
        const val TAG = "Realtime"
        const val HEARTBEAT_MS = 25_000L
    }
}
