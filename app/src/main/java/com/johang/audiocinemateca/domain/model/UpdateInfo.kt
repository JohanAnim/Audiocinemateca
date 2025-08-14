package com.johang.audiocinemateca.domain.model

data class UpdateInfo(
    val version: String,
    val name: String,
    val changelog: String,
    val downloadUrl: String,
    val updatedAt: String,
    val downloadCount: Int
)
