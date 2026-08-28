package com.johang.audiocinemateca.presentation.contentdetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.domain.DownloadRequest
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.util.TimeFormatUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

@AndroidEntryPoint
class ContentDetailFragment : Fragment() {

    private val viewModel: ContentDetailViewModel by viewModels()
    private val args: ContentDetailFragmentArgs by navArgs()

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    private var favoriteMenuItem: MenuItem? = null

    private val toolbarTitleState = androidx.compose.runtime.mutableStateOf("Detalles del contenido")

    // Views
    private lateinit var contentTitleHeader: TextView
    private lateinit var contentRatingText: TextView
    private lateinit var listenNowButton: Button
    private lateinit var downloadContainer: LinearLayout
    private lateinit var downloadButton: com.google.android.material.button.MaterialButton
    private lateinit var downloadProgressBar: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var contentViewCount: TextView
    private lateinit var contentYear: TextView
    private lateinit var contentGenre: TextView
    private lateinit var contentCountry: TextView
    private lateinit var contentDirector: TextView
    private lateinit var contentScreenwriter: TextView
    private lateinit var contentMusic: TextView
    private lateinit var contentPhotography: TextView
    private lateinit var contentCast: TextView
    private lateinit var contentProducer: TextView
    private lateinit var contentNarration: TextView
    private lateinit var contentDuration: TextView
    private lateinit var contentLanguage: TextView
    private lateinit var contentFilmaffinity: TextView
    private lateinit var contentSinopsis: TextView
    private lateinit var moviePartsContainer: LinearLayout
    private lateinit var moviePartsListContainer: LinearLayout
    private lateinit var seriesChaptersContainer: LinearLayout
    private lateinit var seasonSpinner: Spinner
    private lateinit var btnDownloadSeason: com.google.android.material.button.MaterialButton
    private lateinit var episodesListContainer: LinearLayout

