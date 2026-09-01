package kittoku.osc.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kittoku.osc.preference.OscPrefKey


// Мост между репозиторием и Compose: значение читается синхронно, дальше экран
// живёт на подписке. Протокольный слой продолжает читать те же ключи напрямую.

@Composable
internal fun PrefsRepository.booleanState(key: OscPrefKey): State<Boolean> =
    remember(key) { booleanFlow(key) }.collectAsState(initial = getBoolean(key))

@Composable
internal fun PrefsRepository.intState(key: OscPrefKey): State<Int> =
    remember(key) { intFlow(key) }.collectAsState(initial = getInt(key))

@Composable
internal fun PrefsRepository.stringState(key: OscPrefKey): State<String> =
    remember(key) { stringFlow(key) }.collectAsState(initial = getString(key))

@Composable
internal fun PrefsRepository.setState(key: OscPrefKey): State<Set<String>> =
    remember(key) { setFlow(key) }.collectAsState(initial = getSet(key))

@Composable
internal fun PrefsRepository.uriState(key: OscPrefKey): State<Uri?> =
    remember(key) { uriFlow(key) }.collectAsState(initial = getUri(key))
