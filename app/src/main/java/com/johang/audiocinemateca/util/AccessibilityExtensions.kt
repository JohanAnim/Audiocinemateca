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

enum class GamepadNavDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class AccessibilityFocusMoveResult(
    val moved: Boolean,
    val hitBoundary: Boolean,
    val focusedLabel: String? = null,
    val scrolledPendingFocus: Boolean = false
)

private data class PanelNodeEntry(
    val node: SemanticsNode,
    val panelKey: String,
    val scrollableAncestor: SemanticsNode?
)

private class LogicalUiPanel(
    val panelKey: String,
    val rows: List<List<SemanticsNode>>,
    val scrollableNode: SemanticsNode?,
    val topY: Float
) {
    val canScrollVertically: Boolean
        get() = scrollableNode?.config?.contains(SemanticsProperties.VerticalScrollAxisRange) == true
}

private fun isNodeFocusableForGamepad(node: SemanticsNode): Boolean {
    if (node.boundsInRoot.isEmpty || node.boundsInRoot.width <= 1f || node.boundsInRoot.height <= 1f) {
        return false
    }
    val config = node.config
    if (config.contains(SemanticsProperties.Disabled)) {
        return false
    }
    val hasClick = config.contains(SemanticsActions.OnClick)
    val hasEditable = config.contains(SemanticsProperties.EditableText) || config.contains(SemanticsActions.SetText)
    val hasSetProgress = config.contains(SemanticsActions.SetProgress)
    val hasRole = config.contains(SemanticsProperties.Role)
    val isHeading = config.contains(SemanticsProperties.Heading)
    val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.trim()
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.trim()
    val hasReadableText = !desc.isNullOrBlank() || !text.isNullOrBlank()

    // Ignorar contenedores puramente estructurales sin acción ni encabezado propio
    val isPureContainer = config.getOrNull(SemanticsProperties.IsTraversalGroup) == true &&
        !hasClick && !hasEditable && !hasSetProgress && !isHeading

    if (isPureContainer) return false

    return hasClick || hasEditable || hasSetProgress || isHeading || (hasRole && hasReadableText) ||
        (config.isMergingSemanticsOfDescendants && hasReadableText)
}

private fun extractNodeSpokenLabel(node: SemanticsNode): String? {
    val config = node.config
    val parts = mutableListOf<String>()

    val stateDesc = config.getOrNull(SemanticsProperties.StateDescription)?.trim()
    val contentDesc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(". ")?.trim()
    val editableText = config.getOrNull(SemanticsProperties.EditableText)?.text?.trim()
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(". ") { it.text }?.trim()

    val primary = when {
        !contentDesc.isNullOrBlank() -> contentDesc
        !editableText.isNullOrBlank() -> editableText
        !text.isNullOrBlank() -> text
        else -> null
    }
    if (!primary.isNullOrBlank()) {
        parts.add(primary)
    }
    if (!stateDesc.isNullOrBlank() && primary?.contains(stateDesc, ignoreCase = true) != true) {
        parts.add(stateDesc)
    }
    if (config.getOrNull(SemanticsProperties.Selected) == true &&
        parts.none { it.contains("seleccionado", ignoreCase = true) }
    ) {
        parts.add("Seleccionado")
    }
    return parts.joinToString(", ").ifBlank { null }
}

