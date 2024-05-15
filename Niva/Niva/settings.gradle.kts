pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
//        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
//        maven("https://www.jetbrains.com/intellij-repository/releases")
//        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
//    id("org.jetbrains.amper.settings.plugin").version("0.2.0")
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "niva"

//plugins.apply("org.jetbrains.amper.settings.plugin")
