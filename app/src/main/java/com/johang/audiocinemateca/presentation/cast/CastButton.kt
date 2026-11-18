package com.johang.audiocinemateca.presentation.cast

import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

/**
 * Permite ocultar vistas nativas AndroidView (como MediaRouteButton) del árbol de accesibilidad
 * cuando el cajón lateral está abierto para que TalkBack/Jieshuo no atraviesen la superposición.
 */
val LocalDrawerOpenForAccessibility = compositionLocalOf { false }

@Composable
fun CastButton(
    modifier: Modifier = Modifier
) {
    val isDrawerOpen = LocalDrawerOpenForAccessibility.current
    var mediaRouteButton by remember { mutableStateOf<MediaRouteButton?>(null) }

    val accessibilityModifier = if (isDrawerOpen) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.audiocinematecaAccessibility(
            label = "Transmitir audio a Smart TV o altavoz inteligente",
            role = Role.Button,
            onClickAction = { mediaRouteButton?.performClick() }
        )
    }

    AndroidView(
        modifier = modifier
            .size(48.dp)
            .then(accessibilityModifier),
        factory = { context ->
            val appCompatContext = androidx.appcompat.view.ContextThemeWrapper(
                context,
                androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar
            )
            MediaRouteButton(appCompatContext).apply {
                mediaRouteButton = this
                try {
                    CastButtonFactory.setUpMediaRouteButton(appCompatContext, this)
                } catch (e: Exception) {
                    android.util.Log.e("CastButton", "No se pudo configurar CastButtonFactory: ${e.message}")
                }
            }
        },
        update = { button ->
            mediaRouteButton = button
            val a11yMode = if (isDrawerOpen) {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            if (button.importantForAccessibility != a11yMode) {
                button.importantForAccessibility = a11yMode
            }
            val parentView = button.parent as? View
            if (parentView != null && parentView.importantForAccessibility != a11yMode) {
                parentView.importantForAccessibility = a11yMode
            }
        }
    )
}
