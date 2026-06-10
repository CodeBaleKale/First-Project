package com.snapcal.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

const val DEFAULT_MODEL = "claude-opus-4-8"

class SettingsStore(private val context: Context) {
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val modelKey = stringPreferencesKey("model")

    val apiKey: Flow<String> = context.dataStore.data.map { it[apiKeyKey] ?: "" }
    val model: Flow<String> = context.dataStore.data.map { it[modelKey] ?: DEFAULT_MODEL }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[apiKeyKey] = value.trim() }
    }

    suspend fun setModel(value: String) {
        context.dataStore.edit { it[modelKey] = value.trim().ifEmpty { DEFAULT_MODEL } }
    }
}
