plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "studio.cosmosis"
version = "0.1.0"

repositories { mavenCentral() }

val lwjglVersion = "3.4.3"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val uiSmokeGlfw = providers.gradleProperty("cosmosisUiSmokeGlfw").map(String::toBoolean).orElse(false)
val lwjglNatives = when {
    "mac" in osName && ("aarch64" in osArch || "arm64" in osArch) -> "natives-macos-arm64"
    "mac" in osName -> "natives-macos"
    "linux" in osName && ("aarch64" in osArch || "arm64" in osArch) -> "natives-linux-arm64"
    "linux" in osName -> "natives-linux"
    ("win" in osName) && ("aarch64" in osArch || "arm64" in osArch) -> "natives-windows-arm64"
    "win" in osName -> "natives-windows"
    else -> error("Unsupported desktop platform for LWJGL natives: $osName / $osArch")
}

dependencies {
    implementation("org.openrndr:openrndr-application:0.5.0")
    runtimeOnly("org.openrndr:openrndr-gl3-jvm:0.5.0")
    runtimeOnly("org.openrndr:openrndr-application-sdl:0.5.0")
    if (uiSmokeGlfw.get()) {
        runtimeOnly("org.openrndr:openrndr-application-glfw:0.5.0")
    }
    listOf("lwjgl","lwjgl-sdl","lwjgl-glfw","lwjgl-opengl","lwjgl-opengles","lwjgl-jemalloc","lwjgl-stb","lwjgl-tinyexr").forEach {
        runtimeOnly("org.lwjgl:$it:$lwjglVersion:$lwjglNatives")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.slf4j:slf4j-api:2.0.20")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.20")
    runtimeOnly("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

kotlin { jvmToolchain(21) }

application { mainClass.set("studio.cosmosis.AppKt") }

tasks.named<JavaExec>("run") {
    if (uiSmokeGlfw.get()) {
        systemProperty("org.openrndr.application", "GLFW")
    }
}

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

val providerContractSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Offline loopback contract smoke for OpenAI, Gemini and LiteLLM HTTP adapters."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("studio.cosmosis.provider.ProviderContractSmokeKt")
}
