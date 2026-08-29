package com.khubsoja.wirelesscallreceiver.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Utility class to manage and apply dynamic runtime locale changes in the app.
 * Saves user language preferences using SharedPreferences.
 */
object LocaleHelper {

    private const val PREFS_NAME = "wireless_call_receiver_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_BENGALI = "bn"
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_HINDI = "hi"

    /**
     * Checks if language selection has been performed on first launch.
     */
    fun isLanguageSet(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.contains(KEY_LANGUAGE)
    }

    /**
     * Get currently saved language code. Default is English ("en").
     */
    fun getSavedLanguage(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
    }

    /**
     * Save language code and apply locale update.
     */
    fun setLocale(context: Context, languageCode: String): Context {
        saveLanguage(context, languageCode)
        return updateResources(context, languageCode)
    }

    /**
     * Apply saved locale to context on Activity attachBaseContext.
     */
    fun applySavedLocale(context: Context): Context {
        val lang = getSavedLanguage(context)
        return updateResources(context, lang)
    }

    private fun saveLanguage(context: Context, languageCode: String) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val config: Configuration = Configuration(resources.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
            context
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
