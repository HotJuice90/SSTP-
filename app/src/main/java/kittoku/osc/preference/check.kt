package kittoku.osc.preference

import android.content.SharedPreferences
import home.keenetic.sstp.R
import kittoku.osc.MAX_MRU
import kittoku.osc.MAX_MTU
import kittoku.osc.MIN_MRU
import kittoku.osc.MIN_MTU
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getIntPrefValue
import kittoku.osc.preference.accessor.getSetPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.getURIPrefValue


/**
 * Причина, по которой подключаться нельзя. Хранит id строки, а не текст:
 * сообщение переводится на стороне UI.
 */
internal class InvalidSetting(val messageId: Int, val args: List<Any> = emptyList())

internal fun checkPreferences(prefs: SharedPreferences): InvalidSetting? {
    getStringPrefValue(OscPrefKey.HOME_HOSTNAME, prefs).also {
        if (it.isEmpty()) return InvalidSetting(R.string.check_hostname)
    }

    getIntPrefValue(OscPrefKey.SSL_PORT, prefs).also {
        if (it !in 0..65535) return InvalidSetting(R.string.check_port)
    }

    val doSpecifyCerts = getBooleanPrefValue(OscPrefKey.SSL_DO_SPECIFY_CERT, prefs)
    val version = getStringPrefValue(OscPrefKey.SSL_VERSION, prefs)
    val certDir = getURIPrefValue(OscPrefKey.SSL_CERT_DIR, prefs)
    if (doSpecifyCerts && version == "DEFAULT") return InvalidSetting(R.string.check_cert_version)
    if (doSpecifyCerts && certDir == null) return InvalidSetting(R.string.check_cert_dir)

    val doSelectSuites = getBooleanPrefValue(OscPrefKey.SSL_DO_SELECT_SUITES, prefs)
    val suites = getSetPrefValue(OscPrefKey.SSL_SUITES, prefs)
    if (doSelectSuites && suites.isEmpty()) return InvalidSetting(R.string.check_suites)

    val doUseCustomSNI = getBooleanPrefValue(OscPrefKey.SSL_DO_USE_CUSTOM_SNI, prefs)
    val customSNIHostname = getStringPrefValue(OscPrefKey.SSL_CUSTOM_SNI, prefs)
    if (doUseCustomSNI && customSNIHostname.isEmpty()) return InvalidSetting(R.string.check_sni)

    if (getBooleanPrefValue(OscPrefKey.PROXY_DO_USE_PROXY, prefs)) {
        getStringPrefValue(OscPrefKey.PROXY_HOSTNAME, prefs).also {
            if (it.isEmpty()) return InvalidSetting(R.string.check_proxy_hostname)
        }

        getIntPrefValue(OscPrefKey.PROXY_PORT, prefs).also {
            if (it !in 0..65535) return InvalidSetting(R.string.check_proxy_port)
        }
    }

    getIntPrefValue(OscPrefKey.PPP_MRU, prefs).also {
        if (it !in MIN_MRU..MAX_MRU) {
            return InvalidSetting(R.string.check_mru, listOf(MIN_MRU, MAX_MRU))
        }
    }

    getIntPrefValue(OscPrefKey.PPP_MTU, prefs).also {
        if (it !in MIN_MTU..MAX_MTU) {
            return InvalidSetting(R.string.check_mtu, listOf(MIN_MTU, MAX_MTU))
        }
    }

    val isIPv4Enabled = getBooleanPrefValue(OscPrefKey.PPP_IPv4_ENABLED, prefs)
    val isIPv6Enabled = getBooleanPrefValue(OscPrefKey.PPP_IPv6_ENABLED, prefs)
    if (!isIPv4Enabled && !isIPv6Enabled) return InvalidSetting(R.string.check_no_protocol)

    val isStaticIPv4Requested = getBooleanPrefValue(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS, prefs)
    if (isIPv4Enabled && isStaticIPv4Requested) {
        getStringPrefValue(OscPrefKey.PPP_STATIC_IPv4_ADDRESS, prefs).also {
            if (it.isEmpty()) return InvalidSetting(R.string.check_static_ipv4)
        }
    }

    val authProtocols = getSetPrefValue(OscPrefKey.PPP_AUTH_PROTOCOLS, prefs)
    if (authProtocols.isEmpty()) return InvalidSetting(R.string.check_auth)

    getIntPrefValue(OscPrefKey.PPP_AUTH_TIMEOUT, prefs).also {
        if (it < 1) return InvalidSetting(R.string.check_auth_timeout)
    }

    val isCustomDNSServerUsed = getBooleanPrefValue(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER, prefs)
    val isCustomAddressEmpty = getStringPrefValue(OscPrefKey.DNS_CUSTOM_ADDRESS, prefs).isEmpty()
    if (isCustomDNSServerUsed && isCustomAddressEmpty) return InvalidSetting(R.string.check_dns)

    val doSaveLog = getBooleanPrefValue(OscPrefKey.LOG_DO_SAVE_LOG, prefs)
    val logDir = getURIPrefValue(OscPrefKey.LOG_DIR, prefs)
    if (doSaveLog && logDir == null) return InvalidSetting(R.string.check_log_dir)

    return null
}
