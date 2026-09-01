package kittoku.osc.ui

import android.content.SharedPreferences
import android.net.Uri
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.accessor.getBooleanPrefValue
import kittoku.osc.preference.accessor.getIntPrefValue
import kittoku.osc.preference.accessor.getSetPrefValue
import kittoku.osc.preference.accessor.getStringPrefValue
import kittoku.osc.preference.accessor.getURIPrefValue
import kittoku.osc.preference.accessor.setBooleanPrefValue
import kittoku.osc.preference.accessor.setIntPrefValue
import kittoku.osc.preference.accessor.setSetPrefValue
import kittoku.osc.preference.accessor.setStringPrefValue
import kittoku.osc.preference.accessor.setURIPrefValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate


/**
 * Обёртка над SharedPreferences для Compose.
 *
 * Источник истины остаётся один — те же SharedPreferences и те же акцессоры,
 * которыми пользуется протокольный слой: он читает их синхронно в конструкторах,
 * и переезд на DataStore потянул бы правки в файлах, которые трогать нельзя.
 * UI просто подписывается на изменения.
 */
internal class PrefsRepository(private val prefs: SharedPreferences) {
    private fun <T> flowOf(key: OscPrefKey, read: () -> T): Flow<T> = callbackFlow {
        trySend(read())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key.name) {
                trySend(read())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    internal fun booleanFlow(key: OscPrefKey) = flowOf(key) { getBoolean(key) }
    internal fun intFlow(key: OscPrefKey) = flowOf(key) { getInt(key) }
    internal fun stringFlow(key: OscPrefKey) = flowOf(key) { getString(key) }
    internal fun setFlow(key: OscPrefKey) = flowOf(key) { getSet(key) }
    internal fun uriFlow(key: OscPrefKey) = flowOf(key) { getUri(key) }

    internal fun getBoolean(key: OscPrefKey) = getBooleanPrefValue(key, prefs)
    internal fun getInt(key: OscPrefKey) = getIntPrefValue(key, prefs)
    internal fun getString(key: OscPrefKey) = getStringPrefValue(key, prefs)
    internal fun getSet(key: OscPrefKey) = getSetPrefValue(key, prefs)
    internal fun getUri(key: OscPrefKey) = getURIPrefValue(key, prefs)

    internal fun setBoolean(key: OscPrefKey, value: Boolean) = setBooleanPrefValue(value, key, prefs)
    internal fun setInt(key: OscPrefKey, value: Int) = setIntPrefValue(value, key, prefs)
    internal fun setString(key: OscPrefKey, value: String) = setStringPrefValue(value, key, prefs)
    internal fun setSet(key: OscPrefKey, value: Set<String>) = setSetPrefValue(value, key, prefs)
    internal fun setUri(key: OscPrefKey, value: Uri?) = setURIPrefValue(value, key, prefs)

    internal val raw: SharedPreferences
        get() = prefs
}
