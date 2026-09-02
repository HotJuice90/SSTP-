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
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.checkPreferences
import kittoku.osc.service.ACTION_VPN_CONNECT
import kittoku.osc.service.ACTION_VPN_DISCONNECT
import kittoku.osc.service.SstpVpnService
import kotlinx.coroutines.launch


private enum class Screen(val titleId: Int, val isTab: Boolean) {
    HOME(R.string.app_name, true),
    PROFILES(R.string.nav_profiles, true),
    SETTINGS(R.string.nav_settings, true),
    APPS(R.string.nav_apps, false),
    PROFILE_EDITOR(R.string.profile_new, false),
    DETAILS(R.string.status_title, false),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppRoot(prefs: PrefsRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    // Куда возвращаться из экранов, которые открываются поверх вкладки.
    var parentTab by rememberSaveable { mutableStateOf(Screen.HOME) }
    // null — создаём новый профиль, иначе правим существующий.
    var editedProfile by rememberSaveable { mutableStateOf<String?>(null) }

    val state by prefs.stringState(OscPrefKey.HOME_STATE)
    val hostname by prefs.stringState(OscPrefKey.HOME_HOSTNAME)
    val rawStatus by prefs.stringState(OscPrefKey.HOME_STATUS)
    val connectedAtRaw by prefs.stringState(OscPrefKey.HOME_CONNECTED_AT)

    val status = remember(rawStatus) { parseConnectionStatus(rawStatus) }
    val connectedAt = connectedAtRaw.toLongOrNull()


    fun open(target: Screen) {
        if (target.isTab) {
            parentTab = target
        }

        screen = target
    }

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
        // Подключаться нечем, пока нет ни одного профиля: ведём создавать.
        if (prefs.getString(OscPrefKey.HOME_HOSTNAME).isEmpty()) {
            editedProfile = null
            open(Screen.PROFILE_EDITOR)
            return
        }

        checkPreferences(prefs.raw)?.also { invalid ->
            val reason = context.getString(invalid.messageId, *invalid.args.toTypedArray())

            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.invalid_setting, reason))
            }

            return
        }

        touchProfile(prefs, prefs.getString(OscPrefKey.HOME_ACTIVE_PROFILE))

        VpnService.prepare(context)?.also {
            preparationLauncher.launch(it)
        } ?: startVpnService(ACTION_VPN_CONNECT)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen.isTab) Screen.HOME else parentTab
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                title = {
                    if (screen == Screen.HOME) {
                        Wordmark()
                    } else {
                        Text(stringResource(screen.titleId))
                    }
                },
                navigationIcon = {
                    if (!screen.isTab) {
                        IconButton(onClick = { screen = parentTab }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (screen.isTab) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = screen == Screen.HOME,
                        onClick = { open(Screen.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_home)) },
                        colors = itemColors,
                    )

                    NavigationBarItem(
                        selected = screen == Screen.PROFILES,
                        onClick = { open(Screen.PROFILES) },
                        icon = { Icon(Icons.Filled.FolderSpecial, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_profiles)) },
                        colors = itemColors,
                    )

                    NavigationBarItem(
                        selected = screen == Screen.SETTINGS,
                        onClick = { open(Screen.SETTINGS) },
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = itemColors,
                    )
                }
            }
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
                    prefs = prefs,
                    state = state,
                    hostname = hostname,
                    status = status,
                    connectedAt = connectedAt,
                    onConnect = { connect() },
                    onDisconnect = { startVpnService(ACTION_VPN_DISCONNECT) },
                    onOpenProfiles = { open(Screen.PROFILES) },
                    onCreateProfile = {
                        editedProfile = null
                        open(Screen.PROFILE_EDITOR)
                    },
                    onOpenApps = { open(Screen.APPS) },
                    onOpenDetails = { open(Screen.DETAILS) },
                )

                Screen.PROFILES -> ProfilesScreen(
                    prefs = prefs,
                    onCreateProfile = {
                        editedProfile = null
                        open(Screen.PROFILE_EDITOR)
                    },
                    onEditProfile = {
                        editedProfile = it
                        open(Screen.PROFILE_EDITOR)
                    },
                )

                Screen.SETTINGS -> SettingsScreen(
                    prefs = prefs,
                    onOpenApps = { open(Screen.APPS) },
                )

                Screen.APPS -> AppsScreen(prefs)

                Screen.DETAILS -> DetailsScreen(status)

                Screen.PROFILE_EDITOR -> ProfileEditorScreen(
                    prefs = prefs,
                    editedProfile = editedProfile,
                    onDone = { screen = parentTab },
                )
            }
        }
    }
}

@Composable
private fun Wordmark() {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("SSTP") }

            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            ) { append("+") }
        },
        style = MaterialTheme.typography.headlineSmall,
    )
}
