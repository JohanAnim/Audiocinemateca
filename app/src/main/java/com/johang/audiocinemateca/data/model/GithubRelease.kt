package com.johang.audiocinemateca.data.model

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String,
    @SerializedName("assets") val assets: List<Asset>,
    @SerializedName("updated_at") val updatedAt: String
)

data class Asset(
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("name") val name: String,
    @SerializedName("download_count") val downloadCount: Int
)
