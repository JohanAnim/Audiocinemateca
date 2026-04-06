package com.johang.audiocinemateca.presentation.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint

import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

@AndroidEntryPoint
class CommunityFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_community, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Comunidad"

        val tabLayout = view.findViewById<TabLayout>(R.id.community_tab_layout)
        val viewPager = view.findViewById<ViewPager2>(R.id.community_view_pager)

        viewPager.adapter = CommunityPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Global"
                1 -> "Anuncios"
                2 -> "Comentarios"
                else -> null
            }
        }.attach()

        // Si venimos de una notificación de anuncios, seleccionamos la pestaña 1 (Anuncios)
        val navigateTo = arguments?.getString("select_tab") ?: activity?.intent?.getStringExtra("navigate_to")
        if (navigateTo == "announcements") {
            viewPager.setCurrentItem(1, false)
            // Limpiamos tanto el intent como los argumentos para evitar repeticiones
            activity?.intent?.removeExtra("navigate_to")
            arguments?.remove("select_tab")
        }
    }
}
