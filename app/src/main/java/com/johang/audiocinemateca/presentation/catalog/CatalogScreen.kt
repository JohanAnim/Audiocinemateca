package com.johang.audiocinemateca.presentation.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.model.FilterOptions
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import kotlinx.coroutines.launch

private val FILTER_TYPES = listOf("Alfabéticamente", "Fecha", "Género", "Idiomas", "Países")
private val ALPHABETICAL_OPTIONS = listOf("A-Z", "Z-A")
private val DATE_OPTIONS = listOf("Más nuevo", "Más antiguo", "Con fecha más reciente", "Con fecha más antigua")
private val LANGUAGE_OPTIONS = listOf("Español Latino", "Español de España")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    initialTabIndex: Int = 0,
    catalogViewModel: CatalogViewModel,
    peliculasViewModel: PeliculasViewModel,
    seriesViewModel: SeriesViewModel,
    cortometrajesViewModel: CortometrajesViewModel,
    documentalesViewModel: DocumentalesViewModel,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSurpriseMeClick: (String) -> Unit,
    onItemClick: (String, String) -> Unit,
    onShareItem: (CatalogItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 3)) }

    val currentCategory = remember(selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> "peliculas"
            1 -> "series"
            2 -> "cortometrajes"
            3 -> "documentales"
            else -> "peliculas"
        }
    }

    val currentFilterOptions by catalogViewModel.filterRepository
        .getFilterOptionsFlow(currentCategory)
        .collectAsState(initial = FilterOptions("Fecha", "Más nuevo"))

    // Dialog states
    var showFilterTypeDialog by remember { mutableStateOf(false) }
    var showFilterValueDialog by remember { mutableStateOf(false) }
    var showSearchableDialog by remember { mutableStateOf(false) }
    var searchableDialogTitle by remember { mutableStateOf("") }
    var searchableDialogItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSearchableItems by remember { mutableStateOf(false) }

    // LazyListStates to preserve scroll position per tab
    val moviesListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val cortosListState = rememberLazyListState()
    val docsListState = rememberLazyListState()

    // Data streams
    val movies by peliculasViewModel.movies.collectAsState()
    val movieFavorites by peliculasViewModel.favoriteIds.collectAsState()
    val isMoviesLoading by peliculasViewModel.isLoading.collectAsState()

    val series by seriesViewModel.series.collectAsState()
    val seriesFavorites by seriesViewModel.favoriteIds.collectAsState()
    val isSeriesLoading by seriesViewModel.isLoading.collectAsState()

    val cortometrajes by cortometrajesViewModel.cortometrajes.collectAsState()
    val cortosFavorites by cortometrajesViewModel.favoriteIds.collectAsState()
    val isCortosLoading by cortometrajesViewModel.isLoading.collectAsState()

    val documentales by documentalesViewModel.documentales.collectAsState()
    val docsFavorites by documentalesViewModel.favoriteIds.collectAsState()
    val isDocsLoading by documentalesViewModel.isLoading.collectAsState()

    val tabs = listOf("Películas", "Series", "Cortometrajes", "Documentales")

    Scaffold(
        topBar = {
            CatalogToolbar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick,
                onSurpriseMeClick = { onSurpriseMeClick(currentCategory) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.clearAndSetSemantics {
                            role = Role.Tab
                            selected = isSelected
                            contentDescription = "$title, ${index + 1} de ${tabs.size}"
                            onClick {
                                selectedTabIndex = index
                                true
                            }
                        }
                    )
                }
            }

            // Filters Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Type Button
                    OutlinedButton(
                        onClick = { showFilterTypeDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .audiocinematecaAccessibility(
                                label = "Filtrar por: ${currentFilterOptions.filterType}",
                                role = Role.Button,
                                onClickAction = { showFilterTypeDialog = true }
                            ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentFilterOptions.filterType,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Filter Value Button
                    Button(
                        onClick = {
                            when (currentFilterOptions.filterType) {
                                "Género", "Países" -> {
                                    val isGenre = currentFilterOptions.filterType == "Género"
                                    searchableDialogTitle = if (isGenre) "Seleccionar género" else "Seleccionar país"
                                    showSearchableDialog = true
                                    isLoadingSearchableItems = true
                                    coroutineScope.launch {
                                        searchableDialogItems = if (isGenre) {
                                            catalogViewModel.getGenresForCategory(currentCategory)
                                        } else {
                                            catalogViewModel.getCountriesForCategory(currentCategory)
                                        }
                                        isLoadingSearchableItems = false
                                    }
                                }
                                else -> {
                                    showFilterValueDialog = true
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .audiocinematecaAccessibility(
                                label = "Criterio de filtro: ${currentFilterOptions.filterValue}",
                                role = Role.Button,
                                onClickAction = {
                                    if (currentFilterOptions.filterType == "Género" || currentFilterOptions.filterType == "Países") {
                                        val isGenre = currentFilterOptions.filterType == "Género"
                                        searchableDialogTitle = if (isGenre) "Seleccionar género" else "Seleccionar país"
                                        showSearchableDialog = true
                                        isLoadingSearchableItems = true
                                        coroutineScope.launch {
                                            searchableDialogItems = if (isGenre) {
                                                catalogViewModel.getGenresForCategory(currentCategory)
                                            } else {
                                                catalogViewModel.getCountriesForCategory(currentCategory)
                                            }
                                            isLoadingSearchableItems = false
                                        }
                                    } else {
                                        showFilterValueDialog = true
                                    }
                                }
                            ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = currentFilterOptions.filterValue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Content List per Tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> CatalogList(
                        items = movies,
                        favoriteIds = movieFavorites,
                        isLoading = isMoviesLoading,
                        listState = moviesListState,
                        categoryKey = "peliculas",
                        onLoadMore = { peliculasViewModel.loadMovies() },
                        onItemClick = { id -> onItemClick(id, "peliculas") },
                        onFavoriteToggle = { item, isFav ->
                            (item as? Movie)?.let { peliculasViewModel.toggleFavorite(it, isFav) }
                        },
                        onShareItem = onShareItem,
                        onResetFilters = {
                            catalogViewModel.updateFilter("peliculas", "Fecha", "Más nuevo")
                        }
                    )
                    1 -> CatalogList(
                        items = series,
                        favoriteIds = seriesFavorites,
                        isLoading = isSeriesLoading,
                        listState = seriesListState,
                        categoryKey = "series",
                        onLoadMore = { seriesViewModel.loadSeries() },
                        onItemClick = { id -> onItemClick(id, "series") },
                        onFavoriteToggle = { item, isFav ->
                            (item as? Serie)?.let { seriesViewModel.toggleFavorite(it, isFav) }
                        },
                        onShareItem = onShareItem,
                        onResetFilters = {
                            catalogViewModel.updateFilter("series", "Fecha", "Más nuevo")
                        }
                    )
                    2 -> CatalogList(
                        items = cortometrajes,
                        favoriteIds = cortosFavorites,
                        isLoading = isCortosLoading,
                        listState = cortosListState,
                        categoryKey = "cortometrajes",
                        onLoadMore = { cortometrajesViewModel.loadCortometrajes() },
                        onItemClick = { id -> onItemClick(id, "cortometrajes") },
                        onFavoriteToggle = { item, isFav ->
                            (item as? ShortFilm)?.let { cortometrajesViewModel.toggleFavorite(it, isFav) }
                        },
                        onShareItem = onShareItem,
                        onResetFilters = {
                            catalogViewModel.updateFilter("cortometrajes", "Fecha", "Más nuevo")
                        }
                    )
                    3 -> CatalogList(
                        items = documentales,
                        favoriteIds = docsFavorites,
                        isLoading = isDocsLoading,
                        listState = docsListState,
                        categoryKey = "documentales",
                        onLoadMore = { documentalesViewModel.loadDocumentales() },
                        onItemClick = { id -> onItemClick(id, "documentales") },
                        onFavoriteToggle = { item, isFav ->
                            (item as? Documentary)?.let { documentalesViewModel.toggleFavorite(it, isFav) }
                        },
                        onShareItem = onShareItem,
                        onResetFilters = {
                            catalogViewModel.updateFilter("documentales", "Fecha", "Más nuevo")
                        }
                    )
                }
            }
        }
    }

    // Filter Type Dialog
    if (showFilterTypeDialog) {
        AlertDialog(
            onDismissRequest = { showFilterTypeDialog = false },
            title = { Text("Filtrar por", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FILTER_TYPES.forEach { filterType ->
                        val isSelected = filterType == currentFilterOptions.filterType
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val defaultValue = when (filterType) {
                                        "Alfabéticamente" -> "A-Z"
                                        "Fecha" -> "Más nuevo"
                                        "Género" -> "Todos"
                                        "Idiomas" -> "Español Latino"
                                        "Países" -> "Todos"
                                        else -> ""
                                    }
                                    catalogViewModel.updateFilter(currentCategory, filterType, defaultValue)
                                    showFilterTypeDialog = false
                                }
                                .padding(vertical = 8.dp)
                                .audiocinematecaAccessibility(
                                    label = filterType,
                                    role = Role.RadioButton,
                                    onClickAction = {
                                        val defaultValue = when (filterType) {
                                            "Alfabéticamente" -> "A-Z"
                                            "Fecha" -> "Más nuevo"
                                            "Género" -> "Todos"
                                            "Idiomas" -> "Español Latino"
                                            "Países" -> "Todos"
                                            else -> ""
                                        }
                                        catalogViewModel.updateFilter(currentCategory, filterType, defaultValue)
                                        showFilterTypeDialog = false
                                    }
                                )
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = filterType,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterTypeDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Filter Value Options Dialog (Alfabéticamente, Fecha, Idiomas)
    if (showFilterValueDialog) {
        val options = when (currentFilterOptions.filterType) {
            "Alfabéticamente" -> ALPHABETICAL_OPTIONS
            "Fecha" -> DATE_OPTIONS
            "Idiomas" -> LANGUAGE_OPTIONS
            else -> emptyList()
        }

        AlertDialog(
            onDismissRequest = { showFilterValueDialog = false },
            title = { Text("Selecciona un valor", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        val isSelected = option == currentFilterOptions.filterValue
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    catalogViewModel.updateFilter(
                                        currentCategory,
                                        currentFilterOptions.filterType,
                                        option
                                    )
                                    showFilterValueDialog = false
                                }
                                .padding(vertical = 8.dp)
                                .audiocinematecaAccessibility(
                                    label = option,
                                    role = Role.RadioButton,
                                    onClickAction = {
                                        catalogViewModel.updateFilter(
                                            currentCategory,
                                            currentFilterOptions.filterType,
                                            option
                                        )
                                        showFilterValueDialog = false
                                    }
                                )
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterValueDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Searchable Filter Dialog (Género / Países)
    if (showSearchableDialog) {
        SearchableCatalogFilterDialog(
            title = searchableDialogTitle,
            items = searchableDialogItems,
            currentOption = currentFilterOptions.filterValue,
            isLoading = isLoadingSearchableItems,
            onDismissRequest = { showSearchableDialog = false },
            onItemSelected = { selectedValue ->
                catalogViewModel.updateFilter(
                    currentCategory,
                    currentFilterOptions.filterType,
                    selectedValue
                )
                showSearchableDialog = false
            }
        )
    }
}

@Composable
private fun <T : CatalogItem> CatalogList(
    items: List<T>,
    favoriteIds: Set<String>,
    isLoading: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    categoryKey: String,
    onLoadMore: () -> Unit,
    onItemClick: (String) -> Unit,
    onFavoriteToggle: (CatalogItem, Boolean) -> Unit,
    onShareItem: (CatalogItem) -> Unit,
    onResetFilters: () -> Unit
) {
    // Infinite scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading) {
            onLoadMore()
        }
    }

    if (items.isEmpty() && isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Cargando catálogo, por favor espere...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Text(
                    text = "No se encontraron resultados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "No hay títulos que coincidan con el filtro actual en esta categoría. Puedes intentar restablecer el filtro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onResetFilters,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Restablecer filtros a valores por defecto",
                        role = Role.Button,
                        onClickAction = onResetFilters
                    )
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restablecer filtros")
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { item ->
                val isFavorite = favoriteIds.contains(item.id)
                CatalogItemCard(
                    item = item,
                    isFavorite = isFavorite,
                    categoryKey = categoryKey,
                    onClick = { onItemClick(item.id) },
                    onFavoriteToggle = { onFavoriteToggle(item, !isFavorite) },
                    onShareClick = { onShareItem(item) }
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogItemCard(
    item: CatalogItem,
    isFavorite: Boolean,
    categoryKey: String,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit
) {
    val categoryBadgeText = remember(item, categoryKey) {
        when {
            item is Serie -> if (item.temporadas.isNotBlank() && item.temporadas != "0") "${item.temporadas} temp." else "Serie"
            item is Movie -> "Película"
            item is Documentary -> "Documental"
            item is ShortFilm -> "Corto"
            else -> ""
        }
    }

    val accessibilityDesc = remember(item, isFavorite) {
        val favStatus = if (isFavorite) "En tus favoritos." else ""
        val yearPart = if (item.anio.isNotBlank()) "Año ${item.anio}." else ""
        val genrePart = if (item.genero.isNotBlank()) "Género ${item.genero}." else ""
        "${item.title}. $yearPart $genrePart $favStatus".trim()
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .audiocinematecaAccessibility(
                label = accessibilityDesc,
                role = Role.Button,
                onClickAction = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.anio.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.anio,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (categoryBadgeText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = categoryBadgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (item.pais.isNotBlank()) {
                        Text(
                            text = item.pais,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (item.genero.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.genero,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action buttons: Share & Favorite
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .size(38.dp)
                        .audiocinematecaAccessibility(
                            label = "Compartir ${item.title}",
                            role = Role.Button,
                            onClickAction = onShareClick
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(38.dp)
                        .audiocinematecaAccessibility(
                            label = if (isFavorite) "Quitar ${item.title} de favoritos" else "Guardar ${item.title} en favoritos",
                            role = Role.Button,
                            onClickAction = onFavoriteToggle
                        )
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableCatalogFilterDialog(
    title: String,
    items: List<String>,
    currentOption: String?,
    isLoading: Boolean,
    onDismissRequest: () -> Unit,
    onItemSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) {
            items
        } else {
            val q = searchQuery.trim().lowercase()
            items.filter { it.lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Cerrar diálogo de selección",
                            role = Role.Button,
                            onClickAction = onDismissRequest
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar en la lista...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Borrar texto")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron coincidencias",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = filteredItems,
                            key = { it }
                        ) { option ->
                            val isSelected = option == currentOption
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onItemSelected(option) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .audiocinematecaAccessibility(
                                        label = option,
                                        role = Role.RadioButton,
                                        onClickAction = { onItemSelected(option) }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
