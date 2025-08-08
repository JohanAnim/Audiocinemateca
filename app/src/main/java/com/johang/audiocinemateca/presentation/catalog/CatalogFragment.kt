package com.johang.audiocinemateca.presentation.catalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.repository.SearchRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.MainNavGraphDirections

@AndroidEntryPoint
class CatalogFragment : Fragment() {

    @Inject
    lateinit var searchRepository: SearchRepository

    private val viewModel: CatalogViewModel by viewModels()
    private lateinit var pagerAdapter: CatalogPagerAdapter
    private lateinit var filterTypeSpinner: Spinner
    private lateinit var filterValueSpinner: Spinner
    private lateinit var viewPager: ViewPager2
    private var ignoreSpinnerSelection = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true) // Indicate that this fragment has options menu

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        

        this.viewPager = view.findViewById<ViewPager2>(R.id.view_pager)
        filterTypeSpinner = view.findViewById<Spinner>(R.id.filter_type_spinner)
        filterValueSpinner = view.findViewById<Spinner>(R.id.filter_value_spinner)

        // Setup ViewPager and Adapter
        pagerAdapter = CatalogPagerAdapter(requireActivity())
        viewPager.adapter = pagerAdapter

        // Link TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()

        // Track current category based on ViewPager selection
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentCategoryName = getCurrentCategoryName()
                    val filterOptions = viewModel.filterRepository.getFilterOptionsFlow(currentCategoryName).first()

                    // Set filterTypeSpinner selection
                    val filterTypeAdapter = filterTypeSpinner.adapter as? ArrayAdapter<String>
                    val filterTypeIndex = filterTypeAdapter?.getPosition(filterOptions.filterType) ?: -1
                    if (filterTypeIndex != -1) {
                        
                        filterTypeSpinner.setSelection(filterTypeIndex)
                    }

                    // Update filterValueSpinner based on the selected filterType and current filter options
                    updateFilterValueSpinner(filterOptions.filterType, null)
                }
            }
        })

        // Setup Spinners
        setupFilterTypeSpinner()
        setupFilterValueSpinner()
        // Initial setup for filterValueSpinner based on the current category
        viewLifecycleOwner.lifecycleScope.launch {
            val currentCategoryName = getCurrentCategoryName()
            val filterOptions = viewModel.filterRepository.getFilterOptionsFlow(currentCategoryName).first()
            updateFilterValueSpinner(filterOptions.filterType, filterOptions.filterValue)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // Limpiar elementos de menú existentes
        inflater.inflate(R.menu.catalog_combined_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                try {
                    findNavController().navigate(CatalogFragmentDirections.actionCatalogFragmentToSearchFragment())
                } catch (e: Exception) {
                    Log.e("CatalogFragment", "Error al navegar a SearchFragment: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error al abrir la búsqueda: ${e.message}", Toast.LENGTH_LONG).show()
                }
                true
            }
            R.id.action_surprise_me -> {
                lifecycleScope.launch {
                    val randomItem = searchRepository.getRandomCatalogItem()
                    randomItem?.let { item ->
                        val itemType = when (item) {
                            is com.johang.audiocinemateca.data.model.Movie -> "peliculas"
                            is com.johang.audiocinemateca.data.model.Serie -> "series"
                            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
                            is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometrajes"
                            else -> ""
                        }
                        val action = MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, itemType)
                        findNavController().navigate(action)
                    } ?: run {
                        Toast.makeText(requireContext(), "No se pudo encontrar un elemento aleatorio.", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getCurrentCategoryName(): String {
        val rawCategoryName = pagerAdapter.getPageTitle(viewPager.currentItem).toString().lowercase()
        val normalizedCategoryName = when (rawCategoryName) {
            "películas" -> "peliculas"
            else -> rawCategoryName
        }
        Log.d("CatalogFragment", "getCurrentCategoryName: Raw category is '$rawCategoryName', Normalized category is '$normalizedCategoryName'")
        return normalizedCategoryName
    }

    private fun setupFilterTypeSpinner() {
        val filterTypes = arrayOf("Alfabéticamente", "Fecha", "Género", "Idiomas")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterTypeSpinner.adapter = adapter

        filterTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFilterType = filterTypes[position]
                // When filter type changes, reset the filter value to its default for the new type
                updateFilterValueSpinner(selectedFilterType, null) // Pass null to force default selection
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateFilterValueSpinner(filterType: String, currentFilterValue: String?) {
        val adapter: ArrayAdapter<String>
        val defaultFilterValue: String

        when (filterType) {
            "Alfabéticamente" -> {
                val alphaOptions = arrayOf("A-Z", "Z-A")
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, alphaOptions)
                defaultFilterValue = "A-Z"
            }
            "Fecha" -> {
                val dateOptions = arrayOf("Más nuevo", "Más antiguo", "Con fecha más reciente", "Con fecha más antigua")
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, dateOptions)
                defaultFilterValue = "Más nuevo"
            }
            "Género" -> {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mutableListOf("Todos"))
                defaultFilterValue = "Todos"
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val genres = viewModel.getGenresForCategory(getCurrentCategoryName()) // Get genres for current category
                        adapter.clear()
                        adapter.addAll(listOf("Todos") + genres)
                        adapter.notifyDataSetChanged()

                        // Set selection after genres are loaded and adapter is updated
                        val finalSelectionIndex = (filterValueSpinner.adapter as? ArrayAdapter<String>)?.getPosition(currentFilterValue) ?: -1
                        if (finalSelectionIndex != -1) {
                            filterValueSpinner.setSelection(finalSelectionIndex, false)
                        } else {
                            val defaultSelectionIndex = (filterValueSpinner.adapter as? ArrayAdapter<String>)?.getPosition(defaultFilterValue) ?: -1
                            if (defaultSelectionIndex != -1) {
                                filterValueSpinner.setSelection(defaultSelectionIndex, false)
                            }
                        }
                        val finalSelectedValue = filterValueSpinner.selectedItem?.toString() ?: ""
                        viewModel.updateFilter(getCurrentCategoryName(), filterType, finalSelectedValue)
                    } catch (e: Exception) {
                        val errorMessage = "Error al cargar géneros: ${e.message}"
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Error", errorMessage)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Error copiado al portapapeles: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            "Idiomas" -> {
                val languageOptions = arrayOf("Todos", "Español Latino", "Español de España")
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languageOptions)
                defaultFilterValue = "Todos"
            }
            else -> {
                adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, emptyArray<String>())
                defaultFilterValue = ""
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterValueSpinner.adapter = adapter

        val selectionIndex = (filterValueSpinner.adapter as? ArrayAdapter<String>)?.getPosition(currentFilterValue) ?: -1
        if (selectionIndex != -1) {
            filterValueSpinner.setSelection(selectionIndex, false) // Use false to not trigger the listener
        } else {
            // If the currentFilterValue is not found, set to defaultFilterValue
            val defaultSelectionIndex = (filterValueSpinner.adapter as? ArrayAdapter<String>)?.getPosition(defaultFilterValue) ?: -1
            if (defaultSelectionIndex != -1) {
                filterValueSpinner.setSelection(defaultSelectionIndex, false)
            }
        }

        // Call updateFilter here for synchronous cases (Alfabéticamente, Fecha, Idiomas)
        if (filterType != "Género") {
            val finalSelectedValue = filterValueSpinner.selectedItem?.toString() ?: ""
            viewModel.updateFilter(getCurrentCategoryName(), filterType, finalSelectedValue)
        }
    }

    private fun setupFilterValueSpinner() {
        filterValueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerSelection) {
                    ignoreSpinnerSelection = false
                    return
                }

                val selectedFilterType = filterTypeSpinner.selectedItem.toString()
                val selectedFilterValue = parent?.getItemAtPosition(position)?.toString() ?: ""
                val currentCategory = getCurrentCategoryName()
                Log.d("CatalogFragment", "onItemSelected: Updating filter for category: '$currentCategory', type: '$selectedFilterType', value: '$selectedFilterValue'")
                viewModel.updateFilter(currentCategory, selectedFilterType, selectedFilterValue)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}