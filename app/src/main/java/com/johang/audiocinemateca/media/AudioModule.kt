package com.johang.audiocinemateca.media

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideSoundEffectsPlayer(@ApplicationContext context: Context): SoundEffectsPlayer {
        return SoundEffectsPlayer(context)
    }

    @Provides
    @Singleton
    fun provideVoicePlayer(@ApplicationContext context: Context): VoicePlayer {
        return VoicePlayer(context)
    }
}
