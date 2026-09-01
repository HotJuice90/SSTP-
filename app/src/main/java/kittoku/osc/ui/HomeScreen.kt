package kittoku.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    onOpenApps: () -> Unit,
    onOpenDetails: () -> Unit,
    onOpenConnection: () -> Unit,
) {
    val isConnected = state == STATE_CONNECTED
    val isBusy = state == STATE_CONNECTING || state == STATE_RECONNECTING

    val activeProfile by prefs.stringState(OscPrefKey.HOME_ACTIVE_PROFILE)
    val title = activeProfile.ifEmpty { hostname }.ifEmpty { stringResource(R.string.no_host) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ConnectionCard(
            title = title,
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
        )

        SplitTunnelingCard(
            prefs = prefs,
            isConnected = isConnected,
            onOpenApps = onOpenApps,
        )

        if (isConnected) {
            InfoStrip(status = status, onOpenDetails = onOpenDetails)
        } else {
            TipCard(
                text = if (hostname.isEmpty()) {
                    stringResource(R.string.tip_no_server)
                } else {
                    stringResource(R.string.tip_connect, title)
                },
                onClick = if (hostname.isEmpty()) onOpenConnection else null,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    title: String,
    state: String,
    isConnected: Boolean,
    isBusy: Boolean,
    connectedAt: Long?,
    pingTarget: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SurfaceCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            ConnectionDiagram(isConnected = isConnected, isBusy = isBusy)

            if (isConnected) {
                SessionStats(connectedAt = connectedAt, pingTarget = pingTarget)
            }

            Button(
                onClick = if (isConnected || isBusy) onDisconnect else onConnect,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
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
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        StatTile(
            icon = Icons.Filled.Speed,
            value = ping?.let { stringResource(R.string.stat_ping_value, it) } ?: "—",
            label = stringResource(R.string.stat_ping),
        )

        VerticalDivider(
            modifier = Modifier.height(36.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        StatTile(
            icon = Icons.Filled.Timer,
            value = connectedAt?.let { formatUptime((now - it).coerceAtLeast(0)) } ?: "—",
            label = stringResource(R.string.stat_session),
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfilesStrip(
    prefs: PrefsRepository,
    activeProfile: String,
    onOpenProfiles: () -> Unit,
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

            AddProfileChip(onClick = onOpenProfiles)
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
            contentDescription = stringResource(R.string.profile_save),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SplitTunnelingCard(
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
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (isEnabled) R.string.split_on_title else R.string.split_off_title
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = if (isEnabled) {
                            stringResource(R.string.apps_selected_count, selected.size)
                        } else {
                            stringResource(R.string.split_off_summary)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = stringResource(if (isEnabled) R.string.split_on else R.string.split_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun TipCard(text: String, onClick: (() -> Unit)?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Column {
                Text(
                    text = stringResource(R.string.tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
