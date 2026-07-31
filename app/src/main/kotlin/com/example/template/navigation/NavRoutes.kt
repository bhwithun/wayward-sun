package com.example.template.navigation

/**
 * Defines navigation routes for the app.
 *
 * Using objects + data classes makes routes type-safe and easy to refactor.
 */
object NavRoutes {
    const val HOME = "home"

    // Example detail route with a simple string argument.
    // Usage: navController.navigate(NavRoutes.detail("42"))
    const val DETAIL_ROUTE = "detail/{id}"

    fun detail(id: String): String = "detail/${id}"
}