private fun collectPanelNodeEntries(
    node: SemanticsNode,
    restrictToTagPrefix: String?,
    currentScrollable: SemanticsNode?,
    out: MutableList<PanelNodeEntry>
) {
    val nextScrollable = if (node.config.contains(SemanticsActions.ScrollBy) &&
        (node.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
            node.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
    ) {
        node
    } else {
        currentScrollable
    }

    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    val matchesPrefix = restrictToTagPrefix == null || (tag != null && tag.startsWith(restrictToTagPrefix))

    if (matchesPrefix && isNodeFocusableForGamepad(node)) {
        val panelKey = when {
            restrictToTagPrefix != null -> "restricted_$restrictToTagPrefix"
            nextScrollable != null -> "scroll_${nextScrollable.id}"
            node.config.getOrNull(SemanticsProperties.Role) == Role.Tab -> {
                "tabs_row_${(node.boundsInRoot.center.y / 76f).toInt()}"
            }
            else -> {
                "bar_row_${(node.boundsInRoot.center.y / 76f).toInt()}"
            }
        }
        out.add(
            PanelNodeEntry(
                node = node,
                panelKey = panelKey,
                scrollableAncestor = nextScrollable
            )
        )
        if (node.config.isMergingSemanticsOfDescendants || node.config.contains(SemanticsActions.OnClick)) {
            return
        }
    }

    for (child in node.children) {
        collectPanelNodeEntries(child, restrictToTagPrefix, nextScrollable, out)
    }
}

private fun buildLogicalPanels(
    rootNode: SemanticsNode,
    restrictToTagPrefix: String?
): List<LogicalUiPanel> {
    val entries = mutableListOf<PanelNodeEntry>()
    collectPanelNodeEntries(rootNode, restrictToTagPrefix, null, entries)
    if (entries.isEmpty()) return emptyList()

    val grouped = entries.distinctBy { it.node.id }.groupBy { it.panelKey }
    val panels = mutableListOf<LogicalUiPanel>()

    for ((key, groupEntries) in grouped) {
        val sortedByY = groupEntries.map { it.node }.sortedBy { it.boundsInRoot.center.y }
        val rows = mutableListOf<MutableList<SemanticsNode>>()

        for (item in sortedByY) {
            val itemCenterY = item.boundsInRoot.center.y
            val matchingRow = rows.lastOrNull { row ->
                val rowAvgY = row.map { it.boundsInRoot.center.y }.average().toFloat()
                kotlin.math.abs(itemCenterY - rowAvgY) <= 42f
            }
            if (matchingRow != null) {
                matchingRow.add(item)
            } else {
                rows.add(mutableListOf(item))
            }
        }

        val finalRows = rows.map { row ->
            row.sortedBy { it.boundsInRoot.left }
        }.filter { it.isNotEmpty() }

        if (finalRows.isNotEmpty()) {
            val topY = finalRows.first().minOf { it.boundsInRoot.top }
            val scrollable = groupEntries.firstOrNull { it.scrollableAncestor != null }?.scrollableAncestor
            panels.add(
                LogicalUiPanel(
                    panelKey = key,
                    rows = finalRows,
                    scrollableNode = scrollable,
                    topY = topY
                )
            )
        }
    }

    return panels.sortedBy { it.topY }
}

private fun nodeMatchesOrContainsId(node: SemanticsNode, targetId: Int): Boolean {
    if (node.id == targetId) return true
    for (child in node.children) {
        if (nodeMatchesOrContainsId(child, targetId)) return true
    }
    return false
}

private fun canScrollVerticallyInDirection(scrollableNode: SemanticsNode?, down: Boolean): Boolean {
    val node = scrollableNode ?: return false
    val vRange = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return false
    val value = vRange.value()
    val maxValue = vRange.maxValue()
    val reverse = vRange.reverseScrolling
    return if (!reverse) {
        if (down) value < maxValue - 1f else value > 1f
    } else {
        if (down) value > 1f else value < maxValue - 1f
    }
}

private fun canScrollHorizontallyInDirection(scrollableNode: SemanticsNode?, right: Boolean): Boolean {
    val node = scrollableNode ?: return false
    val hRange = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) ?: return false
    val value = hRange.value()
    val maxValue = hRange.maxValue()
    return if (right) value < maxValue - 1f else value > 1f
}

private fun View.focusTargetSemanticsNode(
    provider: android.view.accessibility.AccessibilityNodeProvider,
    currentFocusedId: Int?,
    targetNode: SemanticsNode
): AccessibilityFocusMoveResult {
    val focusTarget = findEditableDescendant(targetNode) ?: targetNode
    if (currentFocusedId != null && currentFocusedId != focusTarget.id) {
        provider.performAction(
            currentFocusedId,
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
            null
        )
    }
    val focused = requestComposeAccessibilityFocusById(focusTarget.id)
    val label = extractNodeSpokenLabel(targetNode)
    if (!focused) {
        val a11yEvent = obtainAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED).apply {
            packageName = context.packageName
            className = Button::class.java.name
            isEnabled = true
            if (!label.isNullOrBlank()) {
                contentDescription = label
            }
            setSource(this@focusTargetSemanticsNode, focusTarget.id)
        }
        parent?.requestSendAccessibilityEvent(this, a11yEvent)
    }
    return AccessibilityFocusMoveResult(
        moved = true,
        hitBoundary = false,
        focusedLabel = label,
        scrolledPendingFocus = false
    )
}

