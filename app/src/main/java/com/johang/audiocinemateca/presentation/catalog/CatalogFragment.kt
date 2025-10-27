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
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
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

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private val viewModel: CatalogViewModel by viewModels()
    private lateinit var pagerAdapter: CatalogPagerAdapter
    private lateinit var filterTypeSpinner: Spinner
    private lateinit var filterValueSpinner: Spinner
    private lateinit var filterValueButton: Button
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
        filterValueButton = view.findViewById(R.id.filter_value_button)
        filterValueSpinner = view.findViewById(R.id.filter_value_spinner)

        // Setup ViewPager and Adapter
        pagerAdapter = CatalogPagerAdapter(requireActivity())
        viewPager.adapter = pagerAdapter

        // Link TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pagerAdapter.getPageTitle(position)
        }.attach()

        val defaultTab = sharedPreferencesManager.getString("default_content_tab", "peliculas")
        val tabPosition = when (defaultTab) {
            "series" -> 1
            "cortometrajes" -> 2
            "documentales" -> 3
            else -> 0
        }
        viewPager.setCurrentItem(tabPosition, false)

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
                    updateFilterValueSpinner(filterOptions.filterType, filterOptions.filterValue)
                }
            }
        })

        // Setup Spinners
        setupFilterTypeSpinner()
        
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
                    val currentCategory = getCurrentCategoryName()
                    val randomItem = searchRepository.getRandomCatalogItem(currentCategory)
                    randomItem?.let { item ->
                        val action = MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, currentCategory)
                        findNavController().navigate(action)
                    } ?: run {
                        Toast.makeText(requireContext(), "No se pudo encontrar un elemento aleatorio en esta categoría.", Toast.LENGTH_SHORT).show()
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
        val filterTypes = arrayOf("Alfabéticamente", "Fecha", "Género", "Idiomas", "Países")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterTypeSpinner.adapter = adapter

        filterTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFilterType = filterTypes[position]
                filterTypeSpinner.contentDescription = "Filtro actual: $selectedFilterType"
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentCategoryName = getCurrentCategoryName()
                    val currentFilterOptions = viewModel.filterRepository.getFilterOptionsFlow(currentCategoryName).first()

                    if (currentFilterOptions.filterType != selectedFilterType) {
                        val defaultValue = when (selectedFilterType) {
                            "Alfabéticamente" -> "A-Z"
                            "Fecha" -> "Más nuevo"
                            "Género" -> "Todos"
                            "Idiomas" -> "Español Latino"
                            "Países" -> "Todos"
                            else -> ""
                        }
                        viewModel.updateFilter(getCurrentCategoryName(), selectedFilterType, defaultValue)
                        updateFilterValueSpinner(selectedFilterType, defaultValue)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showFilterOptionsDialog(title: String, options: Array<String>, currentOption: String?, onSelected: (String) -> Unit) {
        val builder = AlertDialog.Builder(requireContext())
        var checkedItem = options.indexOf(currentOption)
        if (checkedItem == -1) {
            checkedItem = 0
        }
        builder.setTitle(title)
        builder.setSingleChoiceItems(options, checkedItem) { dialog, which ->
            onSelected(options[which])
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun updateFilterValueSpinner(filterType: String, currentFilterValue: String?) {
        if (filterType == "Género" || filterType == "Países") {
            val title = if (filterType == "Género") "Seleccionar género" else "Selecciona un país"
            filterValueButton.text = "Seleccionado: ${currentFilterValue ?: ""}"
            filterValueButton.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val items = if (filterType == "Género") {
                            viewModel.getGenresForCategory(getCurrentCategoryName())
                        } else {
                            viewModel.getCountriesForCategory(getCurrentCategoryName())
                        }
                        val dialog = SearchableSpinnerDialogFragment(title, items.toTypedArray(), filterValueButton.text.toString().substringAfter("Seleccionado: ")) { selectedValue ->
                            viewModel.updateFilter(getCurrentCategoryName(), filterType, selectedValue)
                            filterValueButton.post { filterValueButton.text = "Seleccionado: $selectedValue" }
                        }
                        dialog.show(parentFragmentManager, "SearchableSpinnerDialogFragment")
                    } catch (e: Exception) {
                        val errorMessage = "Error al cargar datos: ${e.message}"
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Error", errorMessage)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Error copiado al portapapeles: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            val options: Array<String>
            val defaultFilterValue: String
            when (filterType) {
                "Alfabéticamente" -> {
                    options = arrayOf("A-Z", "Z-A")
                    defaultFilterValue = "A-Z"
                }
                "Fecha" -> {
                    options = arrayOf("Más nuevo", "Más antiguo", "Con fecha más reciente", "Con fecha más antigua")
                    defaultFilterValue = "Más nuevo"
                }
                "Idiomas" -> {
                    options = arrayOf("Español Latino", "Español de España")
                    defaultFilterValue = "Español Latino"
                }
                else -> {
                    options = emptyArray<String>()
                    defaultFilterValue = ""
                }
            }
            filterValueButton.text = "Seleccionado: ${currentFilterValue ?: defaultFilterValue}"
            viewModel.updateFilter(getCurrentCategoryName(), filterType, currentFilterValue ?: defaultFilterValue)
            filterValueButton.setOnClickListener {
                showFilterOptionsDialog("Selecciona un valor", options, filterValueButton.text.toString().substringAfter("Seleccionado: ")) { selectedValue ->
                    filterValueButton.text = "Seleccionado: $selectedValue"
                    viewModel.updateFilter(getCurrentCategoryName(), filterType, selectedValue)
                }
            }
        }
    }

    
}