    private val progressUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateProgressDisplay()
            progressUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val playbackUpdateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA) {
                val itemId = intent.getStringExtra(MainActivity.EXTRA_ITEM_ID)
                if (itemId == args.itemId) {
                    val seasonIndex = intent.getIntExtra(MainActivity.EXTRA_PART_INDEX, -1)
                    if (seasonIndex >= 0 && seasonSpinner.selectedItemPosition != seasonIndex) {
                        Log.d("ContentDetail", "Sincronizando spinner con reproducción: Temporada $seasonIndex")
                        seasonSpinner.setSelection(seasonIndex)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_content_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        val composeToolbar = view.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_detail_toolbar)
        composeToolbar.setContent {
            com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme {
                val title by toolbarTitleState
                val isFavorite by viewModel.isFavorite.collectAsState()
                com.johang.audiocinemateca.presentation.catalog.DetailToolbar(
                    title = title,
                    isFavorite = isFavorite,
                    onBackClick = { findNavController().navigateUp() },
                    onFavoriteClick = { viewModel.toggleFavorite() },
                    onShareClick = { shareContent() }
                )
            }
        }

        initializeViews(view)
        viewModel.loadContentDetail(args.itemId, args.itemType)
        observeViewModel()
        observeDownloadStates()
        observeViewActions()

        downloadButton.setOnClickListener {
            viewModel.onDownloadAction()
        }
        
        contentRatingText.setOnClickListener {
            showRatingDialog()
        }

        // Registrar receptor para sincronización en tiempo real
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            playbackUpdateReceiver, 
            android.content.IntentFilter(MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA)
        )
    }

    private fun initializeViews(view: View) {
        contentTitleHeader = view.findViewById(R.id.content_title_header)
        contentRatingText = view.findViewById(R.id.content_rating_text)
        listenNowButton = view.findViewById(R.id.listen_now_button)
        downloadContainer = view.findViewById(R.id.download_container)
        downloadButton = view.findViewById(R.id.download_button)
        downloadProgressBar = view.findViewById(R.id.download_progress_bar)
        contentViewCount = view.findViewById(R.id.content_view_count)
        contentYear = view.findViewById(R.id.content_year)
        contentGenre = view.findViewById(R.id.content_genre)
        contentCountry = view.findViewById(R.id.content_country)
        contentDirector = view.findViewById(R.id.content_director)
        contentScreenwriter = view.findViewById(R.id.content_screenwriter)
        contentMusic = view.findViewById(R.id.content_music)
        contentPhotography = view.findViewById(R.id.content_photography)
        contentCast = view.findViewById(R.id.content_cast)
        contentProducer = view.findViewById(R.id.content_producer)
        contentNarration = view.findViewById(R.id.content_narration)
        contentDuration = view.findViewById(R.id.content_duration)
        contentLanguage = view.findViewById(R.id.content_language)
        contentFilmaffinity = view.findViewById(R.id.content_filmaffinity)
        contentSinopsis = view.findViewById(R.id.content_sinopsis)
        moviePartsContainer = view.findViewById(R.id.movie_parts_container)
        moviePartsListContainer = view.findViewById(R.id.movie_parts_list_container)
        seriesChaptersContainer = view.findViewById(R.id.series_chapters_container)
        seasonSpinner = view.findViewById(R.id.season_spinner)
        btnDownloadSeason = view.findViewById(R.id.btn_download_season)
        episodesListContainer = view.findViewById(R.id.episodes_list_container)
    }

    private fun observeViewModel() {
        // Observar Estadísticas de Calificación
        lifecycleScope.launch {
            if (!viewModel.isUserLoggedIn) {
                contentRatingText.visibility = View.GONE
            } else {
                contentRatingText.visibility = View.VISIBLE
                viewModel.ratingStats.collect {
                    val avg = String.format("%.1f", it.averageRating)
                    val total = it.totalRatings
                    val myRating = it.userRating
                    
                    val text = StringBuilder()
                    text.append("$avg/5 estrellas ($total votos)")
                    
                    if (myRating > 0) {
                        text.append("\nTu calificación: $myRating estrellas")
                    } else {
                        text.append("\nToca para calificar")
                    }
                    
                    contentRatingText.text = text.toString()
                    contentRatingText.contentDescription = text.toString().replace("/", " de ")
                }
            }
        }

        // Observar Contador de Vistas
        lifecycleScope.launch {
            viewModel.viewCountText.collect {
                if (it.isNotEmpty()) {
                    contentViewCount.text = it
                    contentViewCount.visibility = View.VISIBLE
                    contentViewCount.contentDescription = it
                } else {
                    contentViewCount.visibility = View.GONE
                }
            }
        }

        // Observar Item de Catálogo y Carga
        lifecycleScope.launch {
            viewModel.contentItem.combine(viewModel.isLoading) { contentItem, isLoading ->
                Pair(contentItem, isLoading)
            }.collect { (contentItem, isLoading) ->
                if (!isLoading && contentItem == null) {
                    showContentNotFoundDialog()
                    view?.findViewById<LinearLayout>(R.id.content_details_container)?.visibility = View.GONE
                    listenNowButton.visibility = View.GONE
                } else if (contentItem != null) {
                    updateStaticUI(contentItem)
                    setupContentSpecificUI(contentItem)
                    updateProgressDisplay()
                }
            }
        }
    }

    private fun observeViewActions() {
        lifecycleScope.launch {
            viewModel.viewActions.collect {
                it.getContentIfNotHandled()?.let { action ->
                    when (action) {
                        is ViewAction.StartDownload -> {
                            val request = DownloadRequest(
                                url = action.url,
                                title = action.title,
                                contentId = action.contentId,
                                contentType = action.contentType,
                                partIndex = action.partIndex,
                                episodeIndex = action.episodeIndex,
                                seriesTitle = action.seriesTitle
                            )
                            downloadManager.enqueueDownload(request)
                            Toast.makeText(requireContext(), "Añadido a la cola de descargas.", Toast.LENGTH_SHORT).show()
                        }
                        is ViewAction.ShowCancelConfirmation -> showCancelConfirmationDialog(action.partIndex, action.episodeIndex)
                        is ViewAction.ShowDeleteConfirmation -> showDeleteConfirmationDialog(action.partIndex, action.episodeIndex)
                        is ViewAction.ShowDeleteSeasonConfirmation -> showDeleteSeasonConfirmationDialog(action.seasonIndex, action.seasonName)
                        is ViewAction.ShowCancelSeasonConfirmation -> showCancelSeasonConfirmationDialog(action.seasonIndex, action.seasonName)
                        is ViewAction.ShowDownloadFailed -> showDownloadFailedDialog(action.reason)
                        is ViewAction.ShowError -> showErrorDialog(action.message)
                        is ViewAction.ShowMessage -> requireView().announceForAccessibility(action.message)
                        is ViewAction.ShowSeasonDownloadStarted -> {
                            Toast.makeText(requireContext(), "Iniciando descarga de ${action.count} episodios de la Temporada ${action.seasonNumber}...", Toast.LENGTH_LONG).show()
                            requireView().announceForAccessibility("Iniciando descarga de ${action.count} episodios de la temporada ${action.seasonNumber}")
                        }
                        is ViewAction.ShowSeasonAlreadyDownloaded -> {
                            Toast.makeText(requireContext(), "Todos los episodios de la Temporada ${action.seasonNumber} ya están descargados.", Toast.LENGTH_SHORT).show()
                            requireView().announceForAccessibility("Todos los episodios de la temporada ${action.seasonNumber} ya están descargados")
                        }
                        is ViewAction.StopDownloadService -> {
                            val intent = Intent(requireContext(), com.johang.audiocinemateca.presentation.download.DownloadService::class.java)
                            requireContext().stopService(intent)
                        }
                        is ViewAction.UpdateFavoriteIcon -> updateFavoriteIcon(action.isFavorite)
                    }
                }
            }
        }
    }

    private fun observeDownloadStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.contentItem,
                viewModel.downloadState,
                viewModel.episodeDownloadStates,
                viewModel.targetedEpisodeIndices,
                viewModel.downloadProgress
            ) { item, singleState, episodeStates, targetIndices, progress ->
                object { val item = item
                    val singleState = singleState
                    val episodeStates = episodeStates
                    val targetIndices = targetIndices
                    val progress = progress
                }
            }.collect { data ->
                val item = data.item ?: return@collect
                val singleState = data.singleState
                val episodeStates = data.episodeStates
                val targetIndices = data.targetIndices
                val progress = data.progress

                downloadContainer.visibility = View.VISIBLE
                downloadButton.isEnabled = true

                if (item is Serie && targetIndices != null) {
                    val (seasonIndex, episodeIndex) = targetIndices
                    val seasonKey = item.capitulos.keys.sorted().getOrNull(seasonIndex)
                    val episode = item.capitulos[seasonKey]?.getOrNull(episodeIndex)
                    val episodeNumber = episode?.capitulo ?: (episodeIndex + 1).toString()
                    val baseText = "T${seasonIndex + 1}:E$episodeNumber"

                    when (singleState) {
                        is DownloadState.Downloading -> {
                            downloadButton.text = "Descargando $baseText"
                            downloadButton.icon = null
                            downloadProgressBar.visibility = View.VISIBLE
                            downloadProgressBar.progress = progress
                        }
                        is DownloadState.Downloaded -> {
                            downloadButton.text = "Eliminar descarga $baseText"
                            downloadButton.setIconResource(R.drawable.ic_close)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                        is DownloadState.Failed -> {
                            downloadButton.text = "Reintentar $baseText"
                            downloadButton.setIconResource(R.drawable.ic_downloads)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                        else -> {
                            downloadButton.text = "Descargar $baseText"
                            downloadButton.setIconResource(R.drawable.ic_downloads)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                    }
                } else if (item !is Serie) {
                    when (singleState) {
                        is DownloadState.Downloading -> {
                            downloadButton.text = "Descargando..."
                            downloadButton.icon = null
                            downloadProgressBar.visibility = View.VISIBLE
                            downloadProgressBar.progress = progress
                        }
                        is DownloadState.Downloaded -> {
                            downloadButton.text = "Descargado"
                            downloadButton.setIconResource(R.drawable.ic_close)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                        is DownloadState.Failed -> {
                            downloadButton.text = "Reintentar"
                            downloadButton.setIconResource(R.drawable.ic_downloads)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                        else -> {
                            downloadButton.text = "Descargar"
                            downloadButton.setIconResource(R.drawable.ic_downloads)
                            downloadProgressBar.visibility = View.INVISIBLE
                        }
                    }
                }

                // Update Episode List Items if necessary
                if (item is Serie) {
                    val selectedSeasonIndex = seasonSpinner.selectedItemPosition
                    if (selectedSeasonIndex >= 0) {
                        val seasons = item.capitulos.keys.sorted()
                        val selectedSeasonKey = seasons[selectedSeasonIndex]
                        item.capitulos[selectedSeasonKey]?.forEachIndexed { epIndex, episode ->
                            val epView = episodesListContainer.findViewWithTag<View?>("episode_view_${selectedSeasonIndex}_$epIndex")
                            epView?.let {
                                val dButton: com.google.android.material.button.MaterialButton = it.findViewById(R.id.episode_download_button)
                                val pBar: com.google.android.material.progressindicator.LinearProgressIndicator = it.findViewById(R.id.episode_download_progress_bar)
                                val state = episodeStates["${selectedSeasonIndex}_$epIndex"]
                                when (state) {
                                    is DownloadState.Downloading -> {
                                        dButton.text = "Cancelar"
                                        dButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
                                        pBar.visibility = View.VISIBLE
                                        pBar.progress = progress
                                    }
                                    is DownloadState.Downloaded -> {
                                        dButton.text = "Eliminar"
                                        dButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
                                        pBar.visibility = View.GONE
                                    }
                                    else -> {
                                        dButton.text = "Descargar"
                                        dButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_downloads)
                                        pBar.visibility = View.GONE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateStaticUI(item: CatalogItem) {
        val actionBarTitle = when (item) {
            is Movie -> "Detalles de la película"
            is Serie -> "Detalles de la serie"
            is Documentary -> "Detalles del documental"
            is ShortFilm -> "Detalles del cortometraje"
            else -> "Detalles del contenido"
        }
        toolbarTitleState.value = actionBarTitle
        
        contentYear.text = "Año: ${item.anio}"
        contentGenre.text = "Género: ${item.genero}"
        contentCountry.text = "País: ${item.pais}"
        contentDirector.text = "Director: ${item.director}"
        contentScreenwriter.text = "Escrito por: ${item.guion}"
        contentTitleHeader.text = item.title
        contentMusic.text = "Música: ${item.musica}"
        contentPhotography.text = "Fotografía: ${item.fotografia}"
        contentCast.text = "Reparto: ${item.reparto}"
        contentProducer.text = "Productora: ${item.productora}"
        contentNarration.text = "Narración: ${item.narracion}"
        val durationInMinutes = item.duracion.toIntOrNull() ?: 0
        contentDuration.text = "Duración: ${TimeFormatUtils.formatDuration(durationInMinutes)}"
        contentLanguage.text = "Idioma: " + when (item.idioma) {
            "1" -> "Español de España"
            "2" -> "Español Latino"
            else -> item.idioma
        }
        contentFilmaffinity.apply {
            text = "Ver la ficha en FilmAffinity"
            setOnClickListener { openFilmaffinity(item) }
        }
        contentSinopsis.text = item.sinopsis
    }

    private fun setupContentSpecificUI(item: CatalogItem) {
        when (item) {
            is Movie -> setupMovieUI(item)
            is Serie -> setupSeriesUI(item)
            else -> {
                moviePartsContainer.visibility = View.GONE
                seriesChaptersContainer.visibility = View.GONE
            }
        }
    }

    private fun setupMovieUI(movie: Movie) {
        seriesChaptersContainer.visibility = View.GONE
        if (movie.enlaces.size > 1) {
            moviePartsContainer.visibility = View.VISIBLE
            moviePartsListContainer.removeAllViews()
            movie.enlaces.forEachIndexed { index, _ ->
                val partTextView = createClickableTextView("Parte ${index + 1}: Reproducir ahora", "part_$index") {
                    val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(movie, index, -1)
                    findNavController().navigate(action)
                }
                moviePartsListContainer.addView(partTextView)
            }
        } else {
            moviePartsContainer.visibility = View.GONE
        }
    }

    private fun setupSeriesUI(serie: Serie) {
        moviePartsContainer.visibility = View.GONE
        seriesChaptersContainer.visibility = View.VISIBLE
        val seasons = serie.capitulos.keys.sorted()
        val seasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, seasons.map { "Temporada $it" })
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        seasonSpinner.adapter = seasonAdapter

        btnDownloadSeason.setOnClickListener {
            val selectedSeason = seasonSpinner.selectedItemPosition
            if (selectedSeason >= 0) {
                viewModel.onSeasonDownloadAction(selectedSeason)
            }
        }

        // Observar estado dinámico de descarga de la temporada seleccionada
        lifecycleScope.launch {
            viewModel.seasonDownloadState.collect { state ->
                val currentPosition = seasonSpinner.selectedItemPosition.coerceAtLeast(0)
                val seasonName = seasons.getOrNull(currentPosition) ?: (currentPosition + 1).toString()

                when (state) {
                    is SeasonDownloadState.Downloaded -> {
                        btnDownloadSeason.text = "Eliminar T$seasonName"
                        btnDownloadSeason.setIconResource(R.drawable.ic_close)
                        btnDownloadSeason.contentDescription = "Eliminar todos los capítulos descargados de la temporada $seasonName"
                    }
                    is SeasonDownloadState.Downloading -> {
                        btnDownloadSeason.text = "Descargando T$seasonName..."
                        btnDownloadSeason.setIconResource(R.drawable.ic_downloads)
                        btnDownloadSeason.contentDescription = "Descargando temporada $seasonName. Toca para detener las descargas"
                    }
                    is SeasonDownloadState.NotDownloaded -> {
                        btnDownloadSeason.text = "Descargar T$seasonName"
                        btnDownloadSeason.setIconResource(R.drawable.ic_downloads)
                        btnDownloadSeason.contentDescription = "Descargar todos los episodios de la temporada $seasonName"
                    }
                }
            }
        }

        // SELECCIÓN INTELIGENTE AL ENTRAR
        lifecycleScope.launch {
            val allProgress = playbackProgressRepository.getPlaybackProgressForContent(serie.id)
            val latest = allProgress.maxByOrNull { it.lastPlayedTimestamp }
            
            val initialSeasonIndex = if (latest != null && latest.partIndex >= 0 && latest.partIndex < seasons.size) {
                latest.partIndex
            } else 0

            seasonSpinner.setSelection(initialSeasonIndex)
            viewModel.setSelectedSeasonIndex(initialSeasonIndex)
            updateEpisodeList(serie, seasons[initialSeasonIndex], initialSeasonIndex)
        }

        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setSelectedSeasonIndex(position)
                updateEpisodeList(serie, seasons[position], position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateEpisodeList(serie: Serie, seasonKey: String, seasonIndex: Int) {
        episodesListContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        serie.capitulos[seasonKey]?.forEachIndexed { episodeIndex, episode ->
            val episodeView = inflater.inflate(R.layout.list_item_episode, episodesListContainer, false)
            episodeView.tag = "episode_view_${seasonIndex}_$episodeIndex"
            val titleText: TextView = episodeView.findViewById(R.id.episode_title_text)
            val dButton: View = episodeView.findViewById(R.id.episode_download_button)
            titleText.text = "Episodio ${episode.capitulo}: ${episode.titulo}"
            titleText.setOnClickListener {
                val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(serie, seasonIndex, episodeIndex)
                findNavController().navigate(action)
            }
            dButton.setOnClickListener { viewModel.onEpisodeDownloadAction(seasonIndex, episodeIndex) }
            episodesListContainer.addView(episodeView)
        }
        updateProgressDisplay()
    }

    private fun updateProgressDisplay() {
        val item = viewModel.contentItem.value ?: return
        lifecycleScope.launch {
            val allProgress = playbackProgressRepository.getPlaybackProgressForContent(item.id)
            if (item is Serie) viewModel.updateTargetedEpisodeFromList(allProgress)
            updateListenNowButtonProgress(item, allProgress)
            when (item) {
                is Movie -> updateMoviePartsProgress(allProgress)
                is Serie -> updateEpisodesProgress(item, allProgress)
            }
        }
    }

    private fun updateListenNowButtonProgress(item: CatalogItem, allProgress: List<PlaybackProgressEntity>) {
        val latest = allProgress.maxByOrNull { it.lastPlayedTimestamp }
        val isFinished = latest?.isFinished == true || (latest != null && latest.totalDurationMs > 0 && latest.currentPositionMs >= latest.totalDurationMs - 15000)

        if (latest != null && !isFinished && latest.currentPositionMs < latest.totalDurationMs) {
            val remaining = TimeFormatUtils.formatDuration(latest.totalDurationMs - latest.currentPositionMs)
            val text = if (item is Serie) {
                val seasonKey = item.capitulos.keys.elementAtOrNull(latest.partIndex)
                val episode = item.capitulos[seasonKey]?.getOrNull(latest.episodeIndex)
                episode?.let { "Continuar T${latest.partIndex + 1}:E${it.capitulo} - ${it.titulo} ($remaining restantes)" } ?: "Continuar..."
            } else "Continuar escuchando ($remaining restantes)"
            listenNowButton.text = text
            listenNowButton.setOnClickListener {
                findNavController().navigate(ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, latest.partIndex, latest.episodeIndex))
            }
        } else if (latest != null && isFinished && item is Serie) {
            // Si el último capítulo reproducido se terminó, ofrecer inteligentemente el siguiente episodio de la serie
            val seasonIndex = latest.partIndex.coerceAtLeast(0)
            val episodeIndex = latest.episodeIndex.coerceAtLeast(0)
            val seasons = item.capitulos.keys.sorted()
            val currentSeasonKey = seasons.getOrNull(seasonIndex)
            val currentSeasonEpisodes = item.capitulos[currentSeasonKey] ?: emptyList()

            val (nextSeason, nextEpisode, nextEpObj) = if (episodeIndex + 1 < currentSeasonEpisodes.size) {
                Triple(seasonIndex, episodeIndex + 1, currentSeasonEpisodes[episodeIndex + 1])
            } else if (seasonIndex + 1 < seasons.size) {
                val nextSeasonKey = seasons[seasonIndex + 1]
                val nextSeasonEpisodes = item.capitulos[nextSeasonKey] ?: emptyList()
                if (nextSeasonEpisodes.isNotEmpty()) {
                    Triple(seasonIndex + 1, 0, nextSeasonEpisodes[0])
                } else Triple(-1, -1, null)
            } else {
                Triple(-1, -1, null)
            }

            if (nextEpObj != null) {
                listenNowButton.text = "Siguiente: T${nextSeason + 1}:E${nextEpObj.capitulo} - ${nextEpObj.titulo}"
                listenNowButton.setOnClickListener {
                    findNavController().navigate(ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, nextSeason, nextEpisode))
                }
            } else {
                listenNowButton.text = "Comenzar de nuevo"
                listenNowButton.setOnClickListener {
                    findNavController().navigate(ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, 0, 0))
                }
            }
        } else {
            listenNowButton.text = "Comenzar a oír"
            listenNowButton.setOnClickListener {
                val action = if (item is Serie) ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, 0, 0)
                else ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item)
                findNavController().navigate(action)
            }
        }
    }

    private fun updateMoviePartsProgress(allProgress: List<PlaybackProgressEntity>) {
        for (i in 0 until moviePartsListContainer.childCount) {
            val partView = moviePartsListContainer.findViewWithTag<TextView?>("part_$i")
            partView?.let {
                val progress = allProgress.find { p -> p.partIndex == i }
                it.text = if (progress != null && progress.currentPositionMs < progress.totalDurationMs) {
                    "Parte ${i + 1}: Continuar (${TimeFormatUtils.formatDuration(progress.totalDurationMs - progress.currentPositionMs)} restantes)"
                } else if (progress != null) "Parte ${i + 1}: Completado"
                else "Parte ${i + 1}: Reproducir ahora"
            }
        }
    }

    private fun updateEpisodesProgress(serie: Serie, allProgress: List<PlaybackProgressEntity>) {
        if (seasonSpinner.adapter == null || seasonSpinner.selectedItemPosition < 0) return
        val seasons = serie.capitulos.keys.sorted()
        val selectedSeasonIndex = seasonSpinner.selectedItemPosition
        val selectedSeasonKey = seasons[selectedSeasonIndex]
        serie.capitulos[selectedSeasonKey]?.forEachIndexed { episodeIndex, episode ->
            val epView = episodesListContainer.findViewWithTag<View?>("episode_view_${selectedSeasonIndex}_$episodeIndex")
            epView?.let {
                val titleText: TextView = it.findViewById(R.id.episode_title_text)
                val progress = allProgress.find { p -> p.partIndex == selectedSeasonIndex && p.episodeIndex == episodeIndex }
                val base = "Episodio ${episode.capitulo}: ${episode.titulo}"
                val extra = if (progress != null && progress.currentPositionMs < progress.totalDurationMs) {
                    " (Continuar: ${TimeFormatUtils.formatDuration(progress.totalDurationMs - progress.currentPositionMs)} restantes)"
                } else if (progress != null) " (Completado)" else ""
                titleText.text = "$base$extra"
            }
        }
    }

    private fun showRatingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rating, null)
        val ratingBar = dialogView.findViewById<android.widget.RatingBar>(R.id.rating_bar)
        val currentRating = viewModel.ratingStats.value.userRating
        if (currentRating > 0) ratingBar.rating = currentRating.toFloat()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Calificar Contenido")
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Calificar ahora") { _, _ ->
                val rating = ratingBar.rating.toInt()
                if (rating > 0) viewModel.submitRating(rating)
                else Toast.makeText(requireContext(), "Selecciona al menos 1 estrella", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.content_detail_menu, menu)
        favoriteMenuItem = menu.findItem(R.id.action_favorite)
        updateFavoriteIcon(viewModel.isFavorite.value)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { findNavController().navigateUp(); true }
            R.id.action_share -> { shareContent(); true }
            R.id.action_favorite -> { viewModel.toggleFavorite(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        val iconRes = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        favoriteMenuItem?.setIcon(ContextCompat.getDrawable(requireContext(), iconRes))
        favoriteMenuItem?.title = if (isFavorite) "Eliminar de favoritos" else "Añadir a favoritos"
    }

    private fun shareContent() {
        val item = viewModel.contentItem.value ?: return
        val type = when (item) {
            is Movie -> "película"
            is Serie -> "serie"
            is Documentary -> "documental"
            is ShortFilm -> "cortometraje"
            else -> "contenido"
        }
        val article = when (type) {
            "película", "serie" -> "esta increíble $type"
            else -> "este increíble $type"
        }
        val callWord = when (type) {
            "película", "serie" -> "llamada"
            else -> "llamado"
        }
        val message = "¡Oye! Estoy escuchando $article $callWord '${item.title}' en la Audiocinemateca. ¡Seguro que a ti también te podría gustar! Da clic en este enlace para que lo escuches en la app."
        val typeSlug = when (item) { is Movie -> "pelicula"; is Serie -> "serie"; is Documentary -> "documental"; is ShortFilm -> "cortometraje"; else -> "contenido" }
        val url = "https://audiocinemateca.com/$typeSlug?id=${item.id}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$message\n\n$url")
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir contenido"))
    }

    private fun createClickableTextView(text: String, tag: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            this.tag = tag
            textSize = 16f
            setPadding(0, 8, 0, 8)
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }
    }

    private fun openFilmaffinity(item: CatalogItem) {
        val url = when (item) {
            is Movie -> item.filmaffinity
            is Serie -> item.filmaffinity
            is Documentary -> item.filmaffinity
            is ShortFilm -> item.filmaffinity
            else -> null
        } ?: return
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(partIndex: Int, episodeIndex: Int) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar descarga")
            .setMessage("¿Estás seguro?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteDownload(partIndex, episodeIndex) }
            .show()
    }

    private fun showCancelConfirmationDialog(partIndex: Int, episodeIndex: Int) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancelar descarga")
            .setMessage("¿Estás seguro?")
            .setNegativeButton("No", null)
            .setPositiveButton("Sí, cancelar") { _, _ -> viewModel.cancelDownload(partIndex, episodeIndex) }
            .show()
    }

    private fun showDeleteSeasonConfirmationDialog(seasonIndex: Int, seasonName: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar temporada $seasonName")
            .setMessage("¿Deseas eliminar todos los capítulos descargados de la Temporada $seasonName?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { dialog, _ ->
                viewModel.deleteSeasonDownloads(seasonIndex)
                dialog.dismiss()
            }
            .show()
    }

    private fun showCancelSeasonConfirmationDialog(seasonIndex: Int, seasonName: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancelar descargas de temporada $seasonName")
            .setMessage("¿Deseas detener y cancelar las descargas en curso de la Temporada $seasonName?")
            .setNegativeButton("Continuar descargando", null)
            .setPositiveButton("Detener descargas") { dialog, _ ->
                viewModel.cancelSeasonDownloads(seasonIndex)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDownloadFailedDialog(reason: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error en la descarga")
            .setMessage(reason)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun showContentNotFoundDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Contenido no encontrado")
            .setMessage("El contenido no está disponible.")
            .setPositiveButton("Aceptar") { d, _ -> d.dismiss(); findNavController().navigateUp() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        progressUpdateHandler.post(progressUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playbackUpdateReceiver)
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        (activity as? AppCompatActivity)?.supportActionBar?.subtitle = null
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
}
