import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    id("com.diffplug.spotless")
}

spotless {
    format("markdown") {
        target("*.md")
        prettier()
    }

    format("yaml") {
        target(".github/**/*.yaml")
        prettier()
    }
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    group = "com.gabrielterwesten"
    version = "1.1.0"

    repositories {
        jcenter()
    }

    configure<SpotlessExtension> {
        kotlinGradle {
            ktlint()
        }
    }
}

subprojects {
    configure<SpotlessExtension> {
        kotlin {
            ktfmt("0.18")
        }
    }
}
