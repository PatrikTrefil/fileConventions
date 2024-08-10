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
    implementation("com.dustinredmond.fxtrayicon:FXTrayIcon:4.2.0")
    implementation("org.hibernate.orm:hibernate-core:6.5.2.Final")
    implementation("com.h2database:h2:2.2.224")
    implementation("net.harawata:appdirs:1.2.2")
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
