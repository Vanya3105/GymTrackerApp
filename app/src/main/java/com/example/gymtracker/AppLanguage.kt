package com.example.gymtracker

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language, chosen on the welcome screen or in Profile; until then it follows the phone.
 * Android 13+ keeps a per-app language itself (it also shows in the phone's app settings); older
 * versions keep the choice here and apply it to each activity in [wrap].
 */
enum class AppLanguage(val tag: String, val label: String) {
    English("en", "English"),
    Hebrew("he", "עברית");

    companion object {
        private const val PREFS = "app_language"
        private const val KEY_TAG = "tag"

        fun current(context: Context): AppLanguage {
            val chosen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
            } else prefs(context).getString(KEY_TAG, null)
            return of(chosen ?: Locale.getDefault().language)
        }

        /** Switches the app to [language]; the activity restarts in it. */
        fun set(activity: Activity, language: AppLanguage) {
            if (language == current(activity)) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
            } else {
                prefs(activity).edit().putString(KEY_TAG, language.tag).commit()
                activity.recreate()
            }
        }

        /** For attachBaseContext: before Android 13, gives the activity the chosen language. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = prefs(base).getString(KEY_TAG, null) ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }

        // Java still calls Hebrew "iw" in places.
        private fun of(language: String) = if (language == "he" || language == "iw") Hebrew else English

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

/** The activity behind a Compose context, which may be wrapped (for the theme or the language). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
