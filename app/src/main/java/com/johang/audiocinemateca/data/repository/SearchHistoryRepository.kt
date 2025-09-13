package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao
import com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) {

    suspend fun saveSearchQuery(query: String) {
        val searchHistory = SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis())
        searchHistoryDao.insert(searchHistory)
    }

    fun getRecentSearches(): Flow<List<SearchHistoryEntity>> {
        return searchHistoryDao.getRecentSearches()
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.clearAll()
    }

    suspend fun deleteSearchHistoryItem(query: String) {
        searchHistoryDao.deleteByQuery(query)
    }
}
