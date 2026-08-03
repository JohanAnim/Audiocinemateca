package com.johang.audiocinemateca.presentation.cast

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun CastButton(
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .size(48.dp)
            .audiocinematecaAccessibility(
                label = "Transmitir audio a Smart TV o altavoz inteligente",
                role = Role.Button,
                onClickAction = {}
            ),
        factory = { context ->
            val appCompatContext = androidx.appcompat.view.ContextThemeWrapper(
                context,
                androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar
            )
            MediaRouteButton(appCompatContext).apply {
                try {
                    CastButtonFactory.setUpMediaRouteButton(appCompatContext, this)
                } catch (e: Exception) {
                    android.util.Log.e("CastButton", "No se pudo configurar CastButtonFactory: ${e.message}")
                }
            }
        }
    )
}
