package kittoku.osc.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R
import kittoku.osc.preference.AUTH_PROTOCOL_LIST
import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.LIST_TYPE_DISALLOWED
import kittoku.osc.preference.OscPrefKey
import javax.net.ssl.SSLContext


private val SSL_VERSIONS: List<String> by lazy {
    listOf("DEFAULT") + SSLContext.getDefault().supportedSSLParameters.protocols.toList()
}

private val CIPHER_SUITES: List<String> by lazy {
    SSLContext.getDefault().supportedSSLParameters.cipherSuites.toList()
}

@Composable
internal fun SettingsScreen(
    prefs: PrefsRepository,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current

    Column {
        // --- PPP ---
        SectionTitle(stringResource(R.string.group_ppp))

        val mtu by prefs.intState(OscPrefKey.PPP_MTU)
        val mru by prefs.intState(OscPrefKey.PPP_MRU)

        IntSettingRow(
            title = stringResource(R.string.pref_ppp_mtu),
            value = mtu,
            summary = stringResource(R.string.pref_ppp_mtu_summary),
            onValueChange = { prefs.setInt(OscPrefKey.PPP_MTU, it) },
        )

        IntSettingRow(
            title = stringResource(R.string.pref_ppp_mru),
            value = mru,
            summary = stringResource(R.string.pref_ppp_mru_summary),
            onValueChange = { prefs.setInt(OscPrefKey.PPP_MRU, it) },
        )

        val isIPv4Enabled by prefs.booleanState(OscPrefKey.PPP_IPv4_ENABLED)
        val isIPv6Enabled by prefs.booleanState(OscPrefKey.PPP_IPv6_ENABLED)
        val isStaticIPv4Requested by prefs.booleanState(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS)
        val staticIPv4 by prefs.stringState(OscPrefKey.PPP_STATIC_IPv4_ADDRESS)

        SwitchRow(
            title = stringResource(R.string.pref_ppp_ipv4),
            checked = isIPv4Enabled,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.PPP_IPv4_ENABLED, it) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_ppp_static_ipv4_enabled),
            checked = isStaticIPv4Requested,
            enabled = isIPv4Enabled,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.PPP_DO_REQUEST_STATIC_IPv4_ADDRESS, it) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_ppp_static_ipv4),
            value = staticIPv4,
            placeholder = "192.168.0.1",
            enabled = isIPv4Enabled && isStaticIPv4Requested,
            onValueChange = { prefs.setString(OscPrefKey.PPP_STATIC_IPv4_ADDRESS, it.trim()) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_ppp_ipv6),
            checked = isIPv6Enabled,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.PPP_IPv6_ENABLED, it) },
        )

        GroupDivider()

        // --- аутентификация ---
        SectionTitle(stringResource(R.string.group_auth))

        val authProtocols by prefs.setState(OscPrefKey.PPP_AUTH_PROTOCOLS)
        val authTimeout by prefs.intState(OscPrefKey.PPP_AUTH_TIMEOUT)

        MultiSelectSettingRow(
            title = stringResource(R.string.pref_ppp_auth_protocols),
            summary = authProtocols.sorted().joinToString(", ").ifEmpty { "—" },
            options = AUTH_PROTOCOL_LIST,
            selected = authProtocols,
            onSelectionChange = { prefs.setSet(OscPrefKey.PPP_AUTH_PROTOCOLS, it) },
        )

        IntSettingRow(
            title = stringResource(R.string.pref_ppp_auth_timeout),
            value = authTimeout,
            onValueChange = { prefs.setInt(OscPrefKey.PPP_AUTH_TIMEOUT, it) },
        )

        GroupDivider()

        // --- DNS ---
        SectionTitle(stringResource(R.string.group_dns))

        val doRequestDns by prefs.booleanState(OscPrefKey.DNS_DO_REQUEST_ADDRESS)
        val doUseCustomDns by prefs.booleanState(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER)
        val customDns by prefs.stringState(OscPrefKey.DNS_CUSTOM_ADDRESS)

        SwitchRow(
            title = stringResource(R.string.pref_dns_do_request),
            checked = doRequestDns,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.DNS_DO_REQUEST_ADDRESS, it) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_dns_do_use_custom),
            checked = doUseCustomDns,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.DNS_DO_USE_CUSTOM_SERVER, it) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_dns_custom_address),
            value = customDns,
            enabled = doUseCustomDns,
            note = stringResource(R.string.pref_dns_custom_address_note),
            onValueChange = { prefs.setString(OscPrefKey.DNS_CUSTOM_ADDRESS, it.trim()) },
        )

        GroupDivider()

        // --- маршрутизация ---
        SectionTitle(stringResource(R.string.group_route))

        val doAddDefaultRoute by prefs.booleanState(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE)
        val doRoutePrivate by prefs.booleanState(OscPrefKey.ROUTE_DO_ROUTE_PRIVATE_ADDRESSES)
        val doAddCustomRoutes by prefs.booleanState(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES)
        val customRoutes by prefs.stringState(OscPrefKey.ROUTE_CUSTOM_ROUTES)
        val doEnableAppRule by prefs.booleanState(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE)
        val appListType by prefs.stringState(OscPrefKey.ROUTE_APP_LIST_TYPE)
        val selectedApps by prefs.setState(OscPrefKey.ROUTE_SELECTED_APPS)

        SwitchRow(
            title = stringResource(R.string.pref_route_default),
            checked = doAddDefaultRoute,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.ROUTE_DO_ADD_DEFAULT_ROUTE, it) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_route_private),
            checked = doRoutePrivate,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.ROUTE_DO_ROUTE_PRIVATE_ADDRESSES, it) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_route_custom_enabled),
            checked = doAddCustomRoutes,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.ROUTE_DO_ADD_CUSTOM_ROUTES, it) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_route_custom),
            value = customRoutes,
            placeholder = stringResource(R.string.pref_route_custom_hint),
            enabled = doAddCustomRoutes,
            isMultiline = true,
            onValueChange = { prefs.setString(OscPrefKey.ROUTE_CUSTOM_ROUTES, it.trim()) },
        )

        SwitchRow(
            title = stringResource(R.string.pref_route_app_rule),
            checked = doEnableAppRule,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, it) },
        )

        DropdownSettingRow(
            title = stringResource(R.string.pref_route_app_list_type),
            value = appListType,
            options = listOf(LIST_TYPE_ALLOWED, LIST_TYPE_DISALLOWED),
            enabled = doEnableAppRule,
            // Значение уходит в prefs и в профили как есть, переводится только подпись.
            labelOf = { value ->
                stringResource(
                    if (value == LIST_TYPE_ALLOWED) R.string.list_type_allowed else R.string.list_type_disallowed
                )
            },
            onValueChange = { prefs.setString(OscPrefKey.ROUTE_APP_LIST_TYPE, it) },
        )

        NavigationRow(
            title = stringResource(R.string.pref_route_selected_apps),
            summary = if (selectedApps.isEmpty()) {
                stringResource(R.string.apps_selected_none)
            } else {
                stringResource(R.string.apps_selected_count, selectedApps.size)
            },
            enabled = doEnableAppRule,
            onClick = onOpenApps,
        )

        if (doEnableAppRule && !doAddDefaultRoute) {
            Text(
                text = stringResource(R.string.apps_rule_needs_default_route),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        GroupDivider()

        // --- переподключение ---
        SectionTitle(stringResource(R.string.group_reconnection))

        val isReconnectionEnabled by prefs.booleanState(OscPrefKey.RECONNECTION_ENABLED)

        SwitchRow(
            title = stringResource(R.string.pref_reconnection_enabled),
            summary = stringResource(R.string.pref_reconnection_summary),
            checked = isReconnectionEnabled,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.RECONNECTION_ENABLED, it) },
        )

        GroupDivider()

        // --- журнал ---
        SectionTitle(stringResource(R.string.group_log))

        val doSaveLog by prefs.booleanState(OscPrefKey.LOG_DO_SAVE_LOG)
        val logDir by prefs.uriState(OscPrefKey.LOG_DIR)

        val logDirLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.also {
                // Права нужны и на чтение: DocumentFile обходит дерево каталога,
                // прежде чем создать в нём файл.
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )

                prefs.setUri(OscPrefKey.LOG_DIR, it)
            }
        }

        SwitchRow(
            title = stringResource(R.string.pref_log_do_save),
            summary = stringResource(R.string.pref_log_note),
            checked = doSaveLog,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.LOG_DO_SAVE_LOG, it) },
        )

        SettingRow(
            title = stringResource(R.string.pref_log_dir),
            summary = logDir?.path ?: stringResource(R.string.dir_not_selected),
            enabled = doSaveLog,
            onClick = { logDirLauncher.launch(null) },
        )

        GroupDivider()

        // --- дополнительно ---
        var isAdvancedShown by remember { mutableStateOf(false) }

        SwitchRow(
            title = stringResource(R.string.group_advanced),
            summary = stringResource(R.string.group_advanced_summary),
            checked = isAdvancedShown,
            onCheckedChange = { isAdvancedShown = it },
        )

        if (isAdvancedShown) {
            AdvancedSettings(prefs)
        }
    }
}

