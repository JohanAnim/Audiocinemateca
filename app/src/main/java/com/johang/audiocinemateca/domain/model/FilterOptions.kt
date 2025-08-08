package com.johang.audiocinemateca.domain.model

data class FilterOptions(
    val filterType: String = "Alfabéticamente",
    val filterValue: String = "A-Z",
    val searchQuery: String? = null
)
