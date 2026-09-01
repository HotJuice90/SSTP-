package kittoku.osc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.BuildConfig
import home.keenetic.sstp.R
import kittoku.osc.preference.LIST_TYPE_ALLOWED
import kittoku.osc.preference.STATE_CONNECTED
import kittoku.osc.preference.STATE_CONNECTING
import kittoku.osc.preference.STATE_RECONNECTING
import kotlinx.coroutines.delay


@Composable
internal fun HomeScreen(
    state: String,
    hostname: String,
    status: ConnectionStatus,
    connectedAt: Long?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    Column {
        StateCard(
            state = state,
            hostname = hostname,
            connectedAt = connectedAt,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        NavigationRow(
            title = stringResource(R.string.nav_connection),
            summary = hostname.ifEmpty { stringResource(R.string.no_host) },
            icon = Icons.Filled.Dns,
            onClick = onOpenConnection,
        )

        NavigationRow(
            title = stringResource(R.string.nav_settings),
            icon = Icons.Filled.Tune,
            onClick = onOpenSettings,
        )

        NavigationRow(
            title = stringResource(R.string.nav_profiles),
            icon = Icons.Filled.FolderSpecial,
            onClick = onOpenProfiles,
        )

        GroupDivider()

        StatusCard(status)

        Text(
            text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun StateCard(
    state: String,
    hostname: String,
    connectedAt: Long?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = state == STATE_CONNECTED
    val isBusy = state == STATE_CONNECTING || state == STATE_RECONNECTING

    val containerColor by animateColorAsState(
        targetValue = when {
            isConnected -> MaterialTheme.colorScheme.primaryContainer
            isBusy -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "stateCardColor",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = stringResource(
                    when (state) {
                        STATE_CONNECTED -> R.string.state_connected
                        STATE_CONNECTING -> R.string.state_connecting
                        STATE_RECONNECTING -> R.string.state_reconnecting
                        else -> R.string.state_disconnected
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
            )

            Text(
                text = hostname.ifEmpty { stringResource(R.string.no_host) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isConnected && connectedAt != null) {
                Uptime(connectedAt)
            }

            Button(
                onClick = if (isConnected || isBusy) onDisconnect else onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (isConnected || isBusy) R.string.action_disconnect else R.string.action_connect
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun Uptime(connectedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(connectedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Text(
        text = stringResource(R.string.uptime, formatUptime((now - connectedAt).coerceAtLeast(0))),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusCard(status: ConnectionStatus) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.status_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (status.isEmpty) {
            Text(
                text = stringResource(R.string.status_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            return@Column
        }

        status.protocol?.also { StatusEntry(stringResource(R.string.status_protocol), listOf(it)) }
        status.suite?.also { StatusEntry(stringResource(R.string.status_suite), listOf(it)) }

        if (status.addresses.isNotEmpty()) {
            StatusEntry(stringResource(R.string.status_ip), status.addresses)
        }

        StatusEntry(
            title = stringResource(R.string.status_dns),
            values = status.dnsServers.ifEmpty { listOf(stringResource(R.string.status_dns_none)) },
        )

        if (status.routes.isNotEmpty()) {
            StatusEntry(stringResource(R.string.status_routes), status.routes)
        }

        if (status.apps.isNotEmpty()) {
            val listTypeTitle = stringResource(
                if (status.appListType == LIST_TYPE_ALLOWED) {
                    R.string.list_type_allowed
                } else {
                    R.string.list_type_disallowed
                }
            )

            StatusEntry(listTypeTitle, status.apps)
        }
    }
}

@Composable
private fun StatusEntry(title: String, values: List<String>) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )

        Column(modifier = Modifier.weight(0.58f)) {
            values.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
