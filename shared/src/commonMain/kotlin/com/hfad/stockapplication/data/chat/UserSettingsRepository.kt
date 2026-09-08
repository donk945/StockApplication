package com.hfad.stockapplication.data.chat

import com.tencent.kuikly.core.module.SharedPreferencesModule

/**
 * 本机账号与设置。没有云端登录，只读写 SharedPreferences。
 */
class UserSettingsRepository(
    private val sp: SharedPreferencesModule,
) {
    fun loadDisplayName(): String {
        return sp.getString(KEY_DISPLAY_NAME).trim()
    }

    fun saveDisplayName(name: String) {
        sp.setString(KEY_DISPLAY_NAME, name.trim())
    }

    fun loadCustomApiKey(): String {
        return sp.getString(KEY_CUSTOM_API_KEY).trim()
    }

    fun saveCustomApiKey(key: String) {
        sp.setString(KEY_CUSTOM_API_KEY, key.trim())
    }

    fun loadLastSessionId(): String {
        return sp.getString(KEY_LAST_SESSION_ID).trim()
    }

    fun saveLastSessionId(id: String) {
        sp.setString(KEY_LAST_SESSION_ID, id.trim())
    }

    fun loadThemeMode(): ThemeMode {
        return ThemeMode.from(sp.getString(KEY_THEME_MODE))
    }

    fun saveThemeMode(mode: ThemeMode) {
        sp.setString(KEY_THEME_MODE, mode.storage)
    }

    companion object {
        const val KEY_DISPLAY_NAME = "user_display_name_v1"
        const val KEY_CUSTOM_API_KEY = "user_api_key_v1"
        const val KEY_LAST_SESSION_ID = "user_last_session_id_v1"
        const val KEY_THEME_MODE = "user_theme_mode_v1"
    }
}

enum class ThemeMode(val storage: String, val label: String) {
    System("system", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    ;

    fun isDark(systemNight: Boolean): Boolean {
        return when (this) {
            Dark -> true
            Light -> false
            System -> systemNight
        }
    }

    companion object {
        fun from(raw: String): ThemeMode {
            return entries.firstOrNull { it.storage == raw.trim() } ?: System
        }
    }
}
