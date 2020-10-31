pluginManagement {
    plugins {
        val kotlin = "1.3.50"
        id("org.jetbrains.kotlin.jvm") version kotlin
        id("com.diffplug.spotless") version "5.7.0"
    }
}

rootProject.name = "apollo-engine-reporter-java"

include("apollo-engine-reporter-java")
