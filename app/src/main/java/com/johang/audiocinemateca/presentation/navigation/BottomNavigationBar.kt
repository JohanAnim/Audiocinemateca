package com.johang.audiocinemateca.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

data class NavigationItem(
    val resourceId: Int,
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
    val prefs = remember(context) { sharedPreferencesManager ?: com.johang.audiocinemateca.data.local.SharedPreferencesManager(context) }
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

    var currentDestination by remember { mutableStateOf(navController.currentDestination) }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentDestination = destination
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    val items = remember(hideHomeFeed) {
        if (hideHomeFeed) {
            listOf(
                NavigationItem(
                    resourceId = R.id.catalogFragment,
                    icon = R.drawable.ic_catalog,
                    label = "Explorar",
                    accessibilityLabel = "Explorar"
                ),
                NavigationItem(
                    resourceId = R.id.myListsFragment,
                    icon = R.drawable.ic_lists,
                    label = "Mis Listas",
                    accessibilityLabel = "Mis Listas"
                ),
                NavigationItem(
                    resourceId = R.id.accountFragment,
                    icon = R.drawable.ic_account,
                    label = "Cuenta",
                    accessibilityLabel = "Cuenta"
                )
            )
        } else {
            listOf(
                NavigationItem(
                    resourceId = R.id.homeFragment,
                    icon = R.drawable.ic_home,
                    label = "Inicio",
                    accessibilityLabel = "Inicio"
                ),
                NavigationItem(
                    resourceId = R.id.catalogFragment,
                    icon = R.drawable.ic_catalog,
                    label = "Explorar",
                    accessibilityLabel = "Explorar"
                ),
                NavigationItem(
                    resourceId = R.id.myListsFragment,
                    icon = R.drawable.ic_lists,
                    label = "Mis Listas",
                    accessibilityLabel = "Mis Listas"
                ),
                NavigationItem(
                    resourceId = R.id.accountFragment,
                    icon = R.drawable.ic_account,
                    label = "Cuenta",
                    accessibilityLabel = "Cuenta"
                )
            )
        }
    }

    // Lista de destinos donde NO se debe mostrar la barra
    val hiddenDestinations = listOf(
        R.id.playerFragment,
        R.id.contentDetailFragment,
        R.id.communityFragment,
        R.id.aiChatFragment,
        R.id.notificationsFragment
    )

    val isVisible = currentDestination?.id !in hiddenDestinations

    if (isVisible) {
        NavigationBar {
            items.forEach { item ->
                val isSelected = currentDestination?.id == item.resourceId
                
                val onNavigationClick = {
                    if (!isSelected) {
                        try {
                            val startDestId = navController.graph.startDestinationId
                            val isStartDest = (item.resourceId == startDestId)
                            val navOptions = NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setRestoreState(!isStartDest)
                                .setPopUpTo(startDestId, isStartDest, !isStartDest)
                                .build()
                            
                            navController.navigate(item.resourceId, null, navOptions)
                        } catch (e: Exception) {
                            navController.navigate(item.resourceId)
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
                        contentDescription = item.label
                        role = Role.Tab
                        selected = isSelected
                        if (!isSelected) {
                            onClick(label = "Ir a ${item.label}") {
                                onNavigationClick()
                                true
                            }
                        }
                    }
                )
            }
        }
    }
}
