package com.multistream.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Scaffold raiz de la app: [NavigationSuiteScaffold] decide el componente de navegacion segun el
 * tamano de ventana (bottom bar en telefono, rail en tablet), envuelto por [MultistreamTheme]
 * desde `MainActivity`.
 *
 * Regla de insets del spec: este scaffold padre NUNCA lleva `safeDrawingPadding` (los insets de
 * sistema los aplica cada pantalla a su `contentPadding`; el nav component ya consume el inset
 * del sistema que usa, asi que no hay doble padding).
 *
 * Etiquetas en ingles: regla global del proyecto de textos de UI.
 */
private data class RootDestination(
    val label: String,
    val icon: ImageVector,
    /** Ruta del NavHost a la que navega el item desde la barra/rail. */
    val route: String,
    /** Prefijo de ruta que marca el item seleccionado (`settings/platforms` -> `settings`). */
    val routePrefix: String
)

private val RootDestinations = listOf(
    RootDestination(label = "Home", icon = Icons.Filled.Home, route = "home", routePrefix = "home"),
    RootDestination(
        label = "Go Live",
        icon = Icons.Filled.Cast,
        route = "streaming",
        routePrefix = "streaming"
    ),
    RootDestination(
        label = "Settings",
        icon = Icons.Filled.Settings,
        route = "settings",
        routePrefix = "settings"
    )
)

@Composable
fun RootScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            RootDestinations.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(text = destination.label) },
                    selected = currentRoute?.startsWith(destination.routePrefix) == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        content()
    }
}
