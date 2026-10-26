package org.grant.matchmaker.storage

actual object SessionStorage {
    private const val STORAGE_KEY = "matchmaker_session_v1"
    private var memoryFallback: String? = null

    actual fun save(data: String) {
        memoryFallback = data
        val key = STORAGE_KEY
        js("try { if (typeof localStorage !== 'undefined') localStorage.setItem(key, data); } catch (e) {}")
    }

    actual fun load(): String? {
        val key = STORAGE_KEY
        val stored = js("try { (typeof localStorage !== 'undefined') ? localStorage.getItem(key) : null } catch (e) { null }") as? String
        return stored ?: memoryFallback
    }

    actual fun clear() {
        memoryFallback = null
        val key = STORAGE_KEY
        js("try { if (typeof localStorage !== 'undefined') localStorage.removeItem(key); } catch (e) {}")
    }
}
