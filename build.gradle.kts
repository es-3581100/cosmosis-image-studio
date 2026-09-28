plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "studio.cosmosis"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation("org.openrndr:openrndr-application:0.5.0")
    runtimeOnly("org.openrndr:openrndr-gl3-jvm:0.5.0")
    runtimeOnly("org.openrndr:openrndr-application-sdl:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    runtimeOnly("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("studio.cosmosis.AppKt") }

tasks.test { useJUnitPlatform() }

val liveProviderSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Explicit opt-in live provider smoke tests. Requires COSMOSIS_LIVE_PROVIDER_TESTS=1."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("studio.cosmosis.provider.LiveSmokeKt")
    onlyIf { System.getenv("COSMOSIS_LIVE_PROVIDER_TESTS") == "1" || System.getenv("OFFWORLD_LIVE_PROVIDER_TESTS") == "1" }
}

val acceptanceSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Offline end-to-end acceptance smoke using the deterministic local preview provider."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("studio.cosmosis.AcceptanceSmokeKt")
}