/**
 * Mueve el foco real de accesibilidad en 2D (Arriba, Abajo, Izquierda, Derecha)
 * respetando estrictamente la orientación y los límites del panel actual:
 * - En barras horizontales: solo permite Izquierda / Derecha; Arriba / Abajo da límite.
 * - En listas verticales de 1 columna: solo permite Arriba / Abajo (con auto-scroll); Izquierda / Derecha da límite.
 * - En cuadrículas 2D: permite moverse entre filas (Arriba/Abajo) y columnas de la misma fila (Izquierda/Derecha) con límites reales.
 */
fun View.moveComposeAccessibilityFocusInPanel(
    direction: GamepadNavDirection,
    restrictToTagPrefix: String? = null
): AccessibilityFocusMoveResult {
    val rootForTest = this as? RootForTest ?: return AccessibilityFocusMoveResult(false, false)
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return AccessibilityFocusMoveResult(false, false)
    val provider = accessibilityNodeProvider ?: return AccessibilityFocusMoveResult(false, false)

    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        val panels = buildLogicalPanels(owner.rootSemanticsNode, restrictToTagPrefix)
        if (panels.isEmpty()) {
            return@runCatching AccessibilityFocusMoveResult(moved = false, hitBoundary = true)
        }

        val currentFocusedId = findFocusedSemanticsNode(owner.rootSemanticsNode, provider, null)
            ?: findFocusedSemanticsNode(owner.unmergedRootSemanticsNode, provider, null)

        var activePanelIndex = -1
        var activeRowIndex = -1
        var activeColIndex = -1

        if (currentFocusedId != null) {
            outer@ for (pIdx in panels.indices) {
                val rows = panels[pIdx].rows
                for (rIdx in rows.indices) {
                    val row = rows[rIdx]
                    for (cIdx in row.indices) {
                        if (nodeMatchesOrContainsId(row[cIdx], currentFocusedId)) {
                            activePanelIndex = pIdx
                            activeRowIndex = rIdx
                            activeColIndex = cIdx
                            break@outer
                        }
                    }
                }
            }
        }

        // Si ningún elemento tiene foco aún, enfocar el elemento principal de la pantalla
        if (activePanelIndex == -1) {
            val defaultPanel = panels.firstOrNull { p ->
                p.rows.any { r -> r.any { it.config.getOrNull(SemanticsProperties.TestTag) == "player_play_pause_button" } }
            } ?: panels.firstOrNull { it.canScrollVertically } ?: panels.first()

            val defaultNode = defaultPanel.rows.flatten().firstOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == "player_play_pause_button"
            } ?: defaultPanel.rows.first().first()

            return@runCatching focusTargetSemanticsNode(provider, currentFocusedId, defaultNode)
        }

        val activePanel = panels[activePanelIndex]
        val currentRow = activePanel.rows[activeRowIndex]
        val currentNode = currentRow[activeColIndex]

        when (direction) {
            GamepadNavDirection.LEFT, GamepadNavDirection.RIGHT -> {
                val isRight = direction == GamepadNavDirection.RIGHT
                val targetCol = if (isRight) activeColIndex + 1 else activeColIndex - 1

                if (targetCol in currentRow.indices) {
                    val targetNode = currentRow[targetCol]
                    return@runCatching focusTargetSemanticsNode(provider, currentFocusedId, targetNode)
                }

                // Verificar si el panel tiene scroll horizontal real (ej. carrusel horizontal)
                if (canScrollHorizontallyInDirection(activePanel.scrollableNode, right = isRight)) {
                    val scrollAction = activePanel.scrollableNode?.config?.getOrNull(SemanticsActions.ScrollBy)?.action
                    if (scrollAction != null) {
                        val deltaX = if (isRight) (width * 0.45f) else -(width * 0.45f)
                        if (scrollAction.invoke(deltaX, 0f)) {
                            return@runCatching AccessibilityFocusMoveResult(
                                moved = true,
                                hitBoundary = false,
                                focusedLabel = null,
                                scrolledPendingFocus = true
                            )
                        }
                    }
                }

                // Límite horizontal alcanzado dentro de la fila o lista vertical
                return@runCatching AccessibilityFocusMoveResult(
                    moved = false,
                    hitBoundary = true,
                    focusedLabel = null
                )
            }

            GamepadNavDirection.UP, GamepadNavDirection.DOWN -> {
                val isDown = direction == GamepadNavDirection.DOWN

                // Si estamos en una barra horizontal (1 sola fila y sin scroll vertical), Arriba/Abajo es límite directo
                if (activePanel.rows.size == 1 && !activePanel.canScrollVertically) {
                    return@runCatching AccessibilityFocusMoveResult(
                        moved = false,
                        hitBoundary = true,
                        focusedLabel = null
                    )
                }

                val targetRowIndex = if (isDown) activeRowIndex + 1 else activeRowIndex - 1
                if (targetRowIndex in activePanel.rows.indices) {
                    val targetRow = activePanel.rows[targetRowIndex]
                    val currentCenterX = currentNode.boundsInRoot.center.x
                    val targetNode = targetRow.minByOrNull {
                        kotlin.math.abs(it.boundsInRoot.center.x - currentCenterX)
                    } ?: targetRow.first()

                    return@runCatching focusTargetSemanticsNode(provider, currentFocusedId, targetNode)
                }

                // Estamos en el borde superior o inferior visible del panel: verificar si tiene scroll vertical disponible
                if (canScrollVerticallyInDirection(activePanel.scrollableNode, down = isDown)) {
                    val scrollAction = activePanel.scrollableNode?.config?.getOrNull(SemanticsActions.ScrollBy)?.action
                    if (scrollAction != null) {
                        val deltaY = if (isDown) (height * 0.42f) else -(height * 0.42f)
                        if (scrollAction.invoke(0f, deltaY)) {
                            return@runCatching AccessibilityFocusMoveResult(
                                moved = true,
                                hitBoundary = false,
                                focusedLabel = null,
                                scrolledPendingFocus = true
                            )
                        }
                    }
                }

                // Límite vertical alcanzado en el panel
                return@runCatching AccessibilityFocusMoveResult(
                    moved = false,
                    hitBoundary = true,
                    focusedLabel = null
                )
            }
        }
    }.getOrDefault(AccessibilityFocusMoveResult(false, false))
}

