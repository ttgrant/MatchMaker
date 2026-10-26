@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.grant.matchmaker.storage

@JsFun("(key, value) => { try { if (typeof localStorage !== 'undefined') localStorage.setItem(key, value); } catch (e) {} }")
private external fun jsLocalStorageSet(key: String, value: String)

@JsFun("(key) => { try { if (typeof localStorage !== 'undefined') return localStorage.getItem(key); } catch (e) {} return null; }")
private external fun jsLocalStorageGet(key: String): String?

@JsFun("(key) => { try { if (typeof localStorage !== 'undefined') localStorage.removeItem(key); } catch (e) {} }")
private external fun jsLocalStorageRemove(key: String)

actual object SessionStorage {
    private const val STORAGE_KEY = "matchmaker_session_v1"
    private var memoryFallback: String? = null

    actual fun save(data: String) {
        memoryFallback = data
        jsLocalStorageSet(STORAGE_KEY, data)
    }

    actual fun load(): String? {
        return jsLocalStorageGet(STORAGE_KEY) ?: memoryFallback
    }

    actual fun clear() {
        memoryFallback = null
        jsLocalStorageRemove(STORAGE_KEY)
    }
}
