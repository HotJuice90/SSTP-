package kittoku.osc.ui

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit


// Адрес домашнего роутера хранится рядом с профилем: у каждого профиля свой дом.
private const val HOME_GATEWAY_HEADER = "HOME_GATEWAY."

/**
 * Изнутри домашней сети туннель к своему же роутеру обычно не поднимается: имя
 * резолвится во внешний адрес, и пакету некуда возвращаться — разворот
 * соединения на себя (hairpin NAT) умеют не все роутеры. Ошибка при этом
 * невнятная, поэтому такой случай узнаём заранее.
 *
 * Признак простой: шлюз текущей сети совпадает с адресом роутера, который тот же
 * профиль выдавал как DNS в прошлый раз.
 */
internal fun isOnHomeNetwork(context: Context, prefs: PrefsRepository, profile: String): Boolean {
    val known = readHomeGateway(prefs, profile) ?: return false

    return known == currentGateway(context)
}

internal fun rememberHomeGateway(prefs: PrefsRepository, profile: String, address: String) {
    if (!isPrivateAddress(address)) return

    prefs.raw.edit { putString(HOME_GATEWAY_HEADER + profile, address) }
}

private fun readHomeGateway(prefs: PrefsRepository, profile: String): String? {
    return prefs.raw.getString(HOME_GATEWAY_HEADER + profile, null)
}

private fun currentGateway(context: Context): String? {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val network = manager.activeNetwork ?: return null
    val properties = manager.getLinkProperties(network) ?: return null

    return properties.routes
        .firstOrNull { it.isDefaultRoute && it.gateway != null }
        ?.gateway
        ?.hostAddress
}

private fun isPrivateAddress(address: String): Boolean {
    if (address.startsWith("10.") || address.startsWith("192.168.")) return true

    // 172.16.0.0/12 — это 172.16 … 172.31
    if (address.startsWith("172.")) {
        val second = address.split(".").getOrNull(1)?.toIntOrNull() ?: return false

        return second in 16..31
    }

    return false
}
