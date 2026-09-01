package kittoku.osc.ui

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.checkPreferences
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch


private enum class Screen(val titleId: Int) {
    HOME(R.string.app_name),
    CONNECTION(R.string.nav_connection),
    SETTINGS(R.string.nav_settings),
    PROFILES(R.string.nav_profiles),
    APPS(R.string.nav_apps),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppRoot(prefs: PrefsRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    val state by prefs.stringState(OscPrefKey.HOME_STATE)
    val hostname by prefs.stringState(OscPrefKey.HOME_HOSTNAME)
    val rawStatus by prefs.stringState(OscPrefKey.HOME_STATUS)
    val connectedAtRaw by prefs.stringState(OscPrefKey.HOME_CONNECTED_AT)

    val status = remember(rawStatus) { parseConnectionStatus(rawStatus) }
    val connectedAt = connectedAtRaw.toLongOrNull()

    fun startVpnService(action: String) {
        val intent = Intent(context, SstpVpnService::class.java).setAction(action)

        if (action == ACTION_VPN_CONNECT) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    // Согласие пользователя на VPN запрашивает система; без RESULT_OK сервис не стартует.
    val preparationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            startVpnService(ACTION_VPN_CONNECT)
        }
    }

    fun connect() {
        checkPreferences(prefs.raw)?.also { invalid ->
            val reason = context.getString(invalid.messageId, *invalid.args.toTypedArray())

            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.invalid_setting, reason))
            }

            return
        }

        VpnService.prepare(context)?.also {
            preparationLauncher.launch(it)
        } ?: startVpnService(ACTION_VPN_CONNECT)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.APPS) Screen.SETTINGS else Screen.HOME
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(screen.titleId)) },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(
                            onClick = {
                                screen = if (screen == Screen.APPS) Screen.SETTINGS else Screen.HOME
                            },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    // Список приложений скроллится сам, ему внешний скролл только мешает.
                    if (screen == Screen.APPS) Modifier else Modifier.verticalScroll(rememberScrollState())
                ),
        ) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    state = state,
                    hostname = hostname,
                    status = status,
                    connectedAt = connectedAt,
                    onConnect = { connect() },
                    onDisconnect = { startVpnService(ACTION_VPN_DISCONNECT) },
                    onOpenConnection = { screen = Screen.CONNECTION },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenProfiles = { screen = Screen.PROFILES },
                )

                Screen.CONNECTION -> ConnectionScreen(prefs)

                Screen.SETTINGS -> SettingsScreen(prefs, onOpenApps = { screen = Screen.APPS })

                Screen.PROFILES -> ProfilesScreen(prefs)

                Screen.APPS -> AppsScreen(prefs)
            }
        }
    }
}
