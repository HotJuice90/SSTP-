package kittoku.osc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import home.keenetic.sstp.R
import kittoku.osc.preference.OscPrefKey


@Composable
internal fun ConnectionScreen(prefs: PrefsRepository) {
    val hostname by prefs.stringState(OscPrefKey.HOME_HOSTNAME)
    val port by prefs.intState(OscPrefKey.SSL_PORT)
    val sstpPath by prefs.stringState(OscPrefKey.SSL_SSTP_PATH)
    val username by prefs.stringState(OscPrefKey.HOME_USERNAME)
    val password by prefs.stringState(OscPrefKey.HOME_PASSWORD)

    Column {
        TextSettingRow(
            title = stringResource(R.string.pref_hostname),
            value = hostname,
            placeholder = stringResource(R.string.pref_hostname_hint),
            onValueChange = { prefs.setString(OscPrefKey.HOME_HOSTNAME, it.trim()) },
        )

        IntSettingRow(
            title = stringResource(R.string.pref_port),
            value = port,
            onValueChange = { prefs.setInt(OscPrefKey.SSL_PORT, it) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_sstp_path),
            value = sstpPath,
            placeholder = stringResource(R.string.pref_sstp_path_hint),
            note = stringResource(R.string.pref_sstp_path_note),
            keyboardType = KeyboardType.Uri,
            onValueChange = { prefs.setString(OscPrefKey.SSL_SSTP_PATH, it.trim()) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_username),
            value = username,
            onValueChange = { prefs.setString(OscPrefKey.HOME_USERNAME, it.trim()) },
        )

        TextSettingRow(
            title = stringResource(R.string.pref_password),
            value = password,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            onValueChange = { prefs.setString(OscPrefKey.HOME_PASSWORD, it) },
        )
    }
}

@Composable
internal fun IntSettingRow(
    title: String,
    value: Int,
    summary: String? = null,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    TextSettingRow(
        title = title,
        value = value.toString(),
        placeholder = summary,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        note = summary,
        onValueChange = { text -> text.trim().toIntOrNull()?.also(onValueChange) },
    )
}
