package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.SearchHistoryDao
import com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val sharedPreferencesManager: SharedPreferencesManager
) {

    suspend fun saveSearchQuery(query: String) {
        val searchHistory = SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis())
        searchHistoryDao.insert(searchHistory)

        val limit = sharedPreferencesManager.getString("search_history_limit", "10")?.toIntOrNull() ?: 10
        searchHistoryDao.trimHistory(limit)
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