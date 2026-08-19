package com.brian.solwidget.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object IntentUtils {
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(intent) }
    }
}
