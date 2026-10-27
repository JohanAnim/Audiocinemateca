package com.johang.audiocinemateca.util

import android.content.Context
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import kotlinx.coroutines.delay

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
    customActions: List<CustomAccessibilityAction>? = null,
    testTag: String? = null,
    traversalIndex: Float? = null
): Modifier = this.semantics(mergeDescendants = true) {
    if (!label.isNullOrBlank()) {
        this.contentDescription = label
    }
    role?.let { this.role = it }
    testTag?.let { this.testTag = it }
    traversalIndex?.let { this.traversalIndex = it }
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
 * Marca un título o separador visual como encabezado nativo compatible tanto con TalkBack (`heading()`)
 * como con Jieshuo (`stateDescription = "Encabezado"`), permitiendo saltos rápidos por rotor.
 */
fun Modifier.accessibleHeading(title: String): Modifier = this.clearAndSetSemantics {
    this.heading()
    this.contentDescription = title
    this.stateDescription = "Encabezado"
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

private fun findEditableDescendant(node: SemanticsNode): SemanticsNode? {
    if (node.config.contains(SemanticsProperties.EditableText) ||
        node.config.contains(SemanticsActions.SetText)
    ) {
        return node
    }
    for (child in node.children) {
        findEditableDescendant(child)?.let { return it }
    }
    return null
}

private fun findInSemanticsTree(node: SemanticsNode, tag: String): SemanticsNode? {
    if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) {
        return findEditableDescendant(node) ?: node
    }
    for (child in node.children) {
        findInSemanticsTree(child, tag)?.let { return it }
    }
    return null
}

private fun obtainAccessibilityEvent(eventType: Int): AccessibilityEvent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        AccessibilityEvent(eventType)
    } else {
        @Suppress("DEPRECATION")
        AccessibilityEvent.obtain(eventType)
    }
}

/**
 * Busca un [SemanticsNode] activo dentro del árbol de Compose asociado a este [View]
 * utilizando su [SemanticsProperties.TestTag].
 */
private fun View.findSemanticsNodeByTag(tag: String): SemanticsNode? {
    val rootForTest = this as? RootForTest ?: return null
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return null
    return runCatching<SemanticsNode?> {
        findInSemanticsTree(owner.rootSemanticsNode, tag)
            ?: findInSemanticsTree(owner.unmergedRootSemanticsNode, tag)
    }.getOrNull()
}

/**
 * Verifica si el nodo semántico identificado por [tag] tiene actualmente el foco de accesibilidad.
 */
fun View.isComposeNodeAccessibilityFocused(tag: String): Boolean {
    val node = findSemanticsNodeByTag(tag) ?: return false
    val provider = accessibilityNodeProvider ?: return false
    return runCatching {
        provider.createAccessibilityNodeInfo(node.id)?.isAccessibilityFocused == true
    }.getOrDefault(false)
}

private fun findFocusedSemanticsNode(
    node: SemanticsNode,
    provider: android.view.accessibility.AccessibilityNodeProvider,
    excludeTagPrefix: String? = null
): Int? {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    val isExcluded = excludeTagPrefix != null && tag?.startsWith(excludeTagPrefix) == true
    if (!isExcluded) {
        val isFocused = runCatching {
            provider.createAccessibilityNodeInfo(node.id)?.isAccessibilityFocused == true
        }.getOrDefault(false)
        if (isFocused) {
            return node.id
        }
    }
    for (child in node.children) {
        findFocusedSemanticsNode(child, provider, excludeTagPrefix)?.let { return it }
    }
    return null
}

/**
 * Devuelve el `virtualViewId` (`SemanticsNode.id`) del nodo de Compose que tiene actualmente
 * el foco de accesibilidad en pantalla, permitiendo guardarlo antes de abrir un cajón o modal
 * y restaurarlo al cerrarlo.
 */
fun View.findCurrentlyAccessibilityFocusedNodeId(excludeTagPrefix: String? = null): Int? {
    val rootForTest = this as? RootForTest ?: return null
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return null
    val provider = accessibilityNodeProvider ?: return null
    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        findFocusedSemanticsNode(owner.rootSemanticsNode, provider, excludeTagPrefix)
            ?: findFocusedSemanticsNode(owner.unmergedRootSemanticsNode, provider, excludeTagPrefix)
    }.getOrNull()
}

/**
 * Restaura el foco de accesibilidad directamente sobre un `virtualViewId` previamente capturado
 * mediante [findCurrentlyAccessibilityFocusedNodeId].
 */
fun View.requestComposeAccessibilityFocusById(virtualViewId: Int): Boolean {
    val provider = accessibilityNodeProvider ?: return false
    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        provider.performAction(
            virtualViewId,
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
            null
        )
        provider.performAction(
            virtualViewId,
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )
    }.getOrDefault(false)
}

/**
 * Fuerza el foco de accesibilidad nativo (`ACTION_ACCESSIBILITY_FOCUS` y `TYPE_VIEW_ACCESSIBILITY_FOCUSED`)
 * y opcionalmente el foco de entrada (`FocusRequester`) sobre un nodo virtual de Jetpack Compose identificado por [tag].
 * Si [requestInputFocus] es `false` (ideal para `TextField` / `OutlinedTextField`), posiciona únicamente el cursor
 * del lector de pantalla (Jieshuo / TalkBack) sin abrir el teclado virtual (IME).
 */
