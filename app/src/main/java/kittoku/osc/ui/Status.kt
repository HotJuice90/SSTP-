package kittoku.osc.ui

import kittoku.osc.control.STATUS_KEY_APP
import kittoku.osc.control.STATUS_KEY_APP_LIST_TYPE
import kittoku.osc.control.STATUS_KEY_DNS
import kittoku.osc.control.STATUS_KEY_IP
import kittoku.osc.control.STATUS_KEY_PROTOCOL
import kittoku.osc.control.STATUS_KEY_ROUTE
import kittoku.osc.control.STATUS_KEY_SUITE


internal data class ConnectionStatus(
    val protocol: String? = null,
    val suite: String? = null,
    val addresses: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val appListType: String? = null,
    val apps: List<String> = emptyList(),
) {
    internal val isEmpty: Boolean
        get() = protocol == null && addresses.isEmpty() && routes.isEmpty()
}

/**
 * Разбирает то, что пишет NetworkObserver: строки вида KEY=value.
 * Подписи к значениям добавляет UI, поэтому статус переводится вместе с ним.
 */
internal fun parseConnectionStatus(raw: String): ConnectionStatus {
    var protocol: String? = null
    var suite: String? = null
    var appListType: String? = null
    val addresses = mutableListOf<String>()
    val dnsServers = mutableListOf<String>()
    val routes = mutableListOf<String>()
    val apps = mutableListOf<String>()

    raw.lineSequence().forEach { line ->
        val key = line.substringBefore('=', "")
        val value = line.substringAfter('=', "")

        if (value.isEmpty()) return@forEach

        when (key) {
            STATUS_KEY_PROTOCOL -> protocol = value
            STATUS_KEY_SUITE -> suite = value
            STATUS_KEY_IP -> addresses.add(value)
            STATUS_KEY_DNS -> dnsServers.add(value)
            STATUS_KEY_ROUTE -> routes.add(value)
            STATUS_KEY_APP_LIST_TYPE -> appListType = value
            STATUS_KEY_APP -> apps.add(value)
        }
    }

    return ConnectionStatus(
        protocol = protocol,
        suite = suite,
        addresses = addresses,
        dnsServers = dnsServers,
        routes = routes,
        appListType = appListType,
        apps = apps,
    )
}

internal fun formatUptime(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
