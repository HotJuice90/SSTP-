package kittoku.osc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey


/**
 * Профиль в апстриме — это снимок всех настроек под своим ключом, поэтому редактор
 * правит пять полей подключения поверх снимка, не трогая остальные настройки
 * профиля. Новый профиль берёт за основу текущие настройки приложения.
 */
@Composable
internal fun ProfileEditorScreen(
    prefs: PrefsRepository,
    editedProfile: String?,
    onDone: () -> Unit,
) {
    val existing = remember(editedProfile) { editedProfile?.let { readProfileFields(prefs, it) } }

    var name by remember { mutableStateOf(editedProfile ?: "") }
    var host by remember {
        mutableStateOf(existing?.hostname ?: prefs.getString(OscPrefKey.HOME_HOSTNAME))
    }
    var port by remember {
        mutableStateOf((existing?.port ?: prefs.getInt(OscPrefKey.SSL_PORT)).toString())
    }
    var username by remember {
        mutableStateOf(existing?.username ?: prefs.getString(OscPrefKey.HOME_USERNAME))
    }
    var password by remember {
        mutableStateOf(existing?.password ?: prefs.getString(OscPrefKey.HOME_PASSWORD))
    }

    val isValid = name.isNotBlank() || host.isNotBlank()

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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.profile_editor_note),
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
                    port = port.toIntOrNull() ?: 443,
                    username = username.trim(),
                    password = password,
                )

                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}
