plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "studio.cosmosis"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation(project(":orml-runner"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.12.2")
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("studio.cosmosis.orml.runner.MainKt")
}

tasks.test { useJUnitPlatform() }
