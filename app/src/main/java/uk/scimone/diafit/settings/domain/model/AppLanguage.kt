package uk.scimone.diafit.settings.domain.model

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import uk.scimone.diafit.R
import java.util.Locale

/**
 * Languages the app is translated into. English is the default resource set (`values/`), so any
 * other system language falls back to English without extra code. To add a language: a
 * `values-xx/strings.xml`, an entry in `res/xml/locales_config.xml` and one here.
 */
enum class AppLanguage(val tag: String?, @StringRes val nameRes: Int) {
    SYSTEM(null, R.string.settings_language_system),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
    ITALIAN("it", R.string.language_italian);

    companion object {
        private const val PREFS = "app_language"
        private const val KEY = "tag"

        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == tag?.substringBefore('-') } ?: SYSTEM

        /** What the user picked (the system's per-app setting on Android 13+, our own preference before). */
        fun current(context: Context): AppLanguage =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
                if (locales.isEmpty) SYSTEM else fromTag(locales[0]?.toLanguageTag())
            } else {
                fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
            }

        /**
         * Stores the choice. On Android 13+ the system recreates the activities itself; before that
         * the caller must recreate the activity (see [wrap], which applies the choice).
         */
        fun apply(context: Context, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (language.tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
            }
        }

        /** For `attachBaseContext` of the Application and activities: needed before Android 13 only. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
            val config = Configuration(base.resources.configuration)
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }

        /** Name of the language the phone itself is set to, for "System default (currently ...)". */
        fun systemLanguageName(): String {
            val locale = Locale.getDefault()
            return locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase(locale) }
        }
    }
}

internal tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
