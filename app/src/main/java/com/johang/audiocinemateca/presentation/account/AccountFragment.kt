package com.johang.audiocinemateca.presentation.account

import android.content.Intent
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.media.VoicePlayer
import com.johang.audiocinemateca.util.DownloadProgress
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
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

    private var loadingDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private lateinit var appUpdateButton: Button
    private lateinit var novedadesButton: Button
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
        val aboutButton: Button = view.findViewById(R.id.about_button)
        val logoutButton: Button = view.findViewById(R.id.logout_button)
        val catalogVersionText: TextView = view.findViewById(R.id.catalog_version_text)
        val appVersionText: TextView = view.findViewById(R.id.app_version_text)
        val checkUpdatesButton: Button = view.findViewById(R.id.check_updates_button)
        appUpdateButton = view.findViewById(R.id.app_update_button)
        novedadesButton = view.findViewById(R.id.novedades_button)

        // Mostrar nombre de usuario
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername()
            welcomeText.text = "¡Bienvenido, ${username ?: "Usuario"}!"

            // Mostrar contador de descargas si el usuario es "Johan-a-g"
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

        // Mostrar versión del catálogo
        updateCatalogVersionText(catalogVersionText)

        // Mostrar versión de la aplicación
        try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName?.let { currentVersion ->
                appVersionText.text = "Versión de la Aplicación: $currentVersion"
            } ?: run {
                appVersionText.text = "Versión de la Aplicación: N/A"
            }
        } catch (e: Exception) {
            appVersionText.text = "Versión de la Aplicación: N/A"
        }

        observeUpdateState()

        // Configurar listeners de botones
        settingsButton.setOnClickListener { /* Lógica para Configuración */ }
        aboutButton.setOnClickListener { /* Lógica para Acerca de */ }
        
        novedadesButton.setOnClickListener {
            if (updateInfo != null) {
                showNovedadesDialog(updateInfo!!)
            } else {
                Toast.makeText(requireContext(), "No hay novedades disponibles en este momento.", Toast.LENGTH_SHORT).show()
            }
        }
        
        logoutButton.setOnClickListener {
            lifecycleScope.launch {
                authCatalogRepository.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
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
                        else -> { /* No debería ocurrir */ }
                    }
                }
            }
        }
    }

    private fun observeUpdateState() {
        lifecycleScope.launch {
            viewModel.updateState.collect { result ->
                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        appUpdateButton.visibility = View.VISIBLE
                        this@AccountFragment.updateInfo = result.updateInfo
                        appUpdateButton.setOnClickListener {
                            showUpdateDialog(result.updateInfo)
                        }
                        sendUpdateIndicatorBroadcast(true)
                    }
                    is UpdateCheckResult.NoUpdateAvailable -> {
                        appUpdateButton.visibility = View.GONE
                        this@AccountFragment.updateInfo = result.updateInfo
                        sendUpdateIndicatorBroadcast(false)
                    }
                    is UpdateCheckResult.Error -> {
                        Log.e("AccountFragment", "Error checking for app update: ${result.message}")
                        appUpdateButton.visibility = View.GONE
                        this@AccountFragment.updateInfo = null
                        sendUpdateIndicatorBroadcast(false)
                    }
                    is UpdateCheckResult.Loading -> {
                        // Can show a loading indicator if needed
                    }
                }
            }
        }

        // Observar el progreso de la descarga
        lifecycleScope.launch {
            viewModel.downloadProgress.collect {
                when (it) {
                    is DownloadProgress.Idle -> {
                        hideLoadingDialog()
                    }
                    is DownloadProgress.Progress -> {
                        showLoadingDialog("Descargando Actualización", "Descargando la nueva versión de la app, por favor, espere un poco...")
                        progressBar?.isIndeterminate = false
                        progressBar?.progress = it.percent
                        progressText?.visibility = View.VISIBLE
                        progressText?.text = "${it.percent}%%"
                    }
                    is DownloadProgress.Success -> {
                        hideLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Descarga Completa")
                            .setMessage("La actualización se ha descargado correctamente. ¿Deseas instalarla ahora?")
                            .setPositiveButton("Instalar") {
                                dialog, _ ->
                                viewModel.installUpdate(it.uri)
                                dialog.dismiss()
                            }
                            .setNegativeButton("Más tarde", null)
                            .show()
                    }
                    is DownloadProgress.Error -> {
                        hideLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Error de Descarga")
                            .setMessage("Ocurrió un error al descargar la actualización: ${it.message}")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
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
            .setPositiveButton("Cerrar") {
                dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun downloadAndUpdateCatalog(serverVersion: java.util.Date) {
        lifecycleScope.launch {
            showLoadingDialog("Actualizando Catálogo", "Por favor, espere mientras se actualiza el catálogo.")
            authCatalogRepository.downloadAndSaveCatalog(serverVersion).collect {
                when (it) {
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
                        progressBar?.progress = it.percent
                        progressText?.text = "${it.percent}%%"
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                        hideLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Actualización Completa")
                            .setMessage("El catálogo se ha actualizado correctamente.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                        updateCatalogVersionText(view?.findViewById(R.id.catalog_version_text)!!)
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                        hideLoadingDialog()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Error de Actualización")
                            .setMessage("Ocurrió un error al actualizar el catálogo: ${it.message}")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                    else -> { /* No debería ocurrir */ }
                }
            }
        }
    }

    private fun showLoadingDialog(title: String, message: String) {
        if (loadingDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading_catalog, null)
            progressBar = dialogView.findViewById(R.id.progress_bar)
            progressText = dialogView.findViewById(R.id.progress_text)

            loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
        progressText?.text = "${progressBar?.progress ?: 0}%%"
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun updateCatalogVersionText(catalogVersionText: TextView) {
        lifecycleScope.launch {
            val catalogVersion = authCatalogRepository.getCatalogVersion()
            catalogVersionText.text = "Versión del Catálogo: ${catalogVersion?.let { android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it) } ?: "N/A"}"
        }
    }
}