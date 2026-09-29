plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "studio.cosmosis"
version = "0.1.0"

repositories { mavenCentral() }

val includePinnedTensorFlow =
    providers.gradleProperty("cosmosisBodyPixTensorFlow").orNull?.toBooleanStrictOrNull() ?: false
val tensorflowVersion = "0.4.1"

fun tensorflowNativeClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    require(arch in setOf("amd64", "x86_64")) {
        "Pinned TensorFlow 0.4.1 native distribution is verified here only for x86_64/amd64, found $arch"
    }
    return when {
        os.contains("linux") -> "linux-x86_64"
        os.contains("mac") -> "macosx-x86_64"
        os.contains("win") -> "windows-x86_64"
        else -> error("Unsupported OS for pinned TensorFlow 0.4.1 runtime: $os")
    }
}

dependencies {
    implementation(project(":orml-runner"))

    if (includePinnedTensorFlow) {
        runtimeOnly("org.tensorflow:tensorflow-core-api:$tensorflowVersion")
        runtimeOnly("org.tensorflow:tensorflow-core-api:$tensorflowVersion:${tensorflowNativeClassifier()}")
    }

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("studio.cosmosis.orml.runner.MainKt")
}

tasks.test { useJUnitPlatform() }
