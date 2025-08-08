package com.johang.audiocinemateca.data.remote.model

import com.google.gson.annotations.SerializedName

data class VersionData(
    @SerializedName("year") val year: Int,
    @SerializedName("mon") val mon: Int,
    @SerializedName("mday") val mday: Int,
    @SerializedName("hours") val hours: Int,
    @SerializedName("minutes") val minutes: Int,
    @SerializedName("seconds") val seconds: Int
)
