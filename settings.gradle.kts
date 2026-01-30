pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "1.9.24"
    }
}

rootProject.name = "dendeframework"
include("main")
