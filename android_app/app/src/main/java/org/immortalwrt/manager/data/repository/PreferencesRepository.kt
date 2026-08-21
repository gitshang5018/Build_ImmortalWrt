package org.immortalwrt.manager.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.immortalwrt.manager.domain.model.RouterCredentials
import org.immortalwrt.manager.domain.model.RouterNode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "router_prefs")

class PreferencesRepository(private val context: Context) {

    private val gson = Gson()

    private object PreferencesKeys {
        val KEY_HOST = stringPreferencesKey("router_host")
        val KEY_PORT = intPreferencesKey("router_port")
        val KEY_USERNAME = stringPreferencesKey("router_username")
        val KEY_PASSWORD = stringPreferencesKey("router_password")
        val KEY_HTTPS = booleanPreferencesKey("router_https")
        val KEY_AUTO_LOGIN = booleanPreferencesKey("router_auto_login")
        val KEY_NODES_JSON = stringPreferencesKey("router_nodes_json")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0: Auto, 1: Light, 2: Dark
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
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

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_THEME_MODE] ?: 0
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_DYNAMIC_COLOR] ?: true
    }

    val savedNodesFlow: Flow<List<RouterNode>> = context.dataStore.data.map { prefs ->
        val json = prefs[PreferencesKeys.KEY_NODES_JSON]
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<RouterNode>>() {}.type
                gson.fromJson<List<RouterNode>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
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

    suspend fun saveNode(node: RouterNode) {
        context.dataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.KEY_NODES_JSON]
            val currentList = if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<MutableList<RouterNode>>() {}.type
                    gson.fromJson<MutableList<RouterNode>>(json, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            val existingIdx = currentList.indexOfFirst { it.id == node.id }
            if (existingIdx != -1) {
                currentList[existingIdx] = node
            } else {
                currentList.add(node)
            }
            prefs[PreferencesKeys.KEY_NODES_JSON] = gson.toJson(currentList)
        }
    }

    suspend fun deleteNode(nodeId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[PreferencesKeys.KEY_NODES_JSON]
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<MutableList<RouterNode>>() {}.type
                    val currentList = gson.fromJson<MutableList<RouterNode>>(json, type) ?: mutableListOf()
                    currentList.removeAll { it.id == nodeId }
                    prefs[PreferencesKeys.KEY_NODES_JSON] = gson.toJson(currentList)
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEY_THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferencesKeys.KEY_HOST)
            prefs.remove(PreferencesKeys.KEY_PORT)
            prefs.remove(PreferencesKeys.KEY_USERNAME)
            prefs.remove(PreferencesKeys.KEY_PASSWORD)
            prefs.remove(PreferencesKeys.KEY_HTTPS)
            prefs.remove(PreferencesKeys.KEY_AUTO_LOGIN)
        }
    }
}