/**
 * Cambia el foco real de accesibilidad al siguiente o anterior Panel de la pantalla (usado con el Joystick Derecho).
 * Por ejemplo: Barra Superior <-> Pestañas de Contenido <-> Lista Principal <-> MiniReproductor <-> Pestañas Principales.
 */
fun View.switchComposeAccessibilityPanel(
    nextPanel: Boolean,
    restrictToTagPrefix: String? = null
): AccessibilityFocusMoveResult {
    val rootForTest = this as? RootForTest ?: return AccessibilityFocusMoveResult(false, false)
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return AccessibilityFocusMoveResult(false, false)
    val provider = accessibilityNodeProvider ?: return AccessibilityFocusMoveResult(false, false)

    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        val panels = buildLogicalPanels(owner.rootSemanticsNode, restrictToTagPrefix)
        if (panels.isEmpty()) {
            return@runCatching AccessibilityFocusMoveResult(moved = false, hitBoundary = true)
        }

        val currentFocusedId = findFocusedSemanticsNode(owner.rootSemanticsNode, provider, null)
            ?: findFocusedSemanticsNode(owner.unmergedRootSemanticsNode, provider, null)

        var currentPanelIndex = -1
        if (currentFocusedId != null) {
            for (pIdx in panels.indices) {
                val found = panels[pIdx].rows.any { row ->
                    row.any { nodeMatchesOrContainsId(it, currentFocusedId) }
                }
                if (found) {
                    currentPanelIndex = pIdx
                    break
                }
            }
        }

        val targetPanelIndex = when {
            currentPanelIndex == -1 -> if (nextPanel) 0 else panels.lastIndex
            nextPanel -> currentPanelIndex + 1
            else -> currentPanelIndex - 1
        }

        if (targetPanelIndex !in panels.indices) {
            return@runCatching AccessibilityFocusMoveResult(
                moved = false,
                hitBoundary = true,
                focusedLabel = null
            )
        }

        val targetPanel = panels[targetPanelIndex]
        val allPanelNodes = targetPanel.rows.flatten()

        // Priorizar dentro del panel destino: botón principal del reproductor -> pestaña seleccionada -> primer elemento
        val targetNode = allPanelNodes.firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag) == "player_play_pause_button"
        } ?: allPanelNodes.firstOrNull {
            it.config.getOrNull(SemanticsProperties.Selected) == true
        } ?: allPanelNodes.first()

        focusTargetSemanticsNode(provider, currentFocusedId, targetNode)
    }.getOrDefault(AccessibilityFocusMoveResult(false, false))
}

