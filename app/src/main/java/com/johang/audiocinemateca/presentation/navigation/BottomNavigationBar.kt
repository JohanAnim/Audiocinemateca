package com.johang.audiocinemateca.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.johang.audiocinemateca.R

data class NavigationItem(
    val route: String,
    val icon: Int,
    val label: String,
    val accessibilityLabel: String
)

@Composable
fun AudiocinematecaBottomNavigation(
    navController: NavController,
    sharedPreferencesManager: com.johang.audiocinemateca.data.local.SharedPreferencesManager? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) {
        sharedPreferencesManager ?: com.johang.audiocinemateca.data.local.SharedPreferencesManager(context)
    }
    var hideHomeFeed by remember { mutableStateOf(prefs.getBoolean("hide_home_feed", false)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "hide_home_feed") {
                hideHomeFeed = prefs.getBoolean("hide_home_feed", false)
            }
        }
        val sp = prefs.getPrefs()
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = remember(currentRoute) {
        currentRoute?.substringBefore("?")?.substringBefore("/")
    }

    val items = remember(hideHomeFeed) {
        if (hideHomeFeed) {
            listOf(
                NavigationItem(
                    route = NavRoutes.CATALOG,
                    icon = R.drawable.ic_catalog,
                    label = "Explorar",
                    accessibilityLabel = "Explorar"
                ),
                NavigationItem(
                    route = NavRoutes.MY_LISTS,
                    icon = R.drawable.ic_lists,
                    label = "Mis Listas",
                    accessibilityLabel = "Mis Listas"
                ),
                NavigationItem(
                    route = NavRoutes.ACCOUNT,
                    icon = R.drawable.ic_account,
                    label = "Cuenta",
                    accessibilityLabel = "Cuenta"
                )
            )
        } else {
            listOf(
                NavigationItem(
                    route = NavRoutes.HOME,
                    icon = R.drawable.ic_home,
                    label = "Inicio",
                    accessibilityLabel = "Inicio"
                ),
                NavigationItem(
                    route = NavRoutes.CATALOG,
                    icon = R.drawable.ic_catalog,
                    label = "Explorar",
                    accessibilityLabel = "Explorar"
                ),
                NavigationItem(
                    route = NavRoutes.MY_LISTS,
                    icon = R.drawable.ic_lists,
                    label = "Mis Listas",
                    accessibilityLabel = "Mis Listas"
                ),
                NavigationItem(
                    route = NavRoutes.ACCOUNT,
                    icon = R.drawable.ic_account,
                    label = "Cuenta",
                    accessibilityLabel = "Cuenta"
                )
            )
        }
    }

    // Lista de destinos donde NO se debe mostrar la barra
    val hiddenBaseRoutes = remember {
        setOf(
            "player",
            "content_detail",
            "community",
            "ai_chat",
            "notifications"
        )
    }

    val isVisible = baseRoute == null || baseRoute !in hiddenBaseRoutes

    if (isVisible) {
        NavigationBar(
            modifier = Modifier.semantics {
                collectionInfo = CollectionInfo(rowCount = 1, columnCount = items.size)
            }
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = baseRoute == item.route

                val onNavigationClick = {
                    if (!isSelected) {
                        try {
                            val startDestId = navController.graph.findStartDestination().id
                            val isStartDest = item.route == navController.graph.findStartDestination().route
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState = !isStartDest
                                popUpTo(startDestId) {
                                    inclusive = isStartDest
                                    saveState = !isStartDest
                                }
                            }
                        } catch (e: Exception) {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = onNavigationClick,
                    icon = {
                        Icon(
                            painter = painterResource(id = item.icon),
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(text = item.label)
                    },
                    modifier = Modifier.clearAndSetSemantics {
                        role = Role.Tab
                        selected = isSelected
                        stateDescription = "${index + 1} de ${items.size}"
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = 0,
                            rowSpan = 1,
                            columnIndex = index,
                            columnSpan = 1
                        )
                        contentDescription = item.label
                        onClick {
                            onNavigationClick()
                            true
                        }
                    }
                )
            }
        }
    }
}
