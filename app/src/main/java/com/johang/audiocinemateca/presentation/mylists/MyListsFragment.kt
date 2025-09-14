package com.johang.audiocinemateca.presentation.mylists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.R.string.forget_all_button_text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyListsFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private val playbackHistoryViewModel: PlaybackHistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_lists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.my_lists_title)

        tabLayout = view.findViewById(R.id.tab_layout_mylists)
        viewPager = view.findViewById(R.id.view_pager_mylists)

        val adapter = MyListsPagerAdapter(requireActivity())
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Historial"
                1 -> "Favoritos"
                2 -> "Recomendaciones"
                else -> null
            }
        }.attach()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                activity?.invalidateOptionsMenu()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()

        if (viewPager.currentItem == 0) { // Pestaña "Historial"
            inflater.inflate(R.menu.history_menu, menu)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        if (viewPager.currentItem == 0) { // Pestaña "Historial"
            val clearHistoryItem = menu.findItem(R.id.action_clear_history)
            lifecycleScope.launch {
                // Observar el StateFlow para obtener el estado actual del historial
                playbackHistoryViewModel.historyItems.collect { items ->
                    val hasHistoryItems = items.any { it is HistoryListItem.Item }
                    clearHistoryItem?.isEnabled = hasHistoryItems
                    // Si el botón está deshabilitado, también podemos cambiar su apariencia si es necesario
                    // clearHistoryItem?.icon?.alpha = if (hasHistoryItems) 255 else 130
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_history -> {
                showClearAllConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.forget_all_history_title))
            .setMessage(getString(R.string.forget_all_history_message))
            .setPositiveButton(getString(forget_all_button_text)) { dialog, _ ->
                playbackHistoryViewModel.clearAllHistory()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        activity?.invalidateOptionsMenu()
    }
}