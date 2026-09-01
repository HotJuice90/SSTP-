package kittoku.osc.control

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kittoku.osc.SharedBridge
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue


internal const val STATUS_KEY_PROTOCOL = "PROTOCOL"
internal const val STATUS_KEY_SUITE = "SUITE"
internal const val STATUS_KEY_IP = "IP"
internal const val STATUS_KEY_DNS = "DNS"
internal const val STATUS_KEY_ROUTE = "ROUTE"
internal const val STATUS_KEY_APP_LIST_TYPE = "APP_LIST_TYPE"
internal const val STATUS_KEY_APP = "APP"


internal class NetworkObserver(val bridge: SharedBridge) {
    private val manager = bridge.service.getSystemService(ConnectivityManager::class.java)
    private val callback: ConnectivityManager.NetworkCallback

    init {
        wipeStatus()

        val request = NetworkRequest.Builder().let {
            it.addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            it.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            it.build()
        }


        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    manager.getLinkProperties(network)?.also {
                        updateSummary(it)
                    }
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                updateSummary(linkProperties)
            }
        }

        manager.registerNetworkCallback(request, callback)
    }

    // Статус пишется парами KEY=value: подписи рисует UI, поэтому их можно
    // переводить, не трогая эту сторону. Значения — как их отдаёт система.
    private fun updateSummary(properties: LinkProperties) {
        val summary = mutableListOf<String>()

        bridge.sslTerminal!!.getSession().also {
            if (!it.isValid) return

            summary.add("$STATUS_KEY_PROTOCOL=${it.protocol}")
            summary.add("$STATUS_KEY_SUITE=${it.cipherSuite}")
        }

        properties.linkAddresses.forEach {
            it.address.hostAddress?.also { address ->
                summary.add("$STATUS_KEY_IP=$address")
            }
        }

        properties.dnsServers.forEach {
            it.hostAddress?.also { address ->
                summary.add("$STATUS_KEY_DNS=$address")
            }
        }

        properties.routes.forEach {
            summary.add("$STATUS_KEY_ROUTE=$it")
        }

        val doEnableAppBasedRule = getBooleanPrefValue(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, bridge.prefs)
        if (doEnableAppBasedRule) {
            val listType = getStringPrefValue(OscPrefKey.ROUTE_APP_LIST_TYPE, bridge.prefs)
            summary.add("$STATUS_KEY_APP_LIST_TYPE=$listType")
            bridge.selectedApps.forEach { summary.add("$STATUS_KEY_APP=${it.label}") }
        }

        setStringPrefValue(summary.joinToString("\n"), OscPrefKey.HOME_STATUS, bridge.prefs)
    }

    private fun wipeStatus() {
        setStringPrefValue("", OscPrefKey.HOME_STATUS, bridge.prefs)
    }

    internal fun close() {
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {} // already unregistered

        wipeStatus()
    }
}
