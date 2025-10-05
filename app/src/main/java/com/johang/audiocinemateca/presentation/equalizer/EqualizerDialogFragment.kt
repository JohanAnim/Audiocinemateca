package com.johang.audiocinemateca.presentation.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.presentation.player.PlayerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EqualizerDialogFragment : DialogFragment() {

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private lateinit var equalizerEnabledSwitch: SwitchMaterial
    private lateinit var bandsContainer: LinearLayout
    private lateinit var flatButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_equalizer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        equalizerEnabledSwitch = view.findViewById(R.id.equalizer_enabled_switch)
        bandsContainer = view.findViewById(R.id.equalizer_bands_container)
        flatButton = view.findViewById(R.id.equalizer_flat_button)

        val equalizer = PlayerService.equalizer
        if (equalizer == null) {
            Toast.makeText(requireContext(), "El ecualizador no está disponible.", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        equalizerEnabledSwitch.isChecked = equalizer.enabled
        equalizerEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            equalizer.enabled = isChecked
            sharedPreferencesManager.saveBoolean("equalizer_enabled", isChecked)
            setEqualizerControlsEnabled(isChecked)
        }

        val bandLevelRange = equalizer.bandLevelRange
        val minLevel = bandLevelRange[0]
        val maxLevel = bandLevelRange[1]

        for (i in 0 until equalizer.numberOfBands) {
            val bandIndex = i.toShort()
            val centerFreq = equalizer.getCenterFreq(bandIndex)
            val bandLevel = equalizer.getBandLevel(bandIndex)

            val bandView = LayoutInflater.from(requireContext()).inflate(R.layout.item_equalizer_band, bandsContainer, false)
            
            val bandDescriptionLabel = bandView.findViewById<TextView>(R.id.band_description_label)
            val bandExplanationLabel = bandView.findViewById<TextView>(R.id.band_explanation_label)
            val minDbLabel = bandView.findViewById<TextView>(R.id.min_db_label)
            val maxDbLabel = bandView.findViewById<TextView>(R.id.max_db_label)
            val currentDbLabel = bandView.findViewById<TextView>(R.id.current_db_label)
            val bandSeekBar = bandView.findViewById<SeekBar>(R.id.band_seekbar)

            val bandDescription = getBandDescription(centerFreq)
            bandDescriptionLabel.text = "Ajustar ${bandDescription} (${centerFreq / 1000} Hz):"
            bandExplanationLabel.text = getBandExplanation(centerFreq)
            minDbLabel.text = "${minLevel / 100} dB"
            maxDbLabel.text = "${maxLevel / 100} dB"
            currentDbLabel.text = "${bandLevel / 100} dB"

            bandSeekBar.max = (maxLevel - minLevel).toInt()
            bandSeekBar.progress = bandLevel - minLevel

            bandSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val newLevel = (progress + minLevel).toShort()
                    equalizer.setBandLevel(bandIndex, newLevel)
                    sharedPreferencesManager.saveInt("equalizer_band_${bandIndex}", newLevel.toInt())
                    currentDbLabel.text = "${newLevel / 100} dB"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            bandsContainer.addView(bandView)
        }

        flatButton.setOnClickListener {
            for (i in 0 until equalizer.numberOfBands) {
                val bandIndex = i.toShort()
                equalizer.setBandLevel(bandIndex, 0)
                sharedPreferencesManager.saveInt("equalizer_band_${bandIndex}", 0)
                
                val bandView = bandsContainer.getChildAt(i)
                val bandSeekBar = bandView.findViewById<SeekBar>(R.id.band_seekbar)
                bandSeekBar.progress = 0 - minLevel
                val currentDbLabel = bandView.findViewById<TextView>(R.id.current_db_label)
                currentDbLabel.text = "0 dB"
            }
        }

        setEqualizerControlsEnabled(equalizer.enabled)
    }

    private fun getBandDescription(freq: Int): String {
        val freqHz = freq / 1000
        return when (freqHz) {
            in 0..60 -> "Sub-graves"
            in 61..250 -> "Graves"
            in 251..500 -> "Medios-bajos"
            in 501..2000 -> "Medios"
            in 2001..4000 -> "Medios-altos"
            in 4001..6000 -> "Presencia"
            else -> "Agudos"
        }
    }

    private fun getBandExplanation(freq: Int): String {
        val freqHz = freq / 1000
        return when (freqHz) {
            in 0..60 -> "Aumentar para más 'punch' en la música. Disminuir para reducir el retumbe."
            in 61..250 -> "Ajusta el cuerpo y la calidez del sonido. El exceso puede hacer que suene lodoso."
            in 251..500 -> "Afecta la claridad de los instrumentos graves y la plenitud de las voces."
            in 501..2000 -> "La zona principal de la voz humana. Ajustar para mayor o menor presencia vocal."
            in 2001..4000 -> "Define el ataque de los instrumentos y la inteligibilidad de las voces."
            in 4001..6000 -> "Aumentar para más claridad y definición. El exceso puede sonar áspero."
            else -> "Aporta brillo y 'aire' al sonido. El exceso puede introducir siseo."
        }
    }

    private fun setEqualizerControlsEnabled(enabled: Boolean) {
        flatButton.isEnabled = enabled
        for (i in 0 until bandsContainer.childCount) {
            val bandView = bandsContainer.getChildAt(i)
            val bandSeekBar = bandView.findViewById<SeekBar>(R.id.band_seekbar)
            bandSeekBar.isEnabled = enabled
        }
    }
}