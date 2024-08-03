plugins {
    id("application")
    kotlin("jvm") version "1.9.23"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.ncorti.ktfmt.gradle") version "0.19.0"
}

group = "com.patriktrefil"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.patriktrefil.MainKt"
}

javafx {
    modules("javafx.controls", "javafx.fxml")
}
