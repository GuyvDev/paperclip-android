package com.bu.paperclip.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val baseUrl: String = "",
    val apiToken: String = "",
    val companyId: String = "",
    val theme: String = "system",
    val notifications: Boolean = true,
    val lastNotifiedAt: String = "",
)

class SettingsStore(private val context: Context) {
    private object K {
        val baseUrl = stringPreferencesKey("baseUrl")
        val apiToken = stringPreferencesKey("apiToken")
        val companyId = stringPreferencesKey("companyId")
        val theme = stringPreferencesKey("theme")
        val notifications = booleanPreferencesKey("notifications")
        val lastNotifiedAt = stringPreferencesKey("lastNotifiedAt")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            baseUrl = p[K.baseUrl] ?: "",
            apiToken = p[K.apiToken] ?: "",
            companyId = p[K.companyId] ?: "",
            theme = p[K.theme] ?: "system",
            notifications = p[K.notifications] ?: true,
            lastNotifiedAt = p[K.lastNotifiedAt] ?: "",
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setBaseUrl(v: String) = context.dataStore.edit { it[K.baseUrl] = v }
    suspend fun setApiToken(v: String) = context.dataStore.edit { it[K.apiToken] = v }
    suspend fun setCompanyId(v: String) = context.dataStore.edit { it[K.companyId] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[K.theme] = v }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[K.notifications] = v }
    suspend fun setLastNotifiedAt(v: String) = context.dataStore.edit { it[K.lastNotifiedAt] = v }
}
