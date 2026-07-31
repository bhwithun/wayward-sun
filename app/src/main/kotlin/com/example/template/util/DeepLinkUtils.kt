package com.example.template.util

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController

/**
 * Deep link handling utilities.
 *
 * The template registers two example schemes/hosts in AndroidManifest.xml:
 *   - https://template.example.com/...
 *   - templateapp://open/...
 *
 * Extend this file with your own route mapping logic.
 */
object DeepLinkUtils {

    /**
     * Returns true if the intent looks like a deep link we care about.
     */
    fun isDeepLink(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return when (data.scheme) {
            "https", "http" -> data.host == "template.example.com"
            "templateapp" -> data.host == "open"
            else -> false
        }
    }

    /**
     * Attempts to convert a deep link Uri into a navigation route.
     * Returns null if the link is not recognized.
     *
     * Example mappings:
     *   https://template.example.com/detail/123   ->  detail/123
     *   templateapp://open/detail/42              ->  detail/42
     */
    fun routeFromDeepLink(uri: Uri): String? {
        val segments = uri.pathSegments.filter { it.isNotBlank() }

        return when {
            // /detail/{id}
            segments.size >= 2 && segments[0].equals("detail", ignoreCase = true) -> {
                val id = segments[1]
                "detail/${id}"
            }

            // /home or root → home
            segments.isEmpty() || (segments.size == 1 && segments[0].equals("home", ignoreCase = true)) -> {
                "home"
            }

            else -> null
        }
    }

    /**
     * Handle a deep link by navigating if we can parse it.
     * Call this from onCreate / onNewIntent in MainActivity when you want
     * to support deep linking into the app.
     */
    fun handleDeepLink(intent: Intent?, navController: NavController): Boolean {
        val data = intent?.data ?: return false
        if (!isDeepLink(intent)) return false

        val route = routeFromDeepLink(data) ?: return false

        // Pop everything and go to the target (simple behavior for template)
        navController.popBackStack(route = "home", inclusive = false)
        navController.navigate(route)
        return true
    }

    /**
     * Convenience: build an example deep link Uri for testing.
     */
    fun buildExampleDeepLink(id: String): Uri =
        Uri.parse("https://template.example.com/detail/$id")
}
