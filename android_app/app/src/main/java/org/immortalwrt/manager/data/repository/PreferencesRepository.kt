package org.immortalwrt.manager.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.immortalwrt.manager.domain.model.RouterCredentials

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "router_prefs")

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val KEY_HOST = stringPreferencesKey("router_host")
        val KEY_PORT = intPreferencesKey("router_port")
        val KEY_USERNAME = stringPreferencesKey("router_username")
        val KEY_PASSWORD = stringPreferencesKey("router_password")
        val KEY_HTTPS = booleanPreferencesKey("router_https")
        val KEY_AUTO_LOGIN = booleanPreferencesKey("router_auto_login")
    }

    val credentialsFlow: Flow<RouterCredentials> = context.dataStore.data.map { prefs ->
        RouterCredentials(
            host = prefs[PreferencesKeys.KEY_HOST] ?: "10.10.10.1",
            port = prefs[PreferencesKeys.KEY_PORT] ?: 80,
            username = prefs[PreferencesKeys.KEY_USERNAME] ?: "root",
            password = prefs[PreferencesKeys.KEY_PASSWORD] ?: "",
            useHttps = prefs[PreferencesKeys.KEY_HTTPS] ?: false
        )
    }

    val autoLoginFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_AUTO_LOGIN] ?: false
    }

    suspend fun saveCredentials(credentials: RouterCredentials, autoLogin: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEY_HOST] = credentials.host
            prefs[PreferencesKeys.KEY_PORT] = credentials.port
            prefs[PreferencesKeys.KEY_USERNAME] = credentials.username
            prefs[PreferencesKeys.KEY_PASSWORD] = credentials.password
            prefs[PreferencesKeys.KEY_HTTPS] = credentials.useHttps
            prefs[PreferencesKeys.KEY_AUTO_LOGIN] = autoLogin
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
