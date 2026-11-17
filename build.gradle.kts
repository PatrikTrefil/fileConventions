import java.util.zip.ZipFile

plugins {
    id("application")
    kotlin("jvm") version "1.9.23"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.ncorti.ktfmt.gradle") version "0.19.0"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.beryx.runtime") version "1.13.1"
}

group = "com.trefilsoftware"
version = providers.gradleProperty("releaseVersion").getOrElse("1.0.0")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    implementation("net.harawata:appdirs:1.2.2")
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
    version = "17.0.13"
    modules("javafx.controls", "javafx.fxml")
}

val windowsInstallerResources = layout.buildDirectory.dir("windows-installer-resources")
val prepareWindowsInstallerResources by tasks.registering {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    val jpackageModule = file("${System.getProperty("java.home")}/jmods/jdk.jpackage.jmod")
    val launchUi = file("packaging/windows/launch-application.xml")
    inputs.files(jpackageModule, launchUi)
    outputs.dir(windowsInstallerResources)
    doLast {
        // Extend the packaging JDK's template rather than maintaining a stale copy of it.
        val template = ZipFile(jpackageModule).use { archive ->
            val entry = archive.getEntry("classes/jdk/jpackage/internal/resources/main.wxs")
                ?: error("The packaging JDK does not contain the expected Windows WiX template")
            archive.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        check(template.contains("</Product>")) { "Unsupported jpackage WiX template" }
        val destination = windowsInstallerResources.get().asFile
        destination.mkdirs()
        destination.resolve("main.wxs").writeText(
            template.replace("</Product>", launchUi.readText() + "\n</Product>")
        )
    }
}

tasks.named("jpackage") {
    dependsOn(prepareWindowsInstallerResources)
}

runtime {
    jpackage {
        val currentOs = org.gradle.internal.os.OperatingSystem.current()
        installerType = if (currentOs.isWindows) "exe" else if (currentOs.isMacOsX) "dmg" else "deb"
        val imgType = if (currentOs.isWindows) "ico" else if (currentOs.isMacOsX) "icns" else "png"
        imageOptions.addAll(arrayOf("--icon", "src/main/resources/file-check-white.$imgType"))

        val resourceDir = if (currentOs.isWindows) windowsInstallerResources.get().asFile.path else "src/main/resources"
        installerOptions.addAll(arrayOf("--resource-dir", resourceDir))
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
