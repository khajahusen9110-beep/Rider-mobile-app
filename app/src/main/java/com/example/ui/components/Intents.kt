package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Small wrappers for handing off to the dialer, SMS and share sheet. */
object Intents {
    fun dial(context: Context, phone: String) = launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")), "No phone app found")

    fun sms(context: Context, phone: String, body: String) = launch(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", body),
        "No messaging app found",
    )

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, "Share trip"), "No app to share with")
    }

    private fun launch(context: Context, intent: Intent, fallback: String) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, fallback, Toast.LENGTH_SHORT).show()
        }
    }
}
