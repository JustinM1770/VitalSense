package mx.ita.vitalsense.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettings {
    private const val PREFS_NAME = "vitalsense_app_settings"
    private const val KEY_LANGUAGE = "language_code"
    private const val KEY_THEME = "theme_mode"
    private const val DEFAULT_LANGUAGE = "es"
    private const val DEFAULT_THEME = "light"

    private val _languageFlow = MutableStateFlow(DEFAULT_LANGUAGE)
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(DEFAULT_THEME)
    val themeFlow: StateFlow<String> = _themeFlow.asStateFlow()

    fun applySavedPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasLanguage = prefs.contains(KEY_LANGUAGE)
        val hasTheme = prefs.contains(KEY_THEME)
        val language = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        val theme = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME

        if (!hasLanguage || !hasTheme) {
            prefs.edit()
                .putString(KEY_LANGUAGE, language)
                .putString(KEY_THEME, theme)
                .apply()
        }

        _languageFlow.value = language
        _themeFlow.value = theme
        applyLanguage(language)
        applyTheme(theme)
    }

    fun setLanguage(context: Context, languageCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .apply()
        _languageFlow.value = languageCode
        applyLanguage(languageCode)
    }

    fun setTheme(context: Context, themeMode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, themeMode)
            .apply()
        _themeFlow.value = themeMode
        applyTheme(themeMode)
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun getSavedTheme(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    private fun applyLanguage(languageCode: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }

    private fun applyTheme(themeMode: String) {
        val mode = when (themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