fun View.requestComposeAccessibilityFocus(
    tag: String,
    focusRequester: FocusRequester? = null,
    requestInputFocus: Boolean = true,
    forceReannounce: Boolean = false
): Boolean {
    if (requestInputFocus) {
        runCatching { focusRequester?.requestFocus() }
    }

    val node = findSemanticsNodeByTag(tag) ?: return false
    val provider = accessibilityNodeProvider ?: return false

    return runCatching {
        // Sincronizar el caché de nodos virtuales de AndroidComposeViewAccessibilityDelegateCompat
        provider.createAccessibilityNodeInfo(View.NO_ID)
        val currentInfo = provider.createAccessibilityNodeInfo(node.id)

        if (currentInfo?.isAccessibilityFocused == true && !forceReannounce) {
            return@runCatching true
        }

        if (currentInfo?.isAccessibilityFocused == true) {
            provider.performAction(
                node.id,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null
            )
        }

        if (requestInputFocus) {
            provider.performAction(
                node.id,
                AccessibilityNodeInfo.ACTION_FOCUS,
                null
            )
        }

        var focused = provider.performAction(
            node.id,
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
            null
        )

        if (!focused) {
            provider.performAction(
                node.id,
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                null
            )
            focused = provider.performAction(
                node.id,
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                null
            )
        }

        // Respaldo explícito de evento nativo para modos personalizados de Jieshuo
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (am?.isEnabled == true && !focused) {
            val isEditable = node.config.contains(SemanticsProperties.EditableText) ||
                node.config.contains(SemanticsActions.SetText)
            val targetClassName = if (isEditable) EditText::class.java.name else Button::class.java.name
            val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(". ")
                ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text

            val a11yEvent = obtainAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED).apply {
                packageName = context.packageName
                className = targetClassName
                isEnabled = true
                if (!desc.isNullOrBlank()) {
                    contentDescription = desc
                }
                setSource(this@requestComposeAccessibilityFocus, node.id)
            }
            parent?.requestSendAccessibilityEvent(this, a11yEvent)

            if (requestInputFocus) {
                val focusEvent = obtainAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED).apply {
                    packageName = context.packageName
                    className = targetClassName
                    isEnabled = true
                    if (!desc.isNullOrBlank()) {
                        contentDescription = desc
                    }
                    setSource(this@requestComposeAccessibilityFocus, node.id)
                }
                parent?.requestSendAccessibilityEvent(this, focusEvent)
            }
        }

        provider.createAccessibilityNodeInfo(node.id)?.isAccessibilityFocused == true || focused
    }.getOrDefault(false)
}

/**
 * Modificador global reutilizable que convierte cualquier componente de Compose en un destino de foco
 * inicial garantizado para Jieshuo y TalkBack, preservando `SemanticsProperties.Focused` y
 * disparando `ACTION_ACCESSIBILITY_FOCUS` sobre su nodo virtual tras la transición de pantalla.
 *
 * @param tag Identificador único del nodo semántico en la pantalla.
 * @param focusRequester `FocusRequester` opcional para foco de entrada.
 * @param requestInputFocus `true` para botones/controles normales; `false` para campos de texto (`OutlinedTextField`)
 *                          cuando se desea enfocar con el lector de pantalla sin desplegar el teclado (IME).
 * @param enabled Permite activar o desactivar condicionalmente el auto-foco inicial.
 */
@Composable
fun Modifier.initialAccessibilityFocus(
    tag: String,
    focusRequester: FocusRequester = remember { FocusRequester() },
    requestInputFocus: Boolean = true,
    reassertAfterTransition: Boolean = false,
    enabled: Boolean = true
): Modifier {
    val view = LocalView.current
    var isFocused by remember { mutableStateOf(false) }

    if (enabled) {
        LaunchedEffect(tag, requestInputFocus, reassertAfterTransition) {
            // Esperar únicamente a que Compose confirme el layout y el árbol semántico (instantáneo ~16ms)
            withFrameNanos { }
            withFrameNanos { }
            if (reassertAfterTransition) {
                delay(35L)
            }
            view.requestComposeAccessibilityFocus(
                tag = tag,
                focusRequester = if (requestInputFocus) focusRequester else null,
                requestInputFocus = requestInputFocus,
                forceReannounce = true
            )

            // Respaldo rápido silencioso (solo actúa si el lector aún no había tomado el foco en el frame 1)
            delay(75L)
            if (!view.isComposeNodeAccessibilityFocused(tag)) {
                view.requestComposeAccessibilityFocus(
                    tag = tag,
                    focusRequester = if (requestInputFocus) focusRequester else null,
                    requestInputFocus = requestInputFocus,
                    forceReannounce = true
                )
            }

            // Respaldo final para diálogos / ModalBottomSheet con animación de ventana propia
            delay(140L)
            if (reassertAfterTransition || !view.isComposeNodeAccessibilityFocused(tag)) {
                view.requestComposeAccessibilityFocus(
                    tag = tag,
                    focusRequester = if (requestInputFocus) focusRequester else null,
                    requestInputFocus = requestInputFocus,
                    forceReannounce = reassertAfterTransition
                )
            }
        }
    }

    return if (requestInputFocus) {
        this
            .semantics {
                this.testTag = tag
                this.focused = isFocused
                requestFocus {
                    runCatching { focusRequester.requestFocus() }.isSuccess
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    } else {
        this.semantics {
            this.testTag = tag
        }
    }
}
