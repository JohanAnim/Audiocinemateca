package com.johang.audiocinemateca.presentation

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.DialogWhatsNewBinding

class WhatsNewDialogFragment : DialogFragment() {

    private lateinit var binding: DialogWhatsNewBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogWhatsNewBinding.inflate(LayoutInflater.from(context))

        val changelog = arguments?.getString(ARG_CHANGELOG) ?: "No hay novedades disponibles."
        binding.changelogTextView.text = changelog

        binding.laterButton.setOnClickListener {
            dismiss()
        }

        binding.donateButton.setOnClickListener {
            val url = "https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    companion object {
        const val TAG = "WhatsNewDialog"
        private const val ARG_CHANGELOG = "changelog"

        fun newInstance(changelog: String): WhatsNewDialogFragment {
            val args = Bundle().apply {
                putString(ARG_CHANGELOG, changelog)
            }
            return WhatsNewDialogFragment().apply {
                arguments = args
            }
        }
    }
}
