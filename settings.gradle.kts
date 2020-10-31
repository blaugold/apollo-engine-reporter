pluginManagement {
    plugins {
        val kotlin = "1.4.10"
        kotlin("jvm") version kotlin
        id("com.diffplug.spotless") version "5.7.0"
        id("com.github.ben-manes.versions") version "0.33.0"
        id("com.jfrog.bintray") version "1.8.5"
    }
}

rootProject.name = "apollo-engine-reporter"

include("apollo-engine-reporter")
