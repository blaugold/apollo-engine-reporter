pluginManagement {
    plugins {
        val kotlin = "1.4.10"
        kotlin("jvm") version kotlin
        id("com.diffplug.spotless") version "5.7.0"
    }
}

rootProject.name = "apollo-engine-reporter-java"

include("apollo-engine-reporter-java")
