package kittoku.osc.preference

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.preference.PreferenceManager
import kittoku.osc.preference.accessor.getStringPrefValue
import java.util.Locale


// Значения хранятся как есть, не переводятся — та же логика, что у LIST_TYPE_*.
internal const val THEME_SYSTEM = "system"
internal const val THEME_LIGHT = "light"
internal const val THEME_DARK = "dark"

internal const val LANGUAGE_SYSTEM = "system"
internal const val LANGUAGE_RU = "ru"
internal const val LANGUAGE_EN = "en"

internal val APP_THEMES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
internal val APP_LANGUAGES = listOf(LANGUAGE_SYSTEM, LANGUAGE_RU, LANGUAGE_EN)

/**
 * Оборачивает контекст выбранным языком приложения.
 *
 * На Android 13+ системный LocaleManager (см. [syncSystemLocale]) применяет выбор
 * сам ко всему процессу раньше, чем большинство контекстов вообще создаётся, так
 * что для них эта обёртка — просто подстраховка на случай самого первого onCreate
 * после смены языка. На версиях младше 13 своего API для языка приложения нет
 * вовсе, и это единственный механизм.
 */
internal fun wrapLocale(context: Context, language: String): Context {
    if (language == LANGUAGE_SYSTEM) return context

    val locale = Locale.forLanguageTag(language)
    Locale.setDefault(locale)

    val configuration = Configuration(context.resources.configuration).also {
        it.setLocale(locale)
        it.setLayoutDirection(locale)
    }

    return context.createConfigurationContext(configuration)
}

/**
 * Читает выбранный язык из тех же SharedPreferences и оборачивает им контекст.
 * Вызывается из attachBaseContext активностей и сервиса — до чтения не сама
 * активность/сервис, а переданный системой базовый контекст, но SharedPreferences
 * от него получить можно так же, как от готового.
 */
internal fun applyStoredLocale(context: Context): Context {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val language = getStringPrefValue(OscPrefKey.APP_LANGUAGE, prefs)

    return wrapLocale(context, language)
}

/**
 * Держит системный выбор языка приложения (Настройки → Приложения → Язык) в курсе,
 * чтобы он не расходился с выбором внутри приложения. Работает только с Android 13
 * — своего API для языка приложения на более старых версиях нет, там всё решает
 * только [wrapLocale] через attachBaseContext.
 */
internal fun syncSystemLocale(context: Context, language: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val localeManager = context.getSystemService(LocaleManager::class.java) ?: return

    localeManager.applicationLocales = if (language == LANGUAGE_SYSTEM) {
        LocaleList.getEmptyLocaleList()
    } else {
        LocaleList.forLanguageTags(language)
    }
}
