package org.grant.matchmaker.storage

import platform.Foundation.NSUserDefaults

actual object SessionStorage {
    private const val STORAGE_KEY = "matchmaker_session_v1"

    actual fun save(data: String) {
        NSUserDefaults.standardUserDefaults.setObject(data, forKey = STORAGE_KEY)
    }

    actual fun load(): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(STORAGE_KEY)
    }

    actual fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(STORAGE_KEY)
    }
}
