package com.johang.audiocinemateca.presentation.catalog

interface FilterableContentFragment {
    fun applyFilter(filterType: String, filterValue: String?)
}