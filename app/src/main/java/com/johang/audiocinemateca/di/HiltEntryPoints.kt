package com.johang.audiocinemateca.di

import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SharedPreferencesManagerEntryPoint {
    fun sharedPreferencesManager(): SharedPreferencesManager
}
