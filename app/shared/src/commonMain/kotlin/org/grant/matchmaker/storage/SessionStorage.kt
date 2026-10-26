package org.grant.matchmaker.storage

expect object SessionStorage {
    fun save(data: String)
    fun load(): String?
    fun clear()
}
