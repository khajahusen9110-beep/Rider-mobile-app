package com.example.data

/** Public Supabase settings. The anon key is a publishable key; access is enforced by RLS and RPCs. */
object SupabaseConfig {
    const val PROJECT_REF = "rhftkhfabmrziobhopnr"
    const val URL = "https://$PROJECT_REF.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJoZnRraGZhYm1yemlvYmhvcG5yIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTAzNDA3OTgsImV4cCI6MjEwNTkxNjc5OH0.A23YNHmOexCfWv70a1TEi62Sx6cePl6WnoK2NPMPYhQ"

    const val REST_URL = "$URL/rest/v1"
    const val AUTH_URL = "$URL/auth/v1"
    const val STORAGE_URL = "$URL/storage/v1"
    const val FUNCTIONS_URL = "$URL/functions/v1"
    const val REALTIME_URL = "wss://$PROJECT_REF.supabase.co/realtime/v1/websocket"

    const val AVATARS_BUCKET = "avatars"

    /** Public URL of a file in a public bucket. */
    fun publicUrl(bucket: String, path: String) = "$STORAGE_URL/object/public/$bucket/$path"
}
