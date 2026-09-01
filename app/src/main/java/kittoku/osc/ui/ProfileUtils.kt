package kittoku.osc.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Router
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.importProfile


internal fun readProfileNames(prefs: PrefsRepository): List<String> {
    return prefs.raw.all.keys
        .filter { it.startsWith(PROFILE_KEY_HEADER) }
        .map { it.substringAfter(PROFILE_KEY_HEADER) }
        .sorted()
}

private val PROFILE_ICONS = listOf(
    Icons.Filled.Home,
    Icons.Filled.Apartment,
    Icons.Filled.Cottage,
    Icons.Filled.Router,
    Icons.Filled.Cloud,
)

/** Иконка выводится из имени, а не хранится: у профиля в апстриме нет своих полей,
 *  а разные значки нужны только чтобы отличать чипы взглядом. */
internal fun profileIcon(name: String): ImageVector {
    val index = (name.hashCode().toLong() and 0xFFFFFFFFL) % PROFILE_ICONS.size

    return PROFILE_ICONS[index.toInt()]
}

/** Профиль — это снимок всех настроек, поэтому «переключение» и есть их загрузка. */
internal fun applyProfile(prefs: PrefsRepository, name: String): Boolean {
    val serialized = prefs.raw.getString(PROFILE_KEY_HEADER + name, null) ?: return false
    val profile = deserializeProfile(serialized) ?: return false

    importProfile(profile, prefs.raw)
    setActiveProfile(prefs, name)

    return true
}

internal fun setActiveProfile(prefs: PrefsRepository, name: String) {
    prefs.setString(OscPrefKey.HOME_ACTIVE_PROFILE, name)
}

internal fun forgetProfile(prefs: PrefsRepository, name: String) {
    prefs.raw.edit { remove(PROFILE_KEY_HEADER + name) }

    if (prefs.getString(OscPrefKey.HOME_ACTIVE_PROFILE) == name) {
        setActiveProfile(prefs, "")
    }
}
