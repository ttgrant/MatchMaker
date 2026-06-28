package org.grant.matchmaker

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform