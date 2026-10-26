package org.grant.matchmaker.storage

actual object SessionStorage {
    private var memoryFallback: String? = null

    actual fun save(data: String) {
        memoryFallback = data
    }

    actual fun load(): String? {
        return memoryFallback
    }

    actual fun clear() {
        memoryFallback = null
    }
}
