package com.multistream.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.multistream.feature.settings.SettingsScreen
import com.multistream.feature.streamingui.StreamingScreen

// TODO: En Android 14+ (API 34+) el foreground service `mediaProjection` debe
// iniciarse ANTES de solicitar el consentimiento de MediaProjection, o
// `getMediaProjection` lanzara SecurityException. La implementacion real del
// foreground service queda para un follow-up; el manifiesto ya declara el permiso
// FOREGROUND_SERVICE_MEDIA_PROJECTION.
/**
 * Dos destinos, estructura A del spec: `go-live` (contenido: [StreamingScreen], que emite a
 * todas las plataformas configuradas y no lleva argumento) y `configuracion` (contenido:
 * [SettingsScreen], two-pane con los editores embebidos). No existen rutas intermedias
 * `home`/`settings*`/`streaming*`: la flecha de Configuracion hace popBackStack y vuelve a
 * `go-live` sin pantallas fantasma.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    RootScaffold(navController = navController) {
        NavHost(
            navController = navController,
            startDestination = "go-live"
        ) {
            composable("go-live") {
                StreamingScreen()
            }
            composable("configuracion") {
                SettingsScreen(navController = navController)
            }
        }
    }
}
