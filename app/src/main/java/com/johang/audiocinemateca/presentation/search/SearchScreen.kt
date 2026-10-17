package com.johang.audiocinemateca.presentation.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    searchState: SearchState,
    recentSearches: List<SearchHistoryEntity>,
    onQueryChange: (String) -> Unit,
    onSearchAction: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    onDeleteRecentItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var itemToDeleteFromHistory by remember { mutableStateOf<String?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Enfocar el cuadro de búsqueda al entrar
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Regresar",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                text = "Buscar por título, director, etc...",
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    onSearchAction(searchQuery.trim())
                                }
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onQueryChange("") },
                                        modifier = Modifier.audiocinematecaAccessibility(
                                            label = "Borrar texto",
                                            role = Role.Button
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onVoiceClick,
                                    modifier = Modifier.audiocinematecaAccessibility(
                                        label = "Búsqueda por voz",
                                        role = Role.Button
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (searchState) {
                is SearchState.Initial -> {
                    if (recentSearches.isNotEmpty()) {
                        RecentSearchesView(
                            recentSearches = recentSearches,
                            onQuerySelect = { query ->
                                onQueryChange(query)
                                onSearchAction(query)
                            },
                            onDeleteItem = { itemToDeleteFromHistory = it },
                            onClearAll = { showClearHistoryDialog = true }
                        )
                    } else {
                        EmptySearchView(
                            icon = Icons.Default.Search,
                            title = "Busca tus títulos favoritos",
                            description = "Escribe el nombre de una película, serie, actor, director o tema para encontrarlo al instante."
                        )
                    }
                }
                is SearchState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Buscando en el catálogo...",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is SearchState.NoResults -> {
                    EmptySearchView(
                        icon = Icons.Default.SearchOff,
                        title = "No encontramos resultados",
                        description = "No encontramos ninguna obra que coincida con \"${searchState.query}\". Revisa la ortografía o intenta con palabras más generales."
                    )
                }
                is SearchState.HasResults -> {
                    SearchResultsListView(
                        results = searchState.results,
                        query = searchState.query,
                        onItemClick = onItemClick
                    )
                }
            }
        }
    }

    // Diálogo para eliminar una búsqueda del historial
    itemToDeleteFromHistory?.let { query ->
        AlertDialog(
            onDismissRequest = { itemToDeleteFromHistory = null },
            title = { Text(text = "Eliminar del historial") },
            text = { Text(text = "¿Deseas eliminar \"$query\" de tus búsquedas recientes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRecentItem(query)
                        itemToDeleteFromHistory = null
                    }
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteFromHistory = null }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Diálogo para borrar todo el historial de búsquedas
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(text = "Borrar historial de búsqueda") },
            text = { Text(text = "¿Estás seguro de que deseas vaciar todas las búsquedas recientes?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(text = "Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }
}

@Composable
fun RecentSearchesView(
    recentSearches: List<SearchHistoryEntity>,
    onQuerySelect: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Búsquedas recientes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClearAll) {
                    Text(text = "Borrar todo", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        items(
            items = recentSearches,
            key = { it.query }
        ) { item ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQuerySelect(item.query) }
                    .audiocinematecaAccessibility(
                        label = "Búsqueda anterior: ${item.query}",
                        role = Role.Button
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.query,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDeleteItem(item.query) },
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Eliminar ${item.query} del historial",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultsListView(
    results: List<CatalogItem>,
    query: String,
    onItemClick: (CatalogItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "${results.size} resultado${if (results.size != 1) "s" else ""} para \"$query\"",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        items(
            items = results,
            key = { it.id }
        ) { item ->
            SearchResultCard(
                item = item,
                query = query,
                onClick = { onItemClick(item) }
            )
        }
    }
}

@Composable
fun SearchResultCard(
    item: CatalogItem,
    query: String,
    onClick: () -> Unit
) {
    val typeLabel = remember(item) {
        when (item) {
            is Movie -> "Película"
            is Serie -> "Serie"
            is Documentary -> "Documental"
            is ShortFilm -> "Cortometraje"
            else -> "Contenido"
        }
    }

    val highlightedTitle = remember(item.title, query) {
        buildAnnotatedString {
            val text = item.title
            if (query.isBlank()) {
                append(text)
            } else {
                val lowerText = text.lowercase(Locale.ROOT)
                val lowerQuery = query.lowercase(Locale.ROOT)
                var startIndex = 0

                while (startIndex < text.length) {
                    val index = lowerText.indexOf(lowerQuery, startIndex)
                    if (index >= 0) {
                        if (index > startIndex) {
                            append(text.substring(startIndex, index))
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Black)) {
                            append(text.substring(index, index + query.length))
                        }
                        startIndex = index + query.length
                    } else {
                        append(text.substring(startIndex))
                        break
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .audiocinematecaAccessibility(
                label = "${item.title}. $typeLabel",
                role = Role.Button
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = highlightedTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            SuggestionChip(
                onClick = onClick,
                label = { Text(text = typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.height(26.dp)
            )
        }
    }
}

@Composable
fun EmptySearchView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
