package com.example.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "droplink_settings")

data class UserSettings(
    val deviceId: String = "",
    val deviceName: String = Build.MODEL ?: "Android Device",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.AR,
    val requireApproval: Boolean = true,
    val autoAcceptTrusted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val sessionTimeoutMinutes: Int = 15,
    val hideDeviceWhenIdle: Boolean = false,
    val customSaveTreeUri: String? = null,
    val customSaveFolderLabel: String? = null
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val REQUIRE_APPROVAL = booleanPreferencesKey("require_approval")
        val AUTO_ACCEPT_TRUSTED = booleanPreferencesKey("auto_accept_trusted")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SESSION_TIMEOUT_MINUTES = intPreferencesKey("session_timeout_minutes")
        val HIDE_DEVICE_WHEN_IDLE = booleanPreferencesKey("hide_device_when_idle")
        val SAVE_TREE_URI = stringPreferencesKey("save_tree_uri")
        val SAVE_FOLDER_LABEL = stringPreferencesKey("save_folder_label")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val storedId = prefs[Keys.DEVICE_ID]
        val defaultName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        UserSettings(
            deviceId = storedId ?: "",
            deviceName = prefs[Keys.DEVICE_NAME] ?: defaultName,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            language = prefs[Keys.LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.AR,
            requireApproval = prefs[Keys.REQUIRE_APPROVAL] ?: true,
            autoAcceptTrusted = prefs[Keys.AUTO_ACCEPT_TRUSTED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            sessionTimeoutMinutes = prefs[Keys.SESSION_TIMEOUT_MINUTES] ?: 15,
            hideDeviceWhenIdle = prefs[Keys.HIDE_DEVICE_WHEN_IDLE] ?: false,
            customSaveTreeUri = prefs[Keys.SAVE_TREE_URI],
            customSaveFolderLabel = prefs[Keys.SAVE_FOLDER_LABEL]
        )
    }

    suspend fun ensureDeviceId(): String {
        var result = ""
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.DEVICE_ID]
            if (existing.isNullOrBlank()) {
                val created = UUID.randomUUID().toString()
                prefs[Keys.DEVICE_ID] = created
                result = created
            } else {
                result = existing
            }
        }
        return result
    }

    suspend fun setDeviceName(name: String) {
        val clean = name.trim().take(36)
        if (clean.isNotEmpty()) {
            context.dataStore.edit { it[Keys.DEVICE_NAME] = clean }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setRequireApproval(require: Boolean) {
        context.dataStore.edit { it[Keys.REQUIRE_APPROVAL] = require }
    }

    suspend fun setAutoAcceptTrusted(autoAccept: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_ACCEPT_TRUSTED] = autoAccept }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setSessionTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SESSION_TIMEOUT_MINUTES] = minutes.coerceIn(5, 60) }
    }

    suspend fun setHideDeviceWhenIdle(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_DEVICE_WHEN_IDLE] = hide }
    }

    suspend fun setCustomSaveFolder(uri: String?, label: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(Keys.SAVE_TREE_URI)
                prefs.remove(Keys.SAVE_FOLDER_LABEL)
            } else {
                prefs[Keys.SAVE_TREE_URI] = uri
                prefs[Keys.SAVE_FOLDER_LABEL] = label ?: "Custom Folder"
            }
        }
    }
}
