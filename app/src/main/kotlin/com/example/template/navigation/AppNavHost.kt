package com.example.template.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.template.ui.screens.DetailScreen
import com.example.template.ui.screens.HomeScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onNavigateToDetail = { id ->
                    navController.navigate(NavRoutes.detail(id))
                }
            )
        }

        composable(NavRoutes.DETAIL_ROUTE) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            DetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
