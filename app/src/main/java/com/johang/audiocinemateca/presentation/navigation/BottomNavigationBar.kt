package com.johang.audiocinemateca.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

data class NavigationItem(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
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
    val hideHomeFeedFlow = remember(prefs) {
        prefs.observeBoolean("hide_home_feed", false)
    }
    val hideHomeFeed by hideHomeFeedFlow.collectAsState(
        initial = prefs.getBoolean("hide_home_feed", false)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = remember(currentRoute) {
        currentRoute?.substringBefore("?")?.substringBefore("/")
    }

    val items = if (hideHomeFeed) {
        listOf(
            NavigationItem(
                route = NavRoutes.CATALOG,
                selectedIcon = Icons.Filled.Movie,
                unselectedIcon = Icons.Outlined.Movie,
                label = "Explorar",
                accessibilityLabel = "Explorar"
            ),
            NavigationItem(
                route = NavRoutes.MY_LISTS,
                selectedIcon = Icons.Filled.Bookmarks,
                unselectedIcon = Icons.Outlined.Bookmarks,
                label = "Mis Listas",
                accessibilityLabel = "Mis Listas"
            ),
            NavigationItem(
                route = NavRoutes.ACCOUNT,
                selectedIcon = Icons.Filled.AccountCircle,
                unselectedIcon = Icons.Outlined.AccountCircle,
                label = "Cuenta",
                accessibilityLabel = "Cuenta"
            )
        )
    } else {
        listOf(
            NavigationItem(
                route = NavRoutes.HOME,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                label = "Inicio",
                accessibilityLabel = "Inicio"
            ),
            NavigationItem(
                route = NavRoutes.CATALOG,
                selectedIcon = Icons.Filled.Movie,
                unselectedIcon = Icons.Outlined.Movie,
                label = "Explorar",
                accessibilityLabel = "Explorar"
            ),
            NavigationItem(
                route = NavRoutes.MY_LISTS,
                selectedIcon = Icons.Filled.Bookmarks,
                unselectedIcon = Icons.Outlined.Bookmarks,
                label = "Mis Listas",
                accessibilityLabel = "Mis Listas"
            ),
            NavigationItem(
                route = NavRoutes.ACCOUNT,
                selectedIcon = Icons.Filled.AccountCircle,
                unselectedIcon = Icons.Outlined.AccountCircle,
                label = "Cuenta",
                accessibilityLabel = "Cuenta"
            )
        )
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
                    alwaysShowLabel = true,
                    icon = {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
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

