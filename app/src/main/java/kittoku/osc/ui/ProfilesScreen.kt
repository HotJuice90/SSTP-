package kittoku.osc.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.importProfile
import kittoku.osc.preference.serializeProfile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream


@Composable
internal fun ProfilesScreen(
    prefs: PrefsRepository,
    onCreateProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
) {
    val context = LocalContext.current

    val activeProfile by prefs.stringState(OscPrefKey.HOME_ACTIVE_PROFILE)

    // Профили лежат в тех же SharedPreferences под своим префиксом, а не в
    // наблюдаемом ключе, поэтому список перечитывается по счётчику изменений.
    var revision by remember { mutableIntStateOf(0) }
    val profiles = remember(revision, activeProfile) { readProfiles(prefs) }

    var isExportWarningShown by remember { mutableStateOf(false) }
    var isResetDialogShown by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<String?>(null) }

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
                revision++
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
        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.profiles_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                isActive = profile.name == activeProfile,
                onApply = {
                    applyProfile(prefs, profile.name)
                    toast(R.string.profile_loaded_toast)
                },
                onEdit = { onEditProfile(profile.name) },
                onDelete = { profileToDelete = profile.name },
            )
        }

        GroupDivider()

        NavigationRow(
            title = stringResource(R.string.profile_new),
            icon = Icons.Filled.Add,
            onClick = onCreateProfile,
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

    profileToDelete?.also { name ->
        ConfirmDialog(
            title = name,
            message = stringResource(R.string.profile_delete_message),
            confirmLabel = stringResource(R.string.profile_delete),
            onDismiss = { profileToDelete = null },
            onConfirm = {
                forgetProfile(prefs, name)
                profileToDelete = null
                revision++
                toast(R.string.profile_deleted_toast)
            },
        )
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
                revision++
                toast(R.string.defaults_reloaded_toast)
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileSummary,
    isActive: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var isMenuShown by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Icon(
            imageVector = profileIcon(profile.name),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            )

            Text(
                text = profile.hostname.ifEmpty { stringResource(R.string.no_host) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isActive) {
            Text(
                text = stringResource(R.string.profile_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box {
            IconButton(onClick = { isMenuShown = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }

            DropdownMenu(expanded = isMenuShown, onDismissRequest = { isMenuShown = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_load)) },
                    onClick = {
                        isMenuShown = false
                        onApply()
                    },
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        isMenuShown = false
                        onEdit()
                    },
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.profile_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        isMenuShown = false
                        onDelete()
                    },
                )
            }
        }
    }
}
