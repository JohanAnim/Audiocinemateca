package com.johang.audiocinemateca.presentation.mylists

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.Gravity

class MyListsPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3 // Recomendaciones, Favoritos, Historial

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PlaceholderFragment.newInstance("Recomendaciones") // Fragmento para Recomendaciones
            1 -> PlaceholderFragment.newInstance("Favoritos") // Fragmento para Favoritos
            2 -> PlaybackHistoryFragment() // Nuestro nuevo fragmento de historial
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}

// Fragmento de marcador de posición para las pestañas que aún no tienen contenido real
class PlaceholderFragment : Fragment() {
    companion object {
        private const val ARG_TEXT = "text"
        fun newInstance(text: String): PlaceholderFragment {
            val fragment = PlaceholderFragment()
            val args = Bundle()
            args.putString(ARG_TEXT, text)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val textView = TextView(requireContext())
        textView.text = arguments?.getString(ARG_TEXT)
        textView.gravity = Gravity.CENTER
        return textView
    }
}