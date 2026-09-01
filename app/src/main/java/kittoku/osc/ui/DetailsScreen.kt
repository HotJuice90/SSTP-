package kittoku.osc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R
import kittoku.osc.preference.LIST_TYPE_ALLOWED


@Composable
internal fun DetailsScreen(status: ConnectionStatus) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (status.isEmpty) {
            Text(
                text = stringResource(R.string.status_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            return@Column
        }

        SurfaceCard {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                status.protocol?.also {
                    DetailsEntry(stringResource(R.string.status_protocol), listOf(it))
                }

                status.suite?.also {
                    DetailsEntry(stringResource(R.string.status_suite), listOf(it))
                }

                if (status.addresses.isNotEmpty()) {
                    DetailsEntry(stringResource(R.string.status_ip), status.addresses)
                }

                DetailsEntry(
                    title = stringResource(R.string.status_dns),
                    values = status.dnsServers.ifEmpty {
                        listOf(stringResource(R.string.status_dns_none))
                    },
                )

                val blockedSuffix = stringResource(R.string.route_blocked)
                val routes = status.routes + status.blockedRoutes.map { "$it — $blockedSuffix" }

                if (routes.isNotEmpty()) {
                    DetailsEntry(stringResource(R.string.status_routes), routes)
                }

                if (status.apps.isNotEmpty()) {
                    DetailsEntry(
                        title = stringResource(
                            if (status.appListType == LIST_TYPE_ALLOWED) {
                                R.string.list_type_allowed
                            } else {
                                R.string.list_type_disallowed
                            }
                        ),
                        values = status.apps,
                    )
                }
            }
        }
    }
}

// Подпись над значением, а не рядом: шифронабор и маршруты длиннее половины
// экрана и в две колонки рвутся посреди слова.
@Composable
private fun DetailsEntry(title: String, values: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        values.forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
