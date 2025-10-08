package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.media.VoicePlayer
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    @Inject
    lateinit var voicePlayer: VoicePlayer

    private val viewModel: AccountViewModel by activityViewModels()

    private var catalogLoadingDialog: AlertDialog? = null
    private var catalogProgressBar: ProgressBar? = null
    private var catalogProgressText: TextView? = null
    private var checkingDialog: AlertDialog? = null

    private var updateInfo: UpdateInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val welcomeText: TextView = view.findViewById(R.id.welcome_text)
        val downloadCountText: TextView = view.findViewById(R.id.download_count_text)
        val settingsButton: Button = view.findViewById(R.id.settings_button)
        val myAccountButton: Button = view.findViewById(R.id.my_account_button)
        val aboutButton: Button = view.findViewById(R.id.about_button)
        val donateButton: Button = view.findViewById(R.id.donate_button)
        val logoutButton: Button = view.findViewById(R.id.logout_button)
        val catalogVersionText: TextView = view.findViewById(R.id.catalog_version_text)
        val appVersionText: TextView = view.findViewById(R.id.app_version_text)
        val checkUpdatesButton: Button = view.findViewById(R.id.check_updates_button)
        val novedadesButton: Button = view.findViewById(R.id.novedades_button)
        val releasesButton: Button = view.findViewById(R.id.releases_button)

        // User name display
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername()
            welcomeText.text = "¡Bienvenido, ${username ?: "Usuario"}!"

            if (username == "Johan-a-g") {
                viewModel.updateState.collect {
                    val currentUpdateInfo = when (it) {
                        is UpdateCheckResult.UpdateAvailable -> it.updateInfo
                        is UpdateCheckResult.NoUpdateAvailable -> it.updateInfo
                        else -> null
                    }

                    if (currentUpdateInfo != null) {
                        val downloads = currentUpdateInfo.downloadCount
                        val downloadsText = if (downloads == 1) {
                            "Esta versión de la app tiene 1 descarga."
                        } else {
                            "Esta versión de la app tiene $downloads descargas."
                        }
                        downloadCountText.text = downloadsText
                        downloadCountText.visibility = View.VISIBLE
                    }
                }
            }
        }

        updateCatalogVersionText(catalogVersionText)

        try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName?.let {
                appVersionText.text = "Versión de la Aplicación: $it (toca para buscar actualizaciones)"
            } ?: run {
                appVersionText.text = "Versión de la Aplicación: N/A"
            }
        } catch (e: Exception) {
            appVersionText.text = "Versión de la Aplicación: N/A"
        }

        observeUpdateState()

        settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_accountFragment_to_settingsFragment)
        }

        myAccountButton.setOnClickListener {
            val url = "https://audiocinemateca.com/usuario"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }

        aboutButton.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about, null)
            val feedbackButton: Button = dialogView.findViewById(R.id.feedback_button)
            feedbackButton.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:") // only email apps should handle this
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("gutierrezjohanantonio@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "Retroalimentación de Audiocinemateca Beta")
                    }
                    startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(requireContext(), "No se encontró ninguna aplicación de correo electrónico.", Toast.LENGTH_SHORT).show()
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Acerca de Audiocinemateca")
                .setView(dialogView)
                .setPositiveButton("Aceptar", null)
                .show()
        }

        donateButton.setOnClickListener {
            val options = arrayOf("Invítame un refresco por el desarrollo de la app", "Dona directamente a la página de la audiocinemateca.com")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Apoya el proyecto")
                .setItems(options) { dialog, which ->
                    val url = when (which) {
                        0 -> "https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J"
                        1 -> "https://audiocinemateca.com/donaciones"
                        else -> null
                    }
                    url?.let {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it))
                        startActivity(intent)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        novedadesButton.setOnClickListener {
            if (updateInfo != null) {
                showNovedadesDialog(updateInfo!!)
            } else {
                Toast.makeText(requireContext(), "No hay novedades disponibles en este momento.", Toast.LENGTH_SHORT).show()
            }
        }

        releasesButton.setOnClickListener {
            val url = "https://github.com/JohanAnim/Audiocinemateca/releases"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }

        logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que quieres cerrar la sesión actual?")
                .setNegativeButton("No", null)
                .setPositiveButton("Sí") { dialog, _ ->
                    lifecycleScope.launch {
                        authCatalogRepository.logout()
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    dialog.dismiss()
                }
                .show()
        }

        checkUpdatesButton.setOnClickListener {
            lifecycleScope.launch {
                authCatalogRepository.loadCatalog().collect {
                    when (it) {
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("¡Enhorabuena!")
                                .setMessage("El catálogo tiene la última versión.")
                                .setPositiveButton("Aceptar", null)
                                .show()
                            updateCatalogVersionText(catalogVersionText)
                        }
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Actualización Disponible")
                                .setMessage("Hay una nueva versión del catálogo disponible. ¿Deseas descargarla ahora?")
                                .setPositiveButton("Sí") { dialog, _ ->
                                    dialog.dismiss()
                                    downloadAndUpdateCatalog(it.serverVersion)
                                }
                                .setNegativeButton("No", null)
                                .show()
                        }
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                            Log.e("AccountFragment", "Error al comprobar actualizaciones: ${it.message}")
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error")
                                .setMessage("Ocurrió un error al comprobar las actualizaciones: ${it.message}")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        else -> {}
                    }
                }
            }
        }

        appVersionText.setOnClickListener {
            try {
                val currentVersion = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
                if (currentVersion != null) {
                    showCheckingDialog()
                    lifecycleScope.launch {
                        val result = viewModel.manualCheckForUpdates(currentVersion)
                        hideCheckingDialog()
                        when (result) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                showUpdateDialog(result.updateInfo)
                            }
                            is UpdateCheckResult.NoUpdateAvailable -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Aplicación Actualizada")
                                    .setMessage("Ya tienes la última versión de la aplicación instalada.")
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                            is UpdateCheckResult.Error -> {
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Error")
                                    .setMessage("No se pudo comprobar si hay actualizaciones: ${result.message}")
                                    .setPositiveButton("Aceptar", null)
                                    .show()
                            }
                            UpdateCheckResult.Loading -> { /* Unreachable but handled */ }
                        }
                    }
                }
            } catch (e: Exception) {
                hideCheckingDialog()
                Toast.makeText(requireContext(), "No se pudo obtener la versión de la aplicación.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeUpdateState() {
        lifecycleScope.launch {
            viewModel.updateState.collect { result ->
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        this@AccountFragment.updateInfo = result.updateInfo
                        sendUpdateIndicatorBroadcast(true)
                    }
                    is UpdateCheckResult.NoUpdateAvailable -> {
                        this@AccountFragment.updateInfo = result.updateInfo
                        sendUpdateIndicatorBroadcast(false)
                    }
                    is UpdateCheckResult.Error -> {
                        this@AccountFragment.updateInfo = null
                        Log.e("AccountFragment", "Error checking for app update: ${result.message}")
                        sendUpdateIndicatorBroadcast(false)
                    }
                    is UpdateCheckResult.Loading -> {}
                }
            }
        }
    }

    private fun sendUpdateIndicatorBroadcast(show: Boolean) {
        val intent = if (show) {
            Intent(MainActivity.ACTION_SHOW_UPDATE_INDICATOR)
        } else {
            Intent(MainActivity.ACTION_HIDE_UPDATE_INDICATOR)
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_changelog, null)
        val changelogTextView: TextView = dialogView.findViewById(R.id.changelog_text_view)

        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = inputFormat.parse(updateInfo.updatedAt)
        val formattedDate = date?.let { outputFormat.format(it) } ?: "N/A"

        val fullChangelogMarkdown = "Fecha de lanzamiento: $formattedDate\n\n${updateInfo.changelog}"

        val markwon = Markwon.create(requireContext())
        markwon.setMarkdown(changelogTextView, fullChangelogMarkdown)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Novedades de esta versión")
            .setView(dialogView)
            .setPositiveButton("Toque para actualizar ahora a la última versión de la app") {
                dialog, _ ->
                viewModel.downloadUpdate(updateInfo)
                UpdateProgressDialogFragment().show(parentFragmentManager, "UpdateProgressDialog")
                dialog.dismiss()
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun showNovedadesDialog(updateInfo: UpdateInfo) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_changelog, null)
        val changelogTextView: TextView = dialogView.findViewById(R.id.changelog_text_view)

        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = inputFormat.parse(updateInfo.updatedAt)
        val formattedDate = date?.let { outputFormat.format(it) } ?: "N/A"

        val fullChangelogMarkdown = "Fecha de lanzamiento: $formattedDate\n\n${updateInfo.changelog}"

        val markwon = Markwon.create(requireContext())
        markwon.setMarkdown(changelogTextView, fullChangelogMarkdown)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Novedades de esta versión")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun downloadAndUpdateCatalog(serverVersion: java.util.Date) {
        lifecycleScope.launch {
            showCatalogLoadingDialog("Actualizando Catálogo", "Por favor, espere mientras se actualiza el catálogo.")
            authCatalogRepository.downloadAndSaveCatalog(serverVersion).collect {
                when (it) {
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
                        catalogProgressBar?.progress = it.percent
                        catalogProgressText?.text = "${it.percent}%%"
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                        hideCatalogLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Actualización Completa")
                            .setMessage("El catálogo se ha actualizado correctamente.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                        updateCatalogVersionText(view?.findViewById(R.id.catalog_version_text)!!)
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                        hideCatalogLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Error de Actualización")
                            .setMessage("Ocurrió un error al actualizar el catálogo: ${it.message}")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showCatalogLoadingDialog(title: String, message: String) {
        if (catalogLoadingDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading_catalog, null)
            catalogProgressBar = dialogView.findViewById(R.id.progress_bar)
            catalogProgressText = dialogView.findViewById(R.id.progress_text)

            catalogLoadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        catalogLoadingDialog?.show()
        catalogProgressText?.text = "${catalogProgressBar?.progress ?: 0}%%"
    }

    private fun hideCatalogLoadingDialog() {
        catalogLoadingDialog?.dismiss()
        catalogLoadingDialog = null
    }

    private fun showCheckingDialog() {
        if (checkingDialog == null) {
            checkingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Comprobando...")
                .setMessage("Buscando nuevas actualizaciones.")
                .setCancelable(false)
                .create()
        }
        checkingDialog?.show()
    }

    private fun hideCheckingDialog() {
        checkingDialog?.dismiss()
        checkingDialog = null
    }

    private fun updateCatalogVersionText(catalogVersionText: TextView) {
        lifecycleScope.launch {
            val catalogVersion = authCatalogRepository.getCatalogVersion()
            catalogVersionText.text = "Versión del Catálogo: ${catalogVersion?.let { android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it) } ?: "N/A"}"
        }
    }
}