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

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        val viewPager = view.findViewById<ViewPager2>(R.id.view_pager)

        // Deshabilitar la navegación por gestos laterales entre pestañas
        viewPager.isUserInputEnabled = false
        
        viewPager.adapter = CommunityPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Global"
                1 -> "Anuncios"
                2 -> "Comentarios"
                else -> null
            }
        }.attach()

        val navigateTo = arguments?.getString("select_tab") ?: activity?.intent?.getStringExtra("navigate_to")
        if (navigateTo == "announcements") {
            viewPager.setCurrentItem(1, false)
            activity?.intent?.removeExtra("navigate_to")
            arguments?.remove("select_tab")
        }
    }
}
