package com.johang.audiocinemateca.data.model

import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName

data class LiveJamSession(
    val jamId: String = "",
    val hostUserId: String = "",
    val hostName: String = "",
    val contentId: String = "",
    val title: String = "",
    val contentType: String = "pelicula",
    val partIndex: Int = 0,
    val episodeIndex: Int = 0,
    val episodeTitle: String = "",
    @get:PropertyName("isPlaying")
    @SerializedName("isPlaying")
    val isPlaying: Boolean = true,
    val positionMs: Long = 0L,
    val lastUpdatedTimestamp: Long = 0L,
    val activeListenersCount: Int = 1,
    val pinCode: String = ""
)
