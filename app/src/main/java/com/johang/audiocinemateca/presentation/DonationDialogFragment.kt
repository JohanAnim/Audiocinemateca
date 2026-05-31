package com.johang.audiocinemateca.presentation

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.databinding.DialogDonationBinding

class DonationDialogFragment : DialogFragment() {

    private lateinit var binding: DialogDonationBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogDonationBinding.inflate(LayoutInflater.from(context))

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.donateOfficialButton.setOnClickListener {
            openUrl("https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J")
            dismiss()
        }

        binding.donateDirectButton.setOnClickListener {
            openUrl("https://www.paypal.me/johananimg")
            dismiss()
        }

        binding.donateExternalButton.setOnClickListener {
            openUrl("https://audiocinemateca.com/donar")
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // Silently fail
        }
    }

    companion object {
        const val TAG = "DonationDialog"
    }
}