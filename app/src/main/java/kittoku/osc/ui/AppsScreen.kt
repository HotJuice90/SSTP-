package kittoku.osc.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.getInstalledAppInfos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class AppEntry(
    val packageName: String,
    val label: String,
    val info: ApplicationInfo,
)

@Composable
internal fun AppsScreen(prefs: PrefsRepository) {
    val context = LocalContext.current
    val pm = remember { context.applicationContext.packageManager }

    val doShowBackgroundApps by prefs.booleanState(OscPrefKey.ROUTE_DO_SHOW_BACKGROUND_APPS)
    val selected by prefs.setState(OscPrefKey.ROUTE_SELECTED_APPS)

    var query by remember { mutableStateOf("") }

    // Список приложений строится в фоне: getInstalledApplications с иконками и
    // подписями на пару сотен пакетов заметно тормозит главный поток.
    val apps by produceState(initialValue = emptyList<AppEntry>(), doShowBackgroundApps) {
        value = withContext(Dispatchers.Default) {
            getInstalledAppInfos(doShowBackgroundApps, pm).map {
                AppEntry(it.packageName, pm.getApplicationLabel(it).toString(), it)
            }.sortedBy { it.label.lowercase() }
        }
    }

    val shown = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.apps_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SwitchRow(
            title = stringResource(R.string.apps_show_background),
            checked = doShowBackgroundApps,
            onCheckedChange = { prefs.setBoolean(OscPrefKey.ROUTE_DO_SHOW_BACKGROUND_APPS, it) },
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            TextButton(
                onClick = {
                    prefs.setSet(
                        OscPrefKey.ROUTE_SELECTED_APPS,
                        selected + shown.map { it.packageName },
                    )
                },
            ) { Text(stringResource(R.string.apps_select_all)) }

            TextButton(
                onClick = {
                    prefs.setSet(
                        OscPrefKey.ROUTE_SELECTED_APPS,
                        selected - shown.map { it.packageName }.toSet(),
                    )
                },
            ) { Text(stringResource(R.string.apps_unselect_all)) }
        }

        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.apps_nothing_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            return@Column
        }

        LazyColumn {
            items(shown, key = { it.packageName }) { app ->
                val isChecked = app.packageName in selected

                fun toggle() {
                    prefs.setSet(
                        OscPrefKey.ROUTE_SELECTED_APPS,
                        if (isChecked) selected - app.packageName else selected + app.packageName,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggle() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AppIcon(app, pm.hashCode())

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)

                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Checkbox(checked = isChecked, onCheckedChange = { toggle() })
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry, cacheKey: Int) {
    val context = LocalContext.current

    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, app.packageName, cacheKey) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(app.info).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }

    val icon = bitmap

    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}
