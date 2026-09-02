package kittoku.osc.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Villa
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import kittoku.osc.preference.OscPrefKey
import kittoku.osc.preference.PROFILE_KEY_HEADER
import kittoku.osc.preference.buildProfile
import kittoku.osc.preference.deserializeProfile
import kittoku.osc.preference.encodeProfile
import kittoku.osc.preference.importProfile


// Значок профиля хранится своим ключом рядом с самим профилем: формат профиля
// пришёл из апстрима и своих полей под UI не имеет.
private const val PROFILE_ICON_HEADER = "PROFILE_ICON."
private const val PROFILE_USED_HEADER = "PROFILE_USED."

// Дописывать только в конец: индекс уже сохранён у существующих профилей.
internal val PROFILE_ICONS = listOf(
    Icons.Filled.Home,
    Icons.Filled.Apartment,
    Icons.Filled.Cottage,
    Icons.Filled.Router,
    Icons.Filled.Cloud,
    Icons.Filled.Villa,
    Icons.Filled.Warehouse,
    Icons.Filled.Work,
    Icons.Filled.Storage,
    Icons.Filled.Lan,
    Icons.Filled.Public,
    Icons.Filled.Bolt,
    Icons.Filled.Star,
    Icons.Filled.Favorite,
    Icons.Filled.Pets,
    Icons.Filled.DirectionsCar,
    Icons.Filled.SportsEsports,
)

internal class ProfileSummary(
    val name: String,
    val hostname: String,
    val iconIndex: Int,
)

internal class ProfileFields(
    val hostname: String,
    val port: Int,
    val sstpPath: String,
    val username: String,
    val password: String,
)

internal fun profileIconOf(index: Int): ImageVector {
    return PROFILE_ICONS[index.coerceIn(PROFILE_ICONS.indices)]
}

internal fun readProfileNames(prefs: PrefsRepository): List<String> {
    return prefs.raw.all.keys
        .filter { it.startsWith(PROFILE_KEY_HEADER) }
        .map { it.substringAfter(PROFILE_KEY_HEADER) }
        .sorted()
}

/** Сверху тот, которым пользовались последним: к нему же чаще всего и возвращаются. */
internal fun readProfiles(prefs: PrefsRepository): List<ProfileSummary> {
    return readProfileNames(prefs).map {
        ProfileSummary(
            name = it,
            hostname = readProfileFields(prefs, it)?.hostname ?: "",
            iconIndex = readProfileIcon(prefs, it),
        )
    }.sortedWith(
        compareByDescending<ProfileSummary> { readProfileUsedAt(prefs, it.name) }
            .thenBy { it.name.lowercase() }
    )
}

internal fun readProfileUsedAt(prefs: PrefsRepository, name: String): Long {
    return prefs.raw.getLong(PROFILE_USED_HEADER + name, 0L)
}

internal fun touchProfile(prefs: PrefsRepository, name: String) {
    if (name.isEmpty()) return

    prefs.raw.edit { putLong(PROFILE_USED_HEADER + name, System.currentTimeMillis()) }
}

internal fun readProfileIcon(prefs: PrefsRepository, name: String): Int {
    return prefs.raw.getInt(PROFILE_ICON_HEADER + name, 0)
}

internal fun readProfileFields(prefs: PrefsRepository, name: String): ProfileFields? {
    val serialized = prefs.raw.getString(PROFILE_KEY_HEADER + name, null) ?: return null
    val profile = deserializeProfile(serialized) ?: return null

    return ProfileFields(
        hostname = profile.stringSetting[OscPrefKey.HOME_HOSTNAME.name].orEmpty(),
        port = profile.intSetting[OscPrefKey.SSL_PORT.name] ?: 443,
        sstpPath = profile.stringSetting[OscPrefKey.SSL_SSTP_PATH.name].orEmpty(),
        username = profile.stringSetting[OscPrefKey.HOME_USERNAME.name].orEmpty(),
        password = profile.stringSetting[OscPrefKey.HOME_PASSWORD.name].orEmpty(),
    )
}

/** Профиль — это снимок всех настроек, поэтому «переключение» и есть их загрузка. */
internal fun applyProfile(prefs: PrefsRepository, name: String): Boolean {
    val serialized = prefs.raw.getString(PROFILE_KEY_HEADER + name, null) ?: return false
    val profile = deserializeProfile(serialized) ?: return false

    importProfile(profile, prefs.raw)
    setActiveProfile(prefs, name)
    touchProfile(prefs, name)

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
    sstpPath: String,
    username: String,
    password: String,
    iconIndex: Int,
) {
    val base = previousName
        ?.let { prefs.raw.getString(PROFILE_KEY_HEADER + it, null) }
        ?.let { deserializeProfile(it) }
        ?: buildProfile(prefs.raw)

    base.stringSetting[OscPrefKey.HOME_HOSTNAME.name] = hostname
    base.stringSetting[OscPrefKey.HOME_USERNAME.name] = username
    base.stringSetting[OscPrefKey.HOME_PASSWORD.name] = password
    base.intSetting[OscPrefKey.SSL_PORT.name] = port
    base.stringSetting[OscPrefKey.SSL_SSTP_PATH.name] = sstpPath

    prefs.raw.edit {
        if (previousName != null && previousName != name) {
            remove(PROFILE_KEY_HEADER + previousName)
            remove(PROFILE_ICON_HEADER + previousName)
            remove(PROFILE_USED_HEADER + previousName)
        }

        putString(PROFILE_KEY_HEADER + name, encodeProfile(base))
        putInt(PROFILE_ICON_HEADER + name, iconIndex)
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
    prefs.raw.edit {
        remove(PROFILE_KEY_HEADER + name)
        remove(PROFILE_ICON_HEADER + name)
        remove(PROFILE_USED_HEADER + name)
    }

    if (prefs.getString(OscPrefKey.HOME_ACTIVE_PROFILE) == name) {
        setActiveProfile(prefs, "")
    }
}
