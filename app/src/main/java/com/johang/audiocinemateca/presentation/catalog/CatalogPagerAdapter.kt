package com.johang.audiocinemateca.presentation.catalog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class CatalogPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    private val categoryKeys = listOf("peliculas", "series", "cortometrajes", "documentales")

    override fun getItemCount(): Int = categoryKeys.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PeliculasFragment()
            1 -> SeriesFragment()
            2 -> CortometrajesFragment()
            3 -> DocumentalesFragment()
            else -> throw IllegalStateException("Invalid position")
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "Películas"
            1 -> "Series"
            2 -> "Cortometrajes"
            3 -> "Documentales"
            else -> ""
        }
    }

    fun getCategoryKey(position: Int): String {
        return categoryKeys[position]
    }
}