@Composable
private fun AdvancedSettings(prefs: PrefsRepository) {
    val context = LocalContext.current

    SectionTitle(stringResource(R.string.group_ssl))

    val sslVersion by prefs.stringState(OscPrefKey.SSL_VERSION)
    val doVerify by prefs.booleanState(OscPrefKey.SSL_DO_VERIFY)
    val doSpecifyCert by prefs.booleanState(OscPrefKey.SSL_DO_SPECIFY_CERT)
    val certDir by prefs.uriState(OscPrefKey.SSL_CERT_DIR)
    val doSelectSuites by prefs.booleanState(OscPrefKey.SSL_DO_SELECT_SUITES)
    val suites by prefs.setState(OscPrefKey.SSL_SUITES)
    val doUseCustomSni by prefs.booleanState(OscPrefKey.SSL_DO_USE_CUSTOM_SNI)
    val customSni by prefs.stringState(OscPrefKey.SSL_CUSTOM_SNI)

    val certDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.also {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )

            prefs.setUri(OscPrefKey.SSL_CERT_DIR, it)
        }
    }

    DropdownSettingRow(
        title = stringResource(R.string.pref_ssl_version),
        value = sslVersion,
        options = SSL_VERSIONS,
        onValueChange = { prefs.setString(OscPrefKey.SSL_VERSION, it) },
    )

    SwitchRow(
        title = stringResource(R.string.pref_ssl_do_verify),
        summary = stringResource(R.string.pref_ssl_do_verify_summary),
        checked = doVerify,
        onCheckedChange = { prefs.setBoolean(OscPrefKey.SSL_DO_VERIFY, it) },
    )

    SwitchRow(
        title = stringResource(R.string.pref_ssl_do_specify_cert),
        checked = doSpecifyCert,
        onCheckedChange = { prefs.setBoolean(OscPrefKey.SSL_DO_SPECIFY_CERT, it) },
    )

    SettingRow(
        title = stringResource(R.string.pref_ssl_cert_dir),
        summary = certDir?.path ?: stringResource(R.string.dir_not_selected),
        enabled = doSpecifyCert,
        onClick = { certDirLauncher.launch(null) },
    )

    SwitchRow(
        title = stringResource(R.string.pref_ssl_do_select_suites),
        checked = doSelectSuites,
        onCheckedChange = { prefs.setBoolean(OscPrefKey.SSL_DO_SELECT_SUITES, it) },
    )

    MultiSelectSettingRow(
        title = stringResource(R.string.pref_ssl_suites),
        summary = if (suites.isEmpty()) "—" else stringResource(R.string.apps_selected_count, suites.size),
        options = CIPHER_SUITES,
        selected = suites,
        enabled = doSelectSuites,
        onSelectionChange = { prefs.setSet(OscPrefKey.SSL_SUITES, it) },
    )

    SwitchRow(
        title = stringResource(R.string.pref_ssl_do_use_custom_sni),
        checked = doUseCustomSni,
        onCheckedChange = { prefs.setBoolean(OscPrefKey.SSL_DO_USE_CUSTOM_SNI, it) },
    )

    TextSettingRow(
        title = stringResource(R.string.pref_ssl_custom_sni),
        value = customSni,
        enabled = doUseCustomSni,
        onValueChange = { prefs.setString(OscPrefKey.SSL_CUSTOM_SNI, it.trim()) },
    )

    SectionTitle("HTTP Proxy")

    val doUseProxy by prefs.booleanState(OscPrefKey.PROXY_DO_USE_PROXY)
    val proxyHostname by prefs.stringState(OscPrefKey.PROXY_HOSTNAME)
    val proxyPort by prefs.intState(OscPrefKey.PROXY_PORT)
    val proxyUsername by prefs.stringState(OscPrefKey.PROXY_USERNAME)
    val proxyPassword by prefs.stringState(OscPrefKey.PROXY_PASSWORD)

    SwitchRow(
        title = stringResource(R.string.pref_proxy_do_use),
        checked = doUseProxy,
        onCheckedChange = { prefs.setBoolean(OscPrefKey.PROXY_DO_USE_PROXY, it) },
    )

    TextSettingRow(
        title = stringResource(R.string.pref_proxy_hostname),
        value = proxyHostname,
        enabled = doUseProxy,
        onValueChange = { prefs.setString(OscPrefKey.PROXY_HOSTNAME, it.trim()) },
    )

    IntSettingRow(
        title = stringResource(R.string.pref_proxy_port),
        value = proxyPort,
        enabled = doUseProxy,
        onValueChange = { prefs.setInt(OscPrefKey.PROXY_PORT, it) },
    )

    TextSettingRow(
        title = stringResource(R.string.pref_proxy_username),
        value = proxyUsername,
        enabled = doUseProxy,
        onValueChange = { prefs.setString(OscPrefKey.PROXY_USERNAME, it.trim()) },
    )

    TextSettingRow(
        title = stringResource(R.string.pref_proxy_password),
        value = proxyPassword,
        enabled = doUseProxy,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        onValueChange = { prefs.setString(OscPrefKey.PROXY_PASSWORD, it) },
    )
}
