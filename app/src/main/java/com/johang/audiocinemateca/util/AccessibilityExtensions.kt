package com.johang.audiocinemateca.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Extensión para limpiar semánticas automáticas y establecer una descripción clara con su rol.
 * Evita duplicidades en lectores como Jieshuo.
 */
fun Modifier.audiocinematecaAccessibility(
    label: String,
    role: Role? = null,
    onClickLabel: String? = null,
    onClickAction: (() -> Unit)? = null
): Modifier = if (onClickAction != null) {
    // Los controles nativos ya aportan una acción semántica. La reemplazamos por una
    // única descripción y acción para que TalkBack/Jieshuo no anuncie ambas.
    this.clearAndSetSemantics {
        this.contentDescription = label
        role?.let { this.role = it }
        this.onClick(label = onClickLabel ?: label) {
            onClickAction()
            true
        }
    }
} else {
    this.semantics(mergeDescendants = true) {
        this.contentDescription = label
        role?.let { this.role = it }
    }
}
