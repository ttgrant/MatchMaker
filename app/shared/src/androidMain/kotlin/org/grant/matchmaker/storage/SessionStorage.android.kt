package org.grant.matchmaker.storage

import android.content.Context

actual object SessionStorage {
    private const val PREFS_NAME = "matchmaker_prefs"
    private const val STORAGE_KEY = "matchmaker_session_v1"

    private var appContext: Context? = null
    private var memoryFallback: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun save(data: String) {
        memoryFallback = data
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(STORAGE_KEY, data)
            ?.apply()
    }

    actual fun load(): String? {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs?.getString(STORAGE_KEY, null) ?: memoryFallback
    }

    actual fun clear() {
        memoryFallback = null
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(STORAGE_KEY)
            ?.apply()
    }
}
