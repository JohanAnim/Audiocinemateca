package com.johang.audiocinemateca.presentation.community

object GlobalChatState {
    @Volatile
    var isChatScreenActive: Boolean = false

    @Volatile
    var isJamListener: Boolean = false
}
