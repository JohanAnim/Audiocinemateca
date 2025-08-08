package com.johang.audiocinemateca.data.remote

data class VersionResponse(
    val year: Int,
    val mon: Int,
    val mday: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
)