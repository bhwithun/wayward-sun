package com.brian.solwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.brian.solwidget.navigation.AppNavHost
import com.brian.solwidget.ui.theme.SolColors
import com.brian.solwidget.ui.theme.SolWidgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolWidgetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SolColors.Navy
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}
