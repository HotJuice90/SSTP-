package kittoku.osc.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
            onOpenProfiles = onOpenProfiles,
            onCreateProfile = onCreateProfile,
        )

        ExclusionsCard(
            prefs = prefs,
            isConnected = isConnected,
            onOpenApps = onOpenApps,
        )

        if (isConnected) {
            InfoStrip(status = status, onOpenDetails = onOpenDetails)
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
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
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = when {
                            isConnected -> MaterialTheme.colorScheme.primary
                            isBusy -> MaterialTheme.colorScheme.tertiary
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
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (isConnected) {
            SessionStats(connectedAt = connectedAt, pingTarget = pingTarget)
        }

        Spacer(modifier = Modifier.height(if (isConnected) 12.dp else 4.dp))

        Button(
            onClick = if (isConnected || isBusy) onDisconnect else onConnect,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = stringResource(
                    if (isConnected || isBusy) R.string.action_disconnect else R.string.action_connect
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
        modifier = Modifier.padding(top = 4.dp),
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
            tint = MaterialTheme.colorScheme.primary,
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
    onOpenProfiles: () -> Unit,
    onCreateProfile: () -> Unit,
) {
    // Список профилей лежит в тех же prefs под своим префиксом и меняется редко,
    // поэтому перечитываем его при каждой смене активного профиля.
    val names = remember(activeProfile) { readProfileNames(prefs) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    color = MaterialTheme.colorScheme.primary,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            names.forEach { name ->
                ProfileChip(
                    name = name,
                    isActive = name == activeProfile,
                    onClick = { applyProfile(prefs, name) },
                )
            }

            AddProfileChip(onClick = onCreateProfile)
        }
    }
}

@Composable
private fun ProfileChip(name: String, isActive: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(
                color = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = profileIcon(name),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )

        Text(text = name, style = MaterialTheme.typography.bodyLarge)

        if (isActive) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddProfileChip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.profile_new),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExclusionsCard(
    prefs: PrefsRepository,
    isConnected: Boolean,
    onOpenApps: () -> Unit,
) {
    val isEnabled by prefs.booleanState(OscPrefKey.ROUTE_DO_ENABLE_APP_BASED_RULE)
    val selected by prefs.setState(OscPrefKey.ROUTE_SELECTED_APPS)

    SurfaceCard {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .clickable(onClick = onOpenApps)
                    .padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )

                Text(
                    text = stringResource(
                        if (isConnected) R.string.vpn_active_note else R.string.vpn_inactive_note
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoStrip(status: ConnectionStatus, onOpenDetails: () -> Unit) {
    SurfaceCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onOpenDetails)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            InfoCell(
                icon = Icons.Filled.Public,
                label = stringResource(R.string.info_ip),
                value = status.addresses.firstOrNull() ?: "—",
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            InfoCell(
                icon = Icons.Filled.Dns,
                label = stringResource(R.string.info_dns),
                value = status.dnsServers.firstOrNull() ?: "—",
                modifier = Modifier.weight(1f),
            )

            VerticalDivider(
                modifier = Modifier.height(32.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            InfoCell(
                icon = Icons.Filled.Lock,
                label = stringResource(R.string.info_protocol),
                value = status.protocol?.let { "SSTP / $it" } ?: "SSTP",
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                modifier = Modifier.size(14.dp),
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SurfaceCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}
