pluginManagement {
    plugins {
        val kotlin = "1.3.50"
        id("org.jetbrains.kotlin.jvm") version kotlin
    }
}

rootProject.name = "apollo-engine-reporter-java"

include("apollo-engine-reporter-java")
