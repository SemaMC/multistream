package com.multistream.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.multistream.feature.settings.PlatformConfigScreen
import com.multistream.feature.settings.SettingsScreen
import com.multistream.feature.settings.WidgetConfigScreen
import com.multistream.feature.streamingui.HomeScreen
import com.multistream.feature.streamingui.StreamingScreen

// TODO: En Android 14+ (API 34+) el foreground service `mediaProjection` debe
// iniciarse ANTES de solicitar el consentimiento de MediaProjection, o
// `getMediaProjection` lanzara SecurityException. La implementacion real del
// foreground service queda para un follow-up; el manifiesto ya declara el permiso
// FOREGROUND_SERVICE_MEDIA_PROJECTION.
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("settings") {
            SettingsScreen(navController = navController)
        }
        composable("settings/platforms") {
            PlatformConfigScreen()
        }
        composable("settings/widgets") {
            WidgetConfigScreen()
        }
        composable(
            "streaming/{platform}",
            arguments = listOf(navArgument("platform") { type = NavType.StringType })
        ) {
            StreamingScreen()
        }
    }
}
