package com.johang.audiocinemateca.domain.model

data class FilterOptions(
    val sortType: String = "Fecha",
    val sortValue: String = "Más nuevo",
    val genre: String = "Todos",
    val language: String = "Todos",
    val country: String = "Todos",
    val narrator: String = "Todos",
    val provider: String = "Todos",
    val searchQuery: String? = null
) {
    // Propiedades de compatibilidad
    val filterType: String get() = sortType
    val filterValue: String get() = sortValue
}
