plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(26)
}

group = "org.grant.matchmaker"
version = "1.0.0"
application {
    mainClass = "org.grant.matchmaker.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}