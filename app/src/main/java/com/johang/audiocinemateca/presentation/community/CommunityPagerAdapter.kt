package com.johang.audiocinemateca.presentation.community

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class CommunityPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GlobalChatFragment()
            1 -> AnnouncementsFragment()
            2 -> CommunityCommentsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
