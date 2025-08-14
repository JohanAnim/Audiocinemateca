package com.johang.audiocinemateca.presentation.contentdetail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import javax.inject.Inject


@AndroidEntryPoint
class ContentDetailFragment : Fragment() {

    private val viewModel: ContentDetailViewModel by viewModels()
    private val args: ContentDetailFragmentArgs by navArgs()

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    // Views
    private lateinit var contentTitleHeader: TextView
    private lateinit var listenNowButton: Button
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
    private lateinit var episodesListContainer: LinearLayout

    private val progressUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            updateProgressDisplay()
            progressUpdateHandler.postDelayed(this, 1000) // Actualizar cada 1 segundo
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

        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initializeViews(view)
        viewModel.loadContentDetail(args.itemId, args.itemType)
        observeViewModel()
    }

    private fun initializeViews(view: View) {
        contentTitleHeader = view.findViewById(R.id.content_title_header)
        listenNowButton = view.findViewById(R.id.listen_now_button)
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
        episodesListContainer = view.findViewById(R.id.episodes_list_container)
    }

    

    private fun observeViewModel() {
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
                    updateProgressDisplay() // Initial progress update
                }
            }
        }
    }

    private fun updateStaticUI(item: CatalogItem) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = item.title
        (activity as? AppCompatActivity)?.supportActionBar?.subtitle = when (args.itemType) {
            "peliculas" -> "Detalles de la película"
            "series" -> "Detalles de la serie"
            "cortometrajes" -> "Detalles del cortometraje"
            "documentales" -> "Detalles del documental"
            else -> "Detalles del contenido"
        }
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
        contentDuration.text = "Duración: ${item.duracion} minutos"
        contentLanguage.text = "Idioma: " + when (item.idioma) {
            "1" -> "Español de España"
            "2" -> "Español Latino"
            else -> item.idioma
        }
        contentFilmaffinity.apply {
            text = "Ver la ficha en FilmAffinity"
            setOnClickListener {
                openFilmaffinity(item)
            }
        }
        contentSinopsis.text = item.sinopsis
    }

    private fun setupContentSpecificUI(item: CatalogItem) {
        when (item) {
            is Movie -> setupMovieUI(item)
            is Serie -> setupSeriesUI(item)
            // Documentaries and ShortFilms might not have specific UI elements like parts or episodes
            is Documentary -> { /* Hide or handle UI for documentaries */ }
            is ShortFilm -> { /* Hide or handle UI for short films */ }
        }
    }

    private fun setupMovieUI(movie: Movie) {
        if (movie.enlaces.size > 1) {
            moviePartsContainer.visibility = View.VISIBLE
            moviePartsListContainer.removeAllViews() // Clear previous views
            movie.enlaces.forEachIndexed { index, _ ->
                val partTextView = createClickableTextView("Parte ${index + 1}: Reproducir ahora", "part_$index") {
                    Log.d("ContentDetailFragment", "Reproduciendo parte de película: movie=$movie, partIndex=$index")
                    val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(
                        movie,
                        index,
                        -1 // -1 to indicate it's not a series episode
                    )
                    findNavController().navigate(action)
                }
                moviePartsListContainer.addView(partTextView)
            }
        } else {
            moviePartsContainer.visibility = View.GONE
        }
    }

    private fun setupSeriesUI(serie: Serie) {
        seriesChaptersContainer.visibility = View.VISIBLE
        val seasons = serie.capitulos.keys.sorted()
        val seasonAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, seasons.map { "Temporada $it" })
        seasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        seasonSpinner.adapter = seasonAdapter

        // Pre-seleccionar la temporada guardada
        lifecycleScope.launch {
            val latestProgress = playbackProgressRepository.getPlaybackProgressForContent(serie.id).maxByOrNull { it.lastPlayedTimestamp }
            if (latestProgress != null && latestProgress.partIndex < seasons.size) {
                seasonSpinner.setSelection(latestProgress.partIndex)
            }
        }

        seasonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSeasonKey = seasons[position]
                updateEpisodeList(serie, selectedSeasonKey, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Load episodes for the first season initially
        if (seasons.isNotEmpty()) {
            updateEpisodeList(serie, seasons[0], 0)
        }
    }

    private fun updateEpisodeList(serie: Serie, seasonKey: String, seasonIndex: Int) {
        episodesListContainer.removeAllViews()
        serie.capitulos[seasonKey]?.forEachIndexed { episodeIndex, episode ->
            val episodeTextView = createClickableTextView(
                "Episodio ${episode.capitulo}: ${episode.titulo} - Reproducir ahora",
                "episode_${seasonIndex}_${episodeIndex}"
            ) {
                val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(serie, seasonIndex, episodeIndex)
                findNavController().navigate(action)
            }
            episodesListContainer.addView(episodeTextView)
        }
        updateProgressDisplay() // Refresh progress display for the new list of episodes
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
        Toast.makeText(requireContext(), "Abriendo en el navegador...", Toast.LENGTH_SHORT).show()
        val filmaffinityUrl = when (item) {
            is Movie -> item.filmaffinity
            is Serie -> item.filmaffinity
            is Documentary -> item.filmaffinity
            is ShortFilm -> item.filmaffinity
            else -> null
        }
        filmaffinityUrl?.let { url ->
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "No se encontró una aplicación para abrir el enlace.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al abrir el enlace: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: Toast.makeText(requireContext(), "URL de FilmAffinity no disponible.", Toast.LENGTH_SHORT).show()
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
        // Limpiar el título, el subtítulo y ocultar el botón de volver cuando la vista del fragmento se destruye
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        (activity as? AppCompatActivity)?.supportActionBar?.subtitle = null
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun updateProgressDisplay() {
        val item = viewModel.contentItem.value ?: return
        lifecycleScope.launch {
            val allProgressForContent = playbackProgressRepository.getPlaybackProgressForContent(item.id)
            updateListenNowButtonProgress(item, allProgressForContent)

            when (item) {
                is Movie -> updateMoviePartsProgress(allProgressForContent)
                is Serie -> updateEpisodesProgress(item, allProgressForContent)
            }
        }
    }

    private fun updateListenNowButtonProgress(item: CatalogItem, allProgress: List<PlaybackProgressEntity>) {
        val latestProgress = allProgress.maxByOrNull { it.lastPlayedTimestamp }

        if (latestProgress != null && latestProgress.currentPositionMs < latestProgress.totalDurationMs) {
            val remainingMs = latestProgress.totalDurationMs - latestProgress.currentPositionMs
            val remainingMinutes = remainingMs / (1000 * 60)
            val remainingSeconds = (remainingMs / 1000) % 60

            val progressText = when (item) {
                is Serie -> {
                    val seasonKey = item.capitulos.keys.elementAtOrNull(latestProgress.partIndex)
                    val episode = item.capitulos[seasonKey]?.getOrNull(latestProgress.episodeIndex)
                    episode?.let { "Continuar T${latestProgress.partIndex + 1}:E${it.capitulo} - ${it.titulo} (${remainingMinutes}m ${remainingSeconds}s restantes)" }
                        ?: "Continuar escuchando..."
                }
                else -> "Continuar escuchando (${remainingMinutes}m ${remainingSeconds}s restantes)"
            }
            listenNowButton.text = progressText
            listenNowButton.setOnClickListener {
                val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(
                    item,
                    latestProgress.partIndex,
                    latestProgress.episodeIndex
                )
                findNavController().navigate(action)
            }
        } else {
            listenNowButton.text = "Comenzar a oír"
            listenNowButton.setOnClickListener {
                val action = if (item is Serie) {
                    ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, 0, 0)
                } else {
                    ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item)
                }
                findNavController().navigate(action)
            }
        }
    }

    private fun updateMoviePartsProgress(allProgress: List<PlaybackProgressEntity>) {
        for (i in 0 until moviePartsListContainer.childCount) {
            val partView = moviePartsListContainer.findViewWithTag<TextView?>("part_$i")
            partView?.let {
                val progress = allProgress.find { it.partIndex == i }
                it.text = if (progress != null && progress.currentPositionMs < progress.totalDurationMs) {
                    val remainingMs = progress.totalDurationMs - progress.currentPositionMs
                    val remainingMinutes = remainingMs / (1000 * 60)
                    val remainingSeconds = (remainingMs / 1000) % 60
                    "Parte ${i + 1}: Continuar (${remainingMinutes}m ${remainingSeconds}s restantes)"
                } else if (progress != null) {
                    "Parte ${i + 1}: Completado"
                } else {
                    "Parte ${i + 1}: Reproducir ahora"
                }
            }
        }
    }

    private fun updateEpisodesProgress(serie: Serie, allProgress: List<PlaybackProgressEntity>) {
        if (seasonSpinner.adapter == null || seasonSpinner.selectedItemPosition < 0) return

        val seasons = serie.capitulos.keys.sorted()
        val selectedSeasonIndex = seasonSpinner.selectedItemPosition
        val selectedSeasonKey = seasons[selectedSeasonIndex]

        serie.capitulos[selectedSeasonKey]?.forEachIndexed { episodeIndex, episode ->
            val episodeView = episodesListContainer.findViewWithTag<TextView?>("episode_${selectedSeasonIndex}_${episodeIndex}")
            episodeView?.let {
                val progress = allProgress.find { it.partIndex == selectedSeasonIndex && it.episodeIndex == episodeIndex }
                it.text = if (progress != null && progress.currentPositionMs < progress.totalDurationMs) {
                    val remainingMs = progress.totalDurationMs - progress.currentPositionMs
                    val remainingMinutes = remainingMs / (1000 * 60)
                    val remainingSeconds = (remainingMs / 1000) % 60
                    "Episodio ${episode.capitulo}: ${episode.titulo} (Continuar: ${remainingMinutes}m ${remainingSeconds}s restantes)"
                } else if (progress != null) {
                    "Episodio ${episode.capitulo}: ${episode.titulo} (Completado)"
                } else {
                    "Episodio ${episode.capitulo}: ${episode.titulo} - Reproducir ahora"
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.content_detail_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                true
            }
            R.id.action_share -> {
                shareContent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareContent() {
        val contentItem = viewModel.contentItem.value ?: return
        val itemType = when (contentItem) {
            is Movie -> "película"
            is Serie -> "serie"
            is Documentary -> "documental"
            is ShortFilm -> "cortometraje"
            else -> "contenido"
        }

        val message = "¡Oye! Estoy escuchando esta increíble $itemType llamada '${contentItem.title}' en la Audiocinemateca. ¡Seguro que a ti también te podría gustar! Da clic en este enlace para que lo escuches en la app. (Si el enlace no se abre directamente en la app, asegúrate de tener activada la opción 'Abrir enlaces compatibles' en la configuración de la aplicación Audiocinemateca en tu dispositivo.)"
        val url = "https://audiocinemateca.com/$itemType?id=${contentItem.id}"
        val fullMessage = "$message\n\n$url"

        if (itemType.isNotEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullMessage)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir contenido"))
        } else {
            Toast.makeText(requireContext(), "No se puede compartir este tipo de contenido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContentNotFoundDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Contenido no encontrado")
            .setMessage("El contenido que intentas ver no está disponible o no existe.")
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
                findNavController().navigateUp() // Volver a la pantalla anterior
            }
            .setCancelable(false)
            .show()
    }
}