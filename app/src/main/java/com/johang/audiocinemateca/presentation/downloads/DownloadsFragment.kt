package com.johang.audiocinemateca.presentation.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsFragment : Fragment(), DownloadsActionsBottomSheet.DownloadsActionsListener {

    private val viewModel: DownloadsViewModel by viewModels()
    private lateinit var downloadsAdapter: DownloadsAdapter
    private lateinit var emptyDownloadsText: TextView
    private lateinit var filterButton: MaterialButton
    private lateinit var deleteAllButton: MaterialButton
    private lateinit var totalSizeTextView: TextView
    private lateinit var totalDurationTextView: TextView
    private lateinit var loadingContainer: View
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Permiso concedido. ¡Ya puedes descargar contenido!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Permiso denegado. No podrás descargar contenido.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        totalSizeTextView = view.findViewById(R.id.total_size_textview)
        totalDurationTextView = view.findViewById(R.id.total_duration_textview)
        setupRecyclerView(view)
        setupFilterButton(view)
        setupDeleteAllButton(view)
        observeViewModel()
        showWelcomeDialogIfNeeded()
    }

    private fun setupDeleteAllButton(view: View) {
        deleteAllButton = view.findViewById(R.id.delete_all_button)
        deleteAllButton.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Todas las Descargas")
            .setMessage("¿Estás seguro de que quieres eliminar TODAS tus descargas? Esta acción no se puede deshacer y borrará todos los archivos de audio descargados.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Liberar espacio de almacenamiento") { _, _ ->
                viewModel.deleteAllDownloads()
            }
            .show()
    }

    private fun setupFilterButton(view: View) {
        filterButton = view.findViewById(R.id.filter_button)
        filterButton.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun showFilterDialog() {
        val filterOptions = arrayOf(
            DownloadFilter.All.displayName,
            DownloadFilter.Queued.displayName,
            DownloadFilter.InProgress.displayName,
            DownloadFilter.Failed.displayName,
            DownloadFilter.Completed.displayName
        )
        val currentFilter = viewModel.currentFilter.value
        val checkedItem = when (currentFilter) {
            DownloadFilter.All -> 0
            DownloadFilter.Queued -> 1
            DownloadFilter.InProgress -> 2
            DownloadFilter.Failed -> 3
            DownloadFilter.Completed -> 4
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtrar por")
            .setSingleChoiceItems(filterOptions, checkedItem) { dialog, which ->
                val selectedFilter = when (which) {
                    0 -> DownloadFilter.All
                    1 -> DownloadFilter.Queued
                    2 -> DownloadFilter.InProgress
                    3 -> DownloadFilter.Failed
                    4 -> DownloadFilter.Completed
                    else -> DownloadFilter.All
                }
                viewModel.setFilter(selectedFilter)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupRecyclerView(view: View) {
        loadingContainer = view.findViewById(R.id.loading_container)
        recyclerView = view.findViewById(R.id.downloads_recycler_view)

        downloadsAdapter = DownloadsAdapter(
            onSeriesGroupClick = { contentId ->
                viewModel.onSeriesGroupToggled(contentId)
            },
            onItemClick = { downloadEntity ->
                viewModel.onPlayItem(downloadEntity)
            },
            onMoreClick = { groupedDownload ->
                val isGroup = groupedDownload.contentType == "serie"
                val entityForIndices = if (!isGroup) groupedDownload.episodes.firstOrNull() else null

                DownloadsActionsBottomSheet.newInstance(
                    title = groupedDownload.title,
                    contentId = groupedDownload.contentId,
                    partIndex = entityForIndices?.partIndex ?: -1,
                    episodeIndex = entityForIndices?.episodeIndex ?: -1,
                    isGroup = isGroup,
                    contentType = groupedDownload.contentType
                ).show(childFragmentManager, DownloadsActionsBottomSheet.TAG)
            },
            onEpisodeClick = { downloadEntity ->
                viewModel.onPlayItem(downloadEntity)
            },
            onEpisodeMoreClick = { downloadEntity ->
                DownloadsActionsBottomSheet.newInstance(
                    title = downloadEntity.title,
                    contentId = downloadEntity.contentId,
                    partIndex = downloadEntity.partIndex,
                    episodeIndex = downloadEntity.episodeIndex,
                    isGroup = false,
                    contentType = downloadEntity.contentType
                ).show(childFragmentManager, DownloadsActionsBottomSheet.TAG)
            }
        )
        recyclerView.adapter = downloadsAdapter
        emptyDownloadsText = view.findViewById(R.id.empty_downloads_text)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is DownloadsUiState.Loading -> {
                        loadingContainer.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        emptyDownloadsText.visibility = View.GONE
                    }
                    is DownloadsUiState.Success -> {
                        loadingContainer.visibility = View.GONE
                        downloadsAdapter.updateItems(state.downloads)
                        if (state.downloads.isEmpty()) {
                            recyclerView.visibility = View.GONE
                            emptyDownloadsText.visibility = View.VISIBLE
                        } else {
                            recyclerView.visibility = View.VISIBLE
                            emptyDownloadsText.visibility = View.GONE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalSizeMb.collect { totalSize ->
                val displayText = if (totalSize >= 1000) {
                    val totalSizeGb = totalSize / 1000.0
                    String.format("Actualmente las descargas están ocupando en su dispositivo %.2f GB", totalSizeGb)
                } else {
                    String.format("Actualmente las descargas están ocupando en su dispositivo %.2f MB", totalSize)
                }
                totalSizeTextView.text = displayText
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalDurationMs.collect { totalDuration ->
                val formattedDuration = com.johang.audiocinemateca.util.TimeFormatUtils.formatDuration(totalDuration)
                totalDurationTextView.text = "Tienes $formattedDuration de contenido descargado para disfrutar offline"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentFilter.collect { filter ->
                val filterText = "Filtrar descargas por: ${filter.displayName}"
                filterButton.text = filterText
                filterButton.contentDescription = "Filtrar descargas. Filtro actual: ${filter.displayName}"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.viewActions.collect { event ->
                event.getContentIfNotHandled()?.let { action ->
                    when (action) {
                        is DownloadsViewModel.ViewAction.ShowDeleteConfirmation -> {
                            showDeleteConfirmationDialog(
                                contentId = action.contentId,
                                partIndex = action.partIndex,
                                episodeIndex = action.episodeIndex,
                                title = action.title,
                                isGroup = action.isGroup
                            )
                        }
                        is DownloadsViewModel.ViewAction.NavigateToPlayer -> {
                            val navAction = MainNavGraphDirections.actionGlobalPlayerFragment(
                                catalogItem = action.catalogItem,
                                partIndex = action.partIndex,
                                episodeIndex = action.episodeIndex
                            )
                            findNavController().navigate(navAction)
                        }
                        is DownloadsViewModel.ViewAction.ShowContentDetail -> {
                            val navAction = MainNavGraphDirections.actionGlobalContentDetailFragment(
                                itemId = action.itemId,
                                itemType = action.itemType
                            )
                            findNavController().navigate(navAction)
                        }
                        is DownloadsViewModel.ViewAction.ShowError -> {
                            showErrorDialog(action.message)
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun showWelcomeDialogIfNeeded() {
        val hasSeenWelcome = sharedPreferencesManager.getBoolean(SharedPreferencesManager.HAS_SEEN_DOWNLOADS_WELCOME_KEY)

        if (!hasSeenWelcome) {
            val title = "¡Las Descargas Han Llegado a Audiocinemateca!"
            val message = buildString {
                append("¡Prepárate para una nueva experiencia! La función que tanto esperabas ya está aquí.\n\n")
                append("**¿Qué puedes hacer en esta pantalla?**\n\n")
                append("• **Accede a todo tu contenido sin conexión:** Aquí verás todas las películas, documentales y cortos que descargues.\n")
                append("• **Continúa escuchando:** Reproduce tus audios directamente desde esta pantalla.\n")
                append("• **Gestiona tus descargas:** Libera espacio eliminando los títulos que ya no necesites con un solo toque.\n")
                append("• **Organiza tus series favoritas:** Las series se agruparán aquí mismo, permitiéndote ver fácilmente todos los episodios que has descargado de cada una.\n\n")
                append("¡Disfruta de la Audiocinemateca en cualquier lugar, sin depender de una conexión a internet!\n\n")

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    append("**Importante:** Para que las descargas funcionen en tu versión de Android, se te pedirá que concedas el permiso para acceder al almacenamiento. Es necesario para guardar los archivos de audio.")
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("De acuerdo") { dialog, _ ->
                    sharedPreferencesManager.saveBoolean(SharedPreferencesManager.HAS_SEEN_DOWNLOADS_WELCOME_KEY, true)
                    dialog.dismiss()
                    requestStoragePermission()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // El permiso ya está concedido
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // Opcional: Mostrar un diálogo explicando por qué necesitas el permiso por segunda vez.
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(contentId: String, partIndex: Int, episodeIndex: Int, title: String, isGroup: Boolean) {
        val message = if (isGroup) {
            "¿Estás seguro de que quieres eliminar todos los episodios descargados de la serie \"$title\"? Se borrarán todos los archivos de tu dispositivo."
        } else {
            "¿Estás seguro de que quieres eliminar este contenido de tus descargas? El archivo se borrará de tu dispositivo."
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar descarga")
            .setMessage(message)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.confirmDeleteItem(contentId, partIndex, episodeIndex, isGroup)
            }
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    override fun onDeleteClicked(contentId: String, partIndex: Int, episodeIndex: Int, title: String, isGroup: Boolean) {
        viewModel.onDeleteItem(contentId, partIndex, episodeIndex, title, isGroup)
    }

    override fun onViewDetailsClicked(itemId: String, itemType: String) {
        val pluralItemType = when (itemType) {
            "movie" -> "peliculas"
            "serie" -> "series"
            "documentary" -> "documentales"
            "shortfilm" -> "cortometrajes"
            else -> itemType // Fallback, though should not happen
        }
        val navAction = MainNavGraphDirections.actionGlobalContentDetailFragment(
            itemId = itemId,
            itemType = pluralItemType
        )
        findNavController().navigate(navAction)
    }
}








