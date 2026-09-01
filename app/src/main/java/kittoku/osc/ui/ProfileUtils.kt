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
import kittoku.osc.preference.buildProfile
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.encodeProfile
import kittoku.osc.preference.importProfile


internal class ProfileSummary(val name: String, val hostname: String)

internal class ProfileFields(
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String,
)

internal fun readProfileNames(prefs: PrefsRepository): List<String> {
    return prefs.raw.all.keys
        .filter { it.startsWith(PROFILE_KEY_HEADER) }
        .map { it.substringAfter(PROFILE_KEY_HEADER) }
        .sorted()
}

internal fun readProfiles(prefs: PrefsRepository): List<ProfileSummary> {
    return readProfileNames(prefs).map {
        ProfileSummary(it, readProfileFields(prefs, it)?.hostname ?: "")
    }
}

internal fun readProfileFields(prefs: PrefsRepository, name: String): ProfileFields? {
    val serialized = prefs.raw.getString(PROFILE_KEY_HEADER + name, null) ?: return null
    val profile = deserializeProfile(serialized) ?: return null

    return ProfileFields(
        hostname = profile.stringSetting[OscPrefKey.HOME_HOSTNAME.name].orEmpty(),
        port = profile.intSetting[OscPrefKey.SSL_PORT.name] ?: 443,
        username = profile.stringSetting[OscPrefKey.HOME_USERNAME.name].orEmpty(),
        password = profile.stringSetting[OscPrefKey.HOME_PASSWORD.name].orEmpty(),
    )
}

private val PROFILE_ICONS = listOf(
    Icons.Filled.Home,
    Icons.Filled.Apartment,
    Icons.Filled.Cottage,
    Icons.Filled.Router,
    Icons.Filled.Cloud,
)

/** Иконка выводится из имени, а не хранится: у профиля в апстриме нет своих полей,
 *  а разные значки нужны только чтобы отличать записи взглядом. */
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

/**
 * Правит поля подключения поверх снимка настроек. Для нового профиля снимок берётся
 * с текущих настроек, для существующего — из него самого, чтобы правка хоста не
 * сбрасывала остальное: MTU, маршруты, исключения приложений.
 */
internal fun saveProfile(
    prefs: PrefsRepository,
    previousName: String?,
    name: String,
    hostname: String,
    port: Int,
    username: String,
    password: String,
) {
    val base = previousName
        ?.let { prefs.raw.getString(PROFILE_KEY_HEADER + it, null) }
        ?.let { deserializeProfile(it) }
        ?: buildProfile(prefs.raw)

    base.stringSetting[OscPrefKey.HOME_HOSTNAME.name] = hostname
    base.stringSetting[OscPrefKey.HOME_USERNAME.name] = username
    base.stringSetting[OscPrefKey.HOME_PASSWORD.name] = password
    base.intSetting[OscPrefKey.SSL_PORT.name] = port

    prefs.raw.edit {
        if (previousName != null && previousName != name) {
            remove(PROFILE_KEY_HEADER + previousName)
        }

        putString(PROFILE_KEY_HEADER + name, encodeProfile(base))
    }

    val wasActive = prefs.getString(OscPrefKey.HOME_ACTIVE_PROFILE) == previousName

    // Новый профиль и правка активного сразу становятся текущими настройками:
    // иначе на главной остались бы старые host и логин.
    if (previousName == null || wasActive) {
        applyProfile(prefs, name)
    }
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
