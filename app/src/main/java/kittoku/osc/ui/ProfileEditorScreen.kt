package kittoku.osc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey


private const val DEFAULT_PORT = 443

/**
 * Профиль в апстриме — это снимок всех настроек под своим ключом, поэтому редактор
 * правит поля подключения поверх снимка, не трогая остальные настройки профиля.
 * Поля нового профиля пустые: подставлять данные активного профиля значит путать
 * пользователя чужим сервером и чужим паролем.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileEditorScreen(
    prefs: PrefsRepository,
    editedProfile: String?,
    onDone: () -> Unit,
) {
    val existing = remember(editedProfile) { editedProfile?.let { readProfileFields(prefs, it) } }

    var name by remember { mutableStateOf(editedProfile ?: "") }
    var host by remember { mutableStateOf(existing?.hostname.orEmpty()) }
    var port by remember { mutableStateOf((existing?.port ?: DEFAULT_PORT).toString()) }
    var sstpPath by remember { mutableStateOf(existing?.sstpPath.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var isPasswordShown by remember { mutableStateOf(false) }
    var iconIndex by remember {
        mutableStateOf(editedProfile?.let { readProfileIcon(prefs, it) } ?: 0)
    }

    val isValid = host.isNotBlank()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text(stringResource(R.string.profile_name)) },
            placeholder = { Text(stringResource(R.string.profile_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            singleLine = true,
            label = { Text(stringResource(R.string.pref_hostname)) },
            placeholder = { Text(stringResource(R.string.pref_hostname_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { char -> char.isDigit() } },
            singleLine = true,
            label = { Text(stringResource(R.string.pref_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = sstpPath,
            onValueChange = { sstpPath = it },
            singleLine = true,
            label = { Text(stringResource(R.string.pref_sstp_path)) },
            placeholder = { Text(stringResource(R.string.pref_sstp_path_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            label = { Text(stringResource(R.string.pref_username)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(stringResource(R.string.pref_password)) },
            visualTransformation = if (isPasswordShown) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { isPasswordShown = !isPasswordShown }) {
                    Icon(
                        imageVector = if (isPasswordShown) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = stringResource(
                            if (isPasswordShown) R.string.hide_password else R.string.show_password
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.profile_icon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PROFILE_ICONS.forEachIndexed { index, icon ->
                val isSelected = index == iconIndex

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = CircleShape,
                        )
                        .clickable { iconIndex = index },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Text(
            text = stringResource(
                if (editedProfile == null) {
                    R.string.profile_editor_note_new
                } else {
                    R.string.profile_editor_note
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            enabled = isValid,
            onClick = {
                saveProfile(
                    prefs = prefs,
                    previousName = editedProfile,
                    name = name.trim().ifEmpty { host.trim() },
                    hostname = host.trim(),
                    port = port.toIntOrNull() ?: DEFAULT_PORT,
                    sstpPath = sstpPath.trim(),
                    username = username.trim(),
                    password = password,
                    iconIndex = iconIndex,
                )

                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
