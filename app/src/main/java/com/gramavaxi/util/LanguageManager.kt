package com.gramavaxi.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "grama_vaxi_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    const val ENGLISH = "en"
    const val KANNADA = "kn"

    fun wrapContext(context: Context): Context {
        val languageTag = getSavedLanguage(context)
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    fun applySavedLanguage(context: Context) {
        applyLanguage(getSavedLanguage(context))
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, ENGLISH) ?: ENGLISH
    }

    fun setLanguage(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
        applyLanguage(languageTag)
    }

    fun positionFor(languageTag: String): Int = if (languageTag == KANNADA) 1 else 0

    fun languageFor(position: Int): String = if (position == 1) KANNADA else ENGLISH

    private fun applyLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
