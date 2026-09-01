package com.johang.audiocinemateca.domain.model

data class DownloadProgressInfo(
    val contentId: String = "",
    val partIndex: Int = -1,
    val episodeIndex: Int = -1,
    val title: String = "",
    val progress: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val remainingSeconds: Long = 0L
)

