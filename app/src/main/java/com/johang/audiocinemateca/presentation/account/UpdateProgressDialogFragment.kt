package com.johang.audiocinemateca.presentation.account

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.util.DownloadProgress
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateProgressDialogFragment : DialogFragment() {

    private val viewModel: AccountViewModel by activityViewModels()
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_loading_catalog, null)
        progressBar = dialogView.findViewById(R.id.progress_bar)
        progressText = dialogView.findViewById(R.id.progress_text)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Descargando Actualización")
            .setMessage("Descargando la nueva versión de la app, por favor, espere un poco...")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        observeDownloadProgress()

        return dialog
    }

    private fun observeDownloadProgress() {
        lifecycleScope.launch {
            viewModel.downloadProgress.collect {
                when (it) {
                    is DownloadProgress.Idle -> {
                        dismissAllowingStateLoss()
                    }
                    is DownloadProgress.Progress -> {
                        progressBar?.isIndeterminate = false
                        progressBar?.progress = it.percent
                        progressText?.visibility = View.VISIBLE
                        progressText?.text = "${it.percent}%"
                    }
                    is DownloadProgress.Success -> {
                        dismissAllowingStateLoss()
                        showInstallDialog(it.uri)
                    }
                    is DownloadProgress.Error -> {
                        dismissAllowingStateLoss()
                        showErrorDialog(it.message)
                    }
                }
            }
        }
    }

    private fun showInstallDialog(uri: android.net.Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Descarga Completa")
            .setMessage("La actualización se ha descargado correctamente. ¿Deseas instalarla ahora?")
            .setPositiveButton("Instalar") { dialog, _ ->
                viewModel.installUpdate(uri)
                dialog.dismiss()
            }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Error de Descarga")
            .setMessage("Ocurrió un error al descargar la actualización: $message")
            .setPositiveButton("Aceptar", null)
            .show()
    }
}