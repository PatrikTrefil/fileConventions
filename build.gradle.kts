plugins {
    id("application")
    kotlin("jvm") version "1.9.23"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.ncorti.ktfmt.gradle") version "0.19.0"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.beryx.runtime") version "1.13.1"
}

group = "com.trefilsoftware"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.dustinredmond.fxtrayicon:FXTrayIcon:4.2.0")
    implementation("org.hibernate.orm:hibernate-core:6.5.2.Final")
    implementation("org.hsqldb:hsqldb:2.7.4")
    implementation("net.harawata:appdirs:1.2.2")
    implementation("commons-io:commons-io:2.16.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.trefilsoftware.fileConventions.MainKt"
    applicationName = "File Conventions"
}

javafx {
    modules("javafx.controls", "javafx.fxml")
}

runtime {
    jpackage {
        val currentOs = org.gradle.internal.os.OperatingSystem.current()
        val imgType = if (currentOs.isWindows) "ico" else if (currentOs.isMacOsX) "icns" else "png"
        imageOptions.addAll(arrayOf("--icon", "src/main/resources/file-check-white.$imgType"))

        installerOptions.addAll(arrayOf("--resource-dir", "src/main/resources"))
        installerOptions.addAll(arrayOf("--vendor", "Trefil Software"))

        val pkgName = "file-conventions"
        imageName = application.applicationName

        if(currentOs.isWindows) {
            installerOptions.addAll(arrayOf("--win-per-user-install", "--win-dir-chooser", "--win-menu", "--win-shortcut"))
        }
        else if (currentOs.isLinux) {
            installerOptions.addAll(arrayOf("--linux-package-name", pkgName,"--linux-shortcut"))
        }
        else if (currentOs.isMacOsX) {
            installerOptions.addAll(arrayOf("--mac-package-name", pkgName))
        }
    }
}
