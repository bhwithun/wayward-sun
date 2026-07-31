package com.example.template.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Simple, reusable wrapper around DataStore<Preferences>.
 *
 * Usage from an Application or Activity:
 *   val storage = AppStorage(context)
 *
 * Or inject it as a singleton in larger apps.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppStorage(private val context: Context) {

    object Keys {
        val LAST_OPENED_AT = longPreferencesKey("last_opened_at")
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val COUNTER = intPreferencesKey("counter")
    }

    // ---------------------------
    // Generic helpers
    // ---------------------------

    fun getStringFlow(key: Preferences.Key<String>, default: String? = null): Flow<String?> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    fun getIntFlow(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    fun getLongFlow(key: Preferences.Key<Long>, default: Long = 0L): Flow<Long> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { it[key] = value }
    }

    fun getBooleanFlow(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> =
        context.dataStore.data.map { it[key] ?: default }

    suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun remove(key: Preferences.Key<*>) {
        context.dataStore.edit { it.remove(key) }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // ---------------------------
    // Convenience typed accessors (examples)
    // ---------------------------

    val lastOpenedAt: Flow<Long?> = getLongFlow(Keys.LAST_OPENED_AT)
    suspend fun setLastOpenedAt(millis: Long) = setLong(Keys.LAST_OPENED_AT, millis)

    val userName: Flow<String?> = getStringFlow(Keys.USER_NAME)
    suspend fun setUserName(name: String) = setString(Keys.USER_NAME, name)

    val isFirstLaunch: Flow<Boolean> = getBooleanFlow(Keys.IS_FIRST_LAUNCH, default = true)
    suspend fun setIsFirstLaunch(value: Boolean) = setBoolean(Keys.IS_FIRST_LAUNCH, value)

    val counter: Flow<Int> = getIntFlow(Keys.COUNTER)
    suspend fun setCounter(value: Int) = setInt(Keys.COUNTER, value)

    /**
     * Read a value once (suspend). Useful in ViewModels or repositories.
     */
    suspend fun getStringOnce(key: Preferences.Key<String>, default: String? = null): String? =
        getStringFlow(key, default).first()

    suspend fun getIntOnce(key: Preferences.Key<Int>, default: Int = 0): Int =
        getIntFlow(key, default).first()

    suspend fun getBooleanOnce(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean =
        getBooleanFlow(key, default).first()
}
