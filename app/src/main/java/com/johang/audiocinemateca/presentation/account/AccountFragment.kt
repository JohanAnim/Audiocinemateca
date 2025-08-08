package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.media.VoicePlayer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    @Inject
    lateinit var voicePlayer: VoicePlayer

    private var loadingDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

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
        val settingsButton: Button = view.findViewById(R.id.settings_button)
        val aboutButton: Button = view.findViewById(R.id.about_button)
        val logoutButton: Button = view.findViewById(R.id.logout_button)
        val catalogVersionText: TextView = view.findViewById(R.id.catalog_version_text)
        val appVersionText: TextView = view.findViewById(R.id.app_version_text)
        val checkUpdatesButton: Button = view.findViewById(R.id.check_updates_button)

        // Mostrar nombre de usuario
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername()
            welcomeText.text = "¡Bienvenido, ${username ?: "Usuario"}!"
        }

        // Mostrar versión del catálogo
        updateCatalogVersionText(catalogVersionText)

        // Mostrar versión de la aplicación
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            appVersionText.text = "Versión de la Aplicación: ${packageInfo.versionName}"
        } catch (e: Exception) {
            appVersionText.text = "Versión de la Aplicación: N/A"
        }

        // Configurar listeners de botones
        settingsButton.setOnClickListener { /* Lógica para Configuración */ }
        aboutButton.setOnClickListener { /* Lógica para Acerca de */ }
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
                                    // Iniciar la descarga con sonido y diálogo de progreso
                                    downloadAndUpdateCatalog(it.serverVersion)
                                }
                                .setNegativeButton("No", null)
                                .show()
                        }
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                            android.util.Log.e("AccountFragment", "Error al comprobar actualizaciones: ${it.message}")
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error")
                                .setMessage("Ocurrió un error al comprobar las actualizaciones: ${it.message}")
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        else -> { /* No-op para Progress en la comprobación inicial */ }
                    }
                }
            }
        }
    }

    private fun downloadAndUpdateCatalog(serverVersion: java.util.Date) {
        lifecycleScope.launch {
            // 1. Reproducir sonido de inicio
            voicePlayer.playVoice(R.raw.voz_descargando)
            // 2. Mostrar diálogo de carga
            showLoadingDialog("Actualizando Catálogo", "Por favor, espere mientras se actualiza el catálogo.")

            // 3. Iniciar la descarga
            authCatalogRepository.downloadAndSaveCatalog(serverVersion).collect { result ->
                when (result) {
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
                        // Actualizar progreso en el diálogo
                        progressBar?.progress = result.percent
                        progressText?.text = "${result.percent}%"
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                        // 4. Ocultar diálogo y reproducir sonido de finalización
                        hideLoadingDialog()
                        voicePlayer.playVoice(R.raw.voz_descarga_lista)
                        
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
                            .setMessage("Ocurrió un error al actualizar el catálogo: ${result.message}")
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
        progressText?.text = "${progressBar?.progress ?: 0}%"
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
