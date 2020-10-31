import com.diffplug.gradle.spotless.SpotlessExtension
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayExtension.PackageConfig
import com.jfrog.bintray.gradle.BintrayExtension.VersionConfig
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact

plugins {
    id("com.diffplug.spotless")
    id("com.github.ben-manes.versions")
    id("com.jfrog.bintray") apply false
    kotlin("jvm") apply false
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
    version = "1.2.0"

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
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<SpotlessExtension> {
        kotlin {
            ktfmt("0.18")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    configure<JavaPluginExtension> {
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        }
    }

    // Publishing

    val publishedProjects = listOf("apollo-engine-reporter")

    if (name in publishedProjects) {
        apply(plugin = "maven-publish")
        apply(plugin = "com.jfrog.bintray")

        val repoUrl = "https://github.com/blaugold/apollo-engine-reporter"

        val sourcesJar = tasks.create<Jar>("sourcesJar") {
            from(project.the<SourceSetContainer>()["main"].allSource)
            archiveClassifier.set("sources")
        }

        configure<PublishingExtension> {
            publications {
                register<MavenPublication>("maven") {
                    from(components["kotlin"])
                    artifact(sourcesJar)

                    pom {
                        name.set(project.name)
                        version = project.version as String
                        scm {
                            url.set(repoUrl)
                            tag.set("v${project.version}")
                        }
                        developers { developer { name.set("Gabriel Terwesten") } }
                    }
                }
            }
        }

        configure<BintrayExtension> {
            user = System.getenv("BINTRAY_USER")
            key = System.getenv("BINTRAY_API_KEY")

            setPublications("maven")

            pkg(closureOf<PackageConfig> {
                publish = true
                setLicenses("MIT")
                userOrg = "gabriel-terwesten-oss"
                repo = "maven"
                name = project.name
                vcsUrl = repoUrl
                version(closureOf<VersionConfig> {
                    name = project.version as String
                })
            })
        }

        tasks.withType<BintrayUploadTask> {
            doFirst {
                project
                    .the<PublishingExtension>()
                    .publications
                    .filterIsInstance<MavenPublication>()
                    .forEach {
                        val module = buildDir.resolve("publications/${it.name}/module.json")
                        it.artifact(object : FileBasedMavenArtifact(module) {
                            override fun getDefaultExtension(): String = "module"
                        })
                    }
            }
        }
    }
}
