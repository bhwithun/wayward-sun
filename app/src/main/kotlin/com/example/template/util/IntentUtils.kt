package com.example.template.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Common intent helpers that most apps end up needing.
 *
 * All functions are safe: they check for a resolving activity before starting
 * and show a short toast if nothing can handle the intent.
 */
object IntentUtils {

    private fun Context.toastIfNoHandler(intent: Intent, fallbackMessage: String) {
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, fallbackMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Share ----------------

    fun shareText(context: Context, text: String, title: String = "Share") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // ---------------- SMS ----------------

    /**
     * Open the SMS app with an optional pre-filled message.
     * Note: Some devices ignore the message body for security reasons.
     */
    fun sendSms(context: Context, phoneNumber: String, message: String = "") {
        val uri = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (message.isNotBlank()) {
                putExtra("sms_body", message)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.toastIfNoHandler(intent, "No SMS app found")
        runCatching { context.startActivity(intent) }
    }

    // ---------------- Phone ----------------

    fun dial(context: Context, phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.toastIfNoHandler(intent, "No dialer found")
        runCatching { context.startActivity(intent) }
    }

    // ---------------- Web / URLs ----------------

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.toastIfNoHandler(intent, "No browser found")
        runCatching { context.startActivity(intent) }
    }

    // ---------------- Email ----------------

    fun sendEmail(
        context: Context,
        address: String,
        subject: String = "",
        body: String = ""
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.toastIfNoHandler(intent, "No email app found")
        runCatching { context.startActivity(intent) }
    }

    // ---------------- Generic ----------------

    /**
     * Start any intent safely. Returns true if an activity was found and started.
     */
    fun start(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }
}
