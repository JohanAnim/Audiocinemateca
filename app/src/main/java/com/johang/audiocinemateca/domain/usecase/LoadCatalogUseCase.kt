package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.AuthCatalogRepository.LoadCatalogResultWithProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoadCatalogUseCase @Inject constructor(
    private val authCatalogRepository: AuthCatalogRepository
) {
    suspend fun execute(): Flow<LoadCatalogResultWithProgress> {
        return authCatalogRepository.loadCatalog()
    }
}
