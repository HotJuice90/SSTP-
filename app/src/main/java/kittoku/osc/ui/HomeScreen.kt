package kittoku.osc.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.keenetic.sstp.R
import kittoku.osc.preference.LIST_TYPE_DISALLOWED
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.STATE_CONNECTED
import kittoku.osc.preference.STATE_CONNECTING
import kittoku.osc.preference.STATE_RECONNECTING
import kotlinx.coroutines.delay


@Composable
internal fun HomeScreen(
    prefs: PrefsRepository,
    state: String,
    hostname: String,
    status: ConnectionStatus,
    connectedAt: Long?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenProfiles: () -> Unit,
    onCreateProfile: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val isConnected = state == STATE_CONNECTED
    val isBusy = state == STATE_CONNECTING || state == STATE_RECONNECTING

    val activeProfile by prefs.stringState(OscPrefKey.HOME_ACTIVE_PROFILE)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Hero(
            name = activeProfile.ifEmpty { hostname }.ifEmpty { stringResource(R.string.no_host) },
            hostname = if (activeProfile.isEmpty()) "" else hostname,
            state = state,
            isConnected = isConnected,
            isBusy = isBusy,
            connectedAt = connectedAt,
            pingTarget = status.dnsServers.firstOrNull(),
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        ProfilesStrip(
            prefs = prefs,
            activeProfile = activeProfile,
            // Во время соединения переключать профиль нечем: настройки применяются
            // при подъёме туннеля, а экран показывал бы «Подключено» уже для чужого
            // профиля. Поэтому чужие чипы недоступны, пока туннель жив.
            isLocked = isConnected || isBusy,
            onOpenProfiles = onOpenProfiles,
            onCreateProfile = onCreateProfile,
        )

        ExclusionsCard(prefs = prefs, onOpenApps = onOpenApps)

        if (isConnected) {
            InfoStrip(
                status = status,
                externalIp = rememberExternalIp(isConnected, connectedAt),
                onOpenDetails = onOpenDetails,
            )
        }
    }
}

@Composable
private fun Hero(
    name: String,
    hostname: String,
    state: String,
    isConnected: Boolean,
    isBusy: Boolean,
    connectedAt: Long?,
    pingTarget: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (hostname.isNotEmpty()) {
            Text(
                text = hostname,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        val pulse = rememberInfiniteTransition(label = "pulse")
        val dotAlpha by pulse.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = when {
                            isConnected -> MaterialTheme.colorScheme.primary
                            isBusy -> MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha)
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
            )

            Text(
                text = stringResource(
                    when (state) {
                        STATE_CONNECTED -> R.string.state_connected
                        STATE_CONNECTING -> R.string.state_connecting
                        STATE_RECONNECTING -> R.string.state_reconnecting
                        else -> R.string.state_disconnected
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        ConnectionDiagram(
            isConnected = isConnected,
            isBusy = isBusy,
        )

        if (isConnected) {
            SessionStats(connectedAt = connectedAt, pingTarget = pingTarget)
        }

        Spacer(modifier = Modifier.height(20.dp))

        val isActive = isConnected || isBusy

        Button(
            onClick = if (isActive) onDisconnect else onConnect,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isActive) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            ),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = stringResource(
                    if (isActive) R.string.action_disconnect else R.string.action_connect
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun SessionStats(connectedAt: Long?, pingTarget: String?) {
    val ping = rememberPing(pingTarget)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(connectedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        StatTile(
            icon = Icons.Filled.NetworkPing,
            value = ping?.let { stringResource(R.string.stat_ping_value, it) } ?: "—",
            label = stringResource(R.string.stat_ping),
        )

        VerticalDivider(
            modifier = Modifier.height(28.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        StatTile(
            icon = Icons.Filled.Schedule,
            value = connectedAt?.let { formatUptime((now - it).coerceAtLeast(0)) } ?: "—",
            label = stringResource(R.string.stat_session),
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfilesStrip(
    prefs: PrefsRepository,
    activeProfile: String,
    isLocked: Boolean,
    onOpenProfiles: () -> Unit,
    onCreateProfile: () -> Unit,
) {
    // Список профилей лежит в тех же prefs под своим префиксом и меняется редко,
    // поэтому перечитываем его при каждой смене активного профиля.
    val profiles = remember(activeProfile) { readProfiles(prefs) }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.nav_profiles),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenProfiles),
            ) {
                Text(
                    text = stringResource(R.string.profiles_all),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            profiles.forEach { profile ->
                val isActive = profile.name == activeProfile

                ProfileChip(
                    profile = profile,
                    isActive = isActive,
                    isEnabled = isActive || !isLocked,
                    onClick = { applyProfile(prefs, profile.name) },
                )
            }

            AddProfileChip(onClick = onCreateProfile)
        }
    }
}

@Composable
private fun ProfileChip(
    profile: ProfileSummary,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (isEnabled) 1f else 0.4f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .background(
                color = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = isEnabled && !isActive, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = profileIconOf(profile.iconIndex),
            contentDescription = null,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            },
            modifier = Modifier.size(20.dp),
        )

        Column {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )

            if (profile.hostname.isNotEmpty()) {
                Text(
                    text = profile.hostname,
                    style = MaterialTheme.typography.bodySmall,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }

    }
}

@Composable
private fun AddProfileChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(60.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.profile_new),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExclusionsCard(prefs: PrefsRepository, onOpenApps: () -> Unit) {
    val isEnabled by prefs.booleanState(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE)
    val selected by prefs.setState(OscPrefKey.ROUTE_SELECTED_APPS)

    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .clickable(onClick = onOpenApps)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.exclusions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (isEnabled) {
                        stringResource(R.string.exclusions_on, selected.size)
                    } else {
                        stringResource(R.string.exclusions_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    // Карточка про исключения, поэтому режим списка задаём явно:
                    // в туннель идёт всё, кроме отмеченного.
                    prefs.setString(OscPrefKey.ROUTE_APP_LIST_TYPE, LIST_TYPE_DISALLOWED)
                    prefs.setBoolean(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE, it)
                },
            )
        }
    }
}

@Composable
private fun InfoStrip(
    status: ConnectionStatus,
    externalIp: String?,
    onOpenDetails: () -> Unit,
) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onOpenDetails)
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        ) {
            InfoCell(
                icon = Icons.Filled.Public,
                label = stringResource(R.string.info_external_ip),
                value = externalIp ?: "…",
                modifier = Modifier.weight(1.2f),
            )

            VerticalDivider(
                modifier = Modifier.height(30.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            InfoCell(
                icon = Icons.Filled.Dns,
                label = stringResource(R.string.info_dns),
                value = status.dnsServers.firstOrNull() ?: "—",
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(
                modifier = Modifier.height(30.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            InfoCell(
                icon = Icons.Filled.Lock,
                label = stringResource(R.string.info_protocol),
                value = status.protocol ?: "SSTP",
                modifier = Modifier.weight(0.8f),
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.sp,
                maxLines = 1,
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SurfaceCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}
