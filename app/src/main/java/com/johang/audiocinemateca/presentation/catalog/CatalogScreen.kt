package com.johang.audiocinemateca.presentation.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.model.FilterOptions
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.buildInteractionDescription
import kotlinx.coroutines.launch
import java.util.Locale

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
    var showSortDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSearchableDialog by remember { mutableStateOf(false) }
    var searchableDialogTitle by remember { mutableStateOf("") }
    var searchableDialogType by remember { mutableStateOf("") }
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

            // Advanced Multi-Filter Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Orden Button
                    OutlinedButton(
                        onClick = { showSortDialog = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Criterio de ordenamiento actual: ${currentFilterOptions.sortType}, ${currentFilterOptions.sortValue}. Toca dos veces para cambiar el ordenamiento"
                            role = Role.Button
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Orden: ${currentFilterOptions.sortValue}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 2. Género Filter Chip
                    val isGenreActive = currentFilterOptions.genre != "Todos"
                    FilterChip(
                        selected = isGenreActive,
                        onClick = {
                            searchableDialogTitle = "Seleccionar género"
                            searchableDialogType = "genre"
                            showSearchableDialog = true
                            isLoadingSearchableItems = true
                            coroutineScope.launch {
                                searchableDialogItems = catalogViewModel.getGenresForCategory(currentCategory)
                                isLoadingSearchableItems = false
                            }
                        },
                        label = { Text("Género: ${currentFilterOptions.genre}") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.semantics {
                            contentDescription = "Filtro de género actual: ${currentFilterOptions.genre}. Toca dos veces para cambiar este filtro"
                            role = Role.Button
                        }
                    )

                    // 3. Idioma Filter Chip
                    val isLangActive = currentFilterOptions.language != "Todos"
                    FilterChip(
                        selected = isLangActive,
                        onClick = { showLanguageDialog = true },
                        label = { Text("Idioma: ${currentFilterOptions.language}") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.semantics {
                            contentDescription = "Filtro de idioma actual: ${currentFilterOptions.language}. Toca dos veces para cambiar este filtro"
                            role = Role.Button
                        }
                    )

                    // 4. País Filter Chip
                    val isCountryActive = currentFilterOptions.country != "Todos"
                    FilterChip(
                        selected = isCountryActive,
                        onClick = {
                            searchableDialogTitle = "Seleccionar país"
                            searchableDialogType = "country"
                            showSearchableDialog = true
                            isLoadingSearchableItems = true
                            coroutineScope.launch {
                                searchableDialogItems = catalogViewModel.getCountriesForCategory(currentCategory)
                                isLoadingSearchableItems = false
                            }
                        },
                        label = { Text("País: ${currentFilterOptions.country}") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.semantics {
                            contentDescription = "Filtro de país actual: ${currentFilterOptions.country}. Toca dos veces para cambiar este filtro"
                            role = Role.Button
                        }
                    )

                    // 5. Restablecer Filtros
                    if (isGenreActive || isLangActive || isCountryActive) {
                        IconButton(
                            onClick = { catalogViewModel.resetFilters(currentCategory) },
                            modifier = Modifier.semantics {
                                contentDescription = "Restablecer todos los filtros a sus valores por defecto"
                                role = Role.Button
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                            catalogViewModel.resetFilters("peliculas")
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
                            catalogViewModel.resetFilters("series")
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
                            catalogViewModel.resetFilters("cortometrajes")
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
                            catalogViewModel.resetFilters("documentales")
                        }
                    )
                }
            }
        }
    }

    // Sort Options Dialog
    if (showSortDialog) {
        val sortOptions = listOf(
            Triple("Fecha", "Más nuevo", "Fecha de inclusión: Más nuevo"),
            Triple("Fecha", "Más antiguo", "Fecha de inclusión: Más antiguo"),
            Triple("Alfabéticamente", "A-Z", "Alfabéticamente: A-Z"),
            Triple("Alfabéticamente", "Z-A", "Alfabéticamente: Z-A"),
            Triple("Año", "Más reciente", "Año de estreno: Más reciente"),
            Triple("Año", "Más antiguo", "Año de estreno: Más antiguo")
        )

        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Ordenar lista por", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortOptions.forEach { (sType, sValue, display) ->
                        val isSelected = currentFilterOptions.sortType == sType && currentFilterOptions.sortValue == sValue
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        catalogViewModel.updateSort(currentCategory, sType, sValue)
                                        showSortDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = display,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Language Filter Dialog
    if (showLanguageDialog) {
        val languageOptions = listOf("Todos", "Español Latino", "Español de España")

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Filtrar por Idioma / Doblaje", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languageOptions.forEach { option ->
                        val isSelected = currentFilterOptions.language == option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        catalogViewModel.updateLanguage(currentCategory, option)
                                        showLanguageDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
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
                TextButton(onClick = { showLanguageDialog = false }) {
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
            currentOption = if (searchableDialogType == "genre") currentFilterOptions.genre else currentFilterOptions.country,
            isLoading = isLoadingSearchableItems,
            onDismissRequest = { showSearchableDialog = false },
            onItemSelected = { selectedValue ->
                if (searchableDialogType == "genre") {
                    catalogViewModel.updateGenre(currentCategory, selectedValue)
                } else {
                    catalogViewModel.updateCountry(currentCategory, selectedValue)
                }
                showSearchableDialog = false
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedItemForMenu by remember { mutableStateOf<CatalogItem?>(null) }

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
                    onClick = onResetFilters
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
                    onOptionsClick = { selectedItemForMenu = item },
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

    selectedItemForMenu?.let { item ->
        val isFav = favoriteIds.contains(item.id)
        ModalBottomSheet(
            onDismissRequest = { selectedItemForMenu = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                ListItem(
                    headlineContent = { Text("Ver detalles de la obra", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            selectedItemForMenu = null
                            onItemClick(item.id)
                        }
                )

                ListItem(
                    headlineContent = { Text(if (isFav) "Quitar de favoritos" else "Guardar en favoritos", fontWeight = FontWeight.Medium) },
                    leadingContent = {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFav) Color(0xFFE57373) else MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clickable {
                            selectedItemForMenu = null
                            onFavoriteToggle(item, isFav)
                        }
                )

                ListItem(
                    headlineContent = { Text("Compartir obra", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            selectedItemForMenu = null
                            onShareItem(item)
                        }
                )
            }
        }
    }
}

private fun getDubbingText(rawIdioma: String): String {
    val trimmed = rawIdioma.trim()
    if (trimmed == "2") return "Doblaje Latino"
    if (trimmed == "1") return "Doblaje Español de España"
    val lower = rawIdioma.lowercase(Locale.ROOT)
    if (lower.contains("latino") || lower.contains("latina")) return "Doblaje Latino"
    if (lower.contains("españa") || lower.contains("castellano") || lower.contains("iberico")) return "Doblaje Español de España"
    if (trimmed.isNotBlank()) return "Doblaje $trimmed"
    return "Doblaje Latino"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogItemCard(
    item: CatalogItem,
    isFavorite: Boolean,
    categoryKey: String,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    val showAccessibilityHints = remember(context) {
        SharedPreferencesManager(context).getBoolean("enable_accessibility_hints", true)
    }

    val titleText = remember(item.title) {
        if (item.title.trim().endsWith(".")) item.title.trim() else "${item.title.trim()}."
    }

    val yearText = remember(item.anio) {
        if (item.anio.isNotBlank()) "Año ${item.anio.trim()}." else ""
    }

    val dubbingText = remember(item.idioma) {
        "${getDubbingText(item.idioma)}."
    }

    val accessibilityDescription = remember(item.title, item.anio, item.idioma, isFavorite, showAccessibilityHints) {
        val cleanTitle = if (item.title.trim().endsWith(".")) item.title.trim() else "${item.title.trim()}."
        val cleanYear = if (item.anio.isNotBlank()) "Año ${item.anio.trim()}." else ""
        val cleanDubbing = "${getDubbingText(item.idioma)}."
        val favStatus = if (isFavorite) "Guardada en favoritos." else ""
        val mainText = listOf(cleanTitle, cleanYear, cleanDubbing, favStatus)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        buildInteractionDescription(
            mainContent = mainText,
            hintText = "Mantén presionado para opciones",
            showHint = showAccessibilityHints
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsClick
            )
            .clearAndSetSemantics {
                contentDescription = accessibilityDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (yearText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = yearText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = dubbingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { onItemSelected(option) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
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
