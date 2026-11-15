package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.myTubeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mytube_prefs")

data class UserProfile(
    val name: String = "Creator",
    val bio: String = "Personal offline studio on MY TUBE",
    val avatarPath: String? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val PROFILE_AVATAR_PATH = stringPreferencesKey("profile_avatar_path")
    }

    val isDarkTheme: Flow<Boolean> = context.myTubeDataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_THEME] ?: true // Default theme: Dark Mode
    }

    val userProfile: Flow<UserProfile> = context.myTubeDataStore.data.map { prefs ->
        UserProfile(
            name = prefs[Keys.PROFILE_NAME]?.takeIf { it.isNotBlank() } ?: "Creator",
            bio = prefs[Keys.PROFILE_BIO] ?: "Personal offline studio on MY TUBE",
            avatarPath = prefs[Keys.PROFILE_AVATAR_PATH]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.myTubeDataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    suspend fun updateProfile(name: String, bio: String) {
        context.myTubeDataStore.edit { prefs ->
            prefs[Keys.PROFILE_NAME] = name.trim().ifBlank { "Creator" }
            prefs[Keys.PROFILE_BIO] = bio.trim()
        }
    }

    suspend fun updateProfileAvatar(avatarPath: String?) {
        context.myTubeDataStore.edit { prefs ->
            if (avatarPath.isNullOrBlank()) {
                prefs.remove(Keys.PROFILE_AVATAR_PATH)
            } else {
                prefs[Keys.PROFILE_AVATAR_PATH] = avatarPath
            }
        }
    }
}