private fun findSemanticsNodeById(node: SemanticsNode, id: Int): SemanticsNode? {
    if (node.id == id) return node
    for (child in node.children) {
        findSemanticsNodeById(child, id)?.let { return it }
    }
    return null
}

/**
 * Ejecuta la acción principal de clic (`OnClick` / `ACTION_CLICK`) sobre el nodo que tiene
 * actualmente el foco de accesibilidad. Si ningún nodo tiene foco, enfoca el primero disponible.
 */
fun View.performClickOnAccessibilityFocusedNode(restrictToTagPrefix: String? = null): Boolean {
    val rootForTest = this as? RootForTest ?: return false
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return false
    val provider = accessibilityNodeProvider ?: return false

    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        val focusedId = findFocusedSemanticsNode(owner.rootSemanticsNode, provider, null)
            ?: findFocusedSemanticsNode(owner.unmergedRootSemanticsNode, provider, null)

        if (focusedId == null) {
            val moveRes = moveComposeAccessibilityFocusInPanel(
                direction = GamepadNavDirection.DOWN,
                restrictToTagPrefix = restrictToTagPrefix
            )
            return@runCatching moveRes.moved
        }

        val node = findSemanticsNodeById(owner.rootSemanticsNode, focusedId)
            ?: findSemanticsNodeById(owner.unmergedRootSemanticsNode, focusedId)

        val semanticClick = node?.config?.getOrNull(SemanticsActions.OnClick)?.action
        if (semanticClick != null) {
            return@runCatching semanticClick.invoke()
        }

        provider.performAction(focusedId, AccessibilityNodeInfo.ACTION_CLICK, null)
    }.getOrDefault(false)
}

/**
 * Ejecuta la acción secundaria / menú de opciones (`OnLongClick` / `ACTION_LONG_CLICK`) sobre el nodo
 * que tiene actualmente el foco de accesibilidad.
 */
fun View.performLongClickOnAccessibilityFocusedNode(): Boolean {
    val rootForTest = this as? RootForTest ?: return false
    val owner = runCatching { rootForTest.semanticsOwner }.getOrNull() ?: return false
    val provider = accessibilityNodeProvider ?: return false

    return runCatching {
        provider.createAccessibilityNodeInfo(View.NO_ID)
        val focusedId = findFocusedSemanticsNode(owner.rootSemanticsNode, provider, null)
            ?: findFocusedSemanticsNode(owner.unmergedRootSemanticsNode, provider, null)
            ?: return@runCatching false

        val node = findSemanticsNodeById(owner.rootSemanticsNode, focusedId)
            ?: findSemanticsNodeById(owner.unmergedRootSemanticsNode, focusedId)

        val semanticLongClick = node?.config?.getOrNull(SemanticsActions.OnLongClick)?.action
        if (semanticLongClick != null) {
            return@runCatching semanticLongClick.invoke()
        }

        provider.performAction(focusedId, AccessibilityNodeInfo.ACTION_LONG_CLICK, null)
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
