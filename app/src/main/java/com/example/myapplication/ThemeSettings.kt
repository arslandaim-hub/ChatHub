package com.example.myapplication

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class MessageTheme(
    val displayName: String,
    val backgroundColor: Color,
    val contentColor: Color
) {
    DEFAULT("Default", Color.Transparent, Color.Unspecified),
    LIGHT_BLUE("Light Blue", Color(0xFFE3F2FD), Color(0xFF0D47A1)),
    LIGHT_GREEN("Light Green", Color(0xFFE8F5E9), Color(0xFF1B5E20)),
    LIGHT_YELLOW("Light Yellow", Color(0xFFFFFDE7), Color(0xFFF57F17)),
    SOFT_PINK("Soft Pink", Color(0xFFFCE4EC), Color(0xFF880E4F)),
    DARK_MODE("Dark Slate", Color(0xFF263238), Color(0xFFECEFF1))
}

enum class AppBackgroundTheme(val displayName: String, val type: AppBackgroundType) {
    NONE("No Background", AppBackgroundType.NONE),
    STARRY_NIGHT("Starry Night", AppBackgroundType.STARS),
    OCEAN_WAVES("Ocean Waves", AppBackgroundType.WAVES),
    AURORA("Aurora", AppBackgroundType.AURORA)
}

enum class AppBackgroundType {
    NONE, STARS, WAVES, AURORA
}

enum class DateFormatPreference(val displayName: String, val pattern: String) {
    UK("UK (DD/MM/YYYY)", "dd/MM/yyyy"),
    US("US (MM/DD/YYYY)", "MM/dd/yyyy")
}

class ThemeManager(private val context: Context) {
    private val messageThemeKey = stringPreferencesKey("message_theme")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val chatLockEnabledKey = booleanPreferencesKey("chat_lock_enabled")
    private val chatLockPinKey = stringPreferencesKey("chat_lock_pin")
    private val isFingerprintEnabledKey = booleanPreferencesKey("is_fingerprint_enabled")
    private val securityQuestionKey = stringPreferencesKey("security_question")
    private val securityAnswerKey = stringPreferencesKey("security_answer")
    private val dateFormatKey = stringPreferencesKey("date_format")

    val selectedMessageTheme: Flow<MessageTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[messageThemeKey] ?: MessageTheme.DEFAULT.name
        try {
            MessageTheme.valueOf(themeName)
        } catch (e: Exception) {
            MessageTheme.DEFAULT
        }
    }

    val selectedAppTheme: Flow<AppBackgroundTheme> = context.dataStore.data.map { preferences ->
        val themeName = preferences[appThemeKey] ?: AppBackgroundTheme.NONE.name
        try {
            AppBackgroundTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppBackgroundTheme.NONE
        }
    }

    val selectedDateFormat: Flow<DateFormatPreference> = context.dataStore.data.map { preferences ->
        val formatName = preferences[dateFormatKey] ?: DateFormatPreference.UK.name
        try {
            DateFormatPreference.valueOf(formatName)
        } catch (e: Exception) {
            DateFormatPreference.UK
        }
    }

    val isChatLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[chatLockEnabledKey] ?: false
    }

    val chatLockPin: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[chatLockPinKey]
    }

    val isFingerprintEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[isFingerprintEnabledKey] ?: false
    }

    val securityQuestion: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[securityQuestionKey]
    }

    val securityAnswer: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[securityAnswerKey]
    }

    suspend fun setMessageTheme(theme: MessageTheme) {
        context.dataStore.edit { preferences ->
            preferences[messageThemeKey] = theme.name
        }
    }

    suspend fun setAppTheme(theme: AppBackgroundTheme) {
        context.dataStore.edit { preferences ->
            preferences[appThemeKey] = theme.name
        }
    }

    suspend fun setChatLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[chatLockEnabledKey] = enabled
        }
    }

    suspend fun setChatLockPin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[chatLockPinKey] = pin
        }
    }

    suspend fun setFingerprintEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[isFingerprintEnabledKey] = enabled
        }
    }

    suspend fun setSecurityQuestion(question: String) {
        context.dataStore.edit { preferences ->
            preferences[securityQuestionKey] = question
        }
    }

    suspend fun setSecurityAnswer(answer: String) {
        context.dataStore.edit { preferences ->
            preferences[securityAnswerKey] = answer
        }
    }

    suspend fun setDateFormat(format: DateFormatPreference) {
        context.dataStore.edit { preferences ->
            preferences[dateFormatKey] = format.name
        }
    }
}
