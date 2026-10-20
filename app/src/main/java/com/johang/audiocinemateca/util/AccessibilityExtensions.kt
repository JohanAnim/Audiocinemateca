package com.johang.audiocinemateca.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Extensión para configurar semánticas accesibles en componentes interactivos o informativos.
 * Evita duplicidades y garantiza que lectores como TalkBack y Jieshuo reconozcan el rol y las acciones.
 */
fun Modifier.audiocinematecaAccessibility(
    label: String? = null,
    role: Role? = null,
    onClickLabel: String? = null,
    onClickAction: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    onLongClickAction: (() -> Unit)? = null,
    customActions: List<CustomAccessibilityAction>? = null
): Modifier = this.semantics(mergeDescendants = true) {
    if (!label.isNullOrBlank()) {
        this.contentDescription = label
    }
    role?.let { this.role = it }
    if (onClickAction != null) {
        this.onClick(label = onClickLabel) {
            onClickAction()
            true
        }
    }
    if (onLongClickAction != null) {
        this.onLongClick(label = onLongClickLabel ?: "Opciones") {
            onLongClickAction()
            true
        }
    }
    if (!customActions.isNullOrEmpty()) {
        this.customActions = customActions
    }
}

/**
 * Construye una descripción semántica unificada e inteligente para lectores de pantalla.
 * Omite la redundante sugerencia de hacer clic y añade recomendaciones contextuales
 * (ej. 'Mantén presionado para opciones') según la configuración global del usuario.
 */
fun buildInteractionDescription(
    mainContent: String,
    hintText: String? = null,
    showHint: Boolean = true
): String {
    val cleanContent = mainContent.trim().removeSuffix(".")
    return if (showHint && !hintText.isNullOrBlank()) {
        "$cleanContent. ${hintText.trim().removeSuffix(".")}."
    } else {
        "$cleanContent."
    }
}
