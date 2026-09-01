package kittoku.osc.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.importProfile
import kittoku.osc.preference.serializeProfile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream


@Composable
internal fun ProfilesScreen(prefs: PrefsRepository) {
    val context = LocalContext.current

    // Профили лежат в тех же SharedPreferences под своим префиксом, поэтому
    // список приходится перечитывать вручную после каждого изменения.
    var profileNames by remember { mutableStateOf(readProfileNames(prefs)) }

    var isSaveDialogShown by remember { mutableStateOf(false) }
    var isExportWarningShown by remember { mutableStateOf(false) }
    var isResetDialogShown by remember { mutableStateOf(false) }
    var openedProfile by remember { mutableStateOf<String?>(null) }

    val hostname by prefs.stringState(OscPrefKey.HOME_HOSTNAME)

    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.also {
            val profile = context.contentResolver.openInputStream(it)?.let { stream ->
                BufferedInputStream(stream).use { buffered ->
                    deserializeProfile(buffered.reader(Charsets.UTF_8).readText())
                }
            }

            if (profile == null) {
                toast(R.string.profile_import_failed_toast)
            } else {
                importProfile(profile, prefs.raw)
                toast(R.string.profile_imported_toast)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.also {
            context.contentResolver.openOutputStream(it)?.also { stream ->
                BufferedOutputStream(stream).use { buffered ->
                    buffered.write(serializeProfile(prefs.raw).toByteArray(Charsets.UTF_8))
                }
            }

            toast(R.string.profile_exported_toast)
        }
    }

    Column {
        SectionTitle(stringResource(R.string.profiles_saved))

        if (profileNames.isEmpty()) {
            Text(
                text = stringResource(R.string.profiles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        profileNames.forEach { name ->
            SettingRow(title = name, onClick = { openedProfile = name })
        }

        GroupDivider()

        NavigationRow(
            title = stringResource(R.string.profile_save),
            icon = Icons.Filled.Save,
            onClick = { isSaveDialogShown = true },
        )

        NavigationRow(
            title = stringResource(R.string.profile_import),
            icon = Icons.Filled.Download,
            onClick = { importLauncher.launch(arrayOf("application/json")) },
        )

        NavigationRow(
            title = stringResource(R.string.profile_export),
            icon = Icons.Filled.Upload,
            onClick = { isExportWarningShown = true },
        )

        NavigationRow(
            title = stringResource(R.string.profile_reload_defaults),
            icon = Icons.Filled.RestartAlt,
            onClick = { isResetDialogShown = true },
        )
    }

    if (isSaveDialogShown) {
        TextInputDialog(
            title = stringResource(R.string.profile_save),
            initialValue = "",
            placeholder = hostname.ifEmpty { stringResource(R.string.profile_save_hint) },
            note = stringResource(R.string.profile_save_message),
            onDismiss = { isSaveDialogShown = false },
            onConfirm = { name ->
                val key = PROFILE_KEY_HEADER + name.ifBlank { hostname }

                prefs.raw.edit { putString(key, serializeProfile(prefs.raw)) }

                profileNames = readProfileNames(prefs)
                isSaveDialogShown = false
                toast(R.string.profile_saved_toast)
            },
        )
    }

    openedProfile?.also { name ->
        val key = PROFILE_KEY_HEADER + name
        val profile = remember(name) { prefs.raw.getString(key, null)?.let { deserializeProfile(it) } }

        if (profile == null) {
            openedProfile = null
            toast(R.string.profile_invalid_toast)
        } else {
            val summary = buildString {
                appendLine(
                    stringResource(R.string.pref_hostname) + ": " +
                        profile.stringSetting[OscPrefKey.HOME_HOSTNAME.name].orEmpty()
                )
                appendLine(
                    stringResource(R.string.pref_username) + ": " +
                        profile.stringSetting[OscPrefKey.HOME_USERNAME.name].orEmpty()
                )
                append(
                    stringResource(R.string.pref_port) + ": " +
                        (profile.intSetting[OscPrefKey.SSL_PORT.name]?.toString().orEmpty())
                )
            }

            ProfileDialog(
                name = name,
                summary = summary,
                onDismiss = { openedProfile = null },
                onLoad = {
                    importProfile(profile, prefs.raw)
                    openedProfile = null
                    toast(R.string.profile_loaded_toast)
                },
                onDelete = {
                    prefs.raw.edit { remove(key) }
                    profileNames = readProfileNames(prefs)
                    openedProfile = null
                    toast(R.string.profile_deleted_toast)
                },
            )
        }
    }

    if (isExportWarningShown) {
        ConfirmDialog(
            message = stringResource(R.string.profile_export_warning),
            confirmLabel = stringResource(R.string.action_proceed),
            onDismiss = { isExportWarningShown = false },
            onConfirm = {
                isExportWarningShown = false
                exportLauncher.launch(hostname.ifEmpty { "profile" } + ".json")
            },
        )
    }

    if (isResetDialogShown) {
        ConfirmDialog(
            message = stringResource(R.string.profile_reload_message),
            confirmLabel = stringResource(R.string.action_reset),
            onDismiss = { isResetDialogShown = false },
            onConfirm = {
                importProfile(null, prefs.raw)
                isResetDialogShown = false
                toast(R.string.defaults_reloaded_toast)
            },
        )
    }
}

@Composable
private fun ProfileDialog(
    name: String,
    summary: String,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { Text(summary) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onLoad) {
                Text(stringResource(R.string.profile_load))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.profile_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

private fun readProfileNames(prefs: PrefsRepository): List<String> {
    return prefs.raw.all.keys
        .filter { it.startsWith(PROFILE_KEY_HEADER) }
        .map { it.substringAfter(PROFILE_KEY_HEADER) }
        .sorted()
}
