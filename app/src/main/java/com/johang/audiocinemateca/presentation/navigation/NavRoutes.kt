package com.johang.audiocinemateca.presentation.navigation

import android.net.Uri
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem

object NavRoutes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val SEARCH = "search"
    const val MY_LISTS = "mylists"
    const val ACCOUNT = "account"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CATEGORY_SETTINGS = "category_settings/{category}"
    const val DOCUMENTATION = "documentation"
    const val LOGIN_CLOUD = "login_cloud"
    const val REGISTER_CLOUD = "register_cloud"
    const val COMMUNITY = "community?tab={tab}"
    const val AI_CHAT = "ai_chat"
    const val NOTIFICATIONS = "notifications"
    const val CONTENT_DETAIL = "content_detail/{itemId}/{itemType}"
    const val PLAYER = "player/{itemId}/{itemType}/{partIndex}/{episodeIndex}"
    const val WEBVIEW = "webview?url={url}"

    @Volatile
    var activePlayerCatalogItem: CatalogItem? = null

    fun categorySettings(category: String = "general"): String =
        "category_settings/${Uri.encode(category)}"

    fun community(tab: String = "chat"): String =
        "community?tab=${Uri.encode(tab)}"

    fun contentDetail(itemId: String, itemType: String): String =
        "content_detail/${Uri.encode(itemId)}/${Uri.encode(itemType)}"

    fun player(
        item: CatalogItem,
        partIndex: Int = 0,
        episodeIndex: Int = -1
    ): String {
        activePlayerCatalogItem = item
        val type = itemTypeOf(item)
        return player(item.id, type, partIndex, episodeIndex)
    }

    fun player(
        itemId: String,
        itemType: String,
        partIndex: Int = 0,
        episodeIndex: Int = -1
    ): String =
        "player/${Uri.encode(itemId)}/${Uri.encode(itemType)}/$partIndex/$episodeIndex"

    fun webView(url: String): String =
        "webview?url=${Uri.encode(url)}"

    fun itemTypeOf(item: CatalogItem): String = when (item) {
        is Movie -> "peliculas"
        is Serie -> "series"
        is Documentary -> "documentales"
        is ShortFilm -> "cortometrajes"
        else -> "peliculas"
    }
}
