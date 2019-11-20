import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.util.*

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("com.graphql-java:graphql-java:13.0")
    implementation("com.google.protobuf:protobuf-java:3.10.0")
    implementation("org.apache.logging.log4j:log4j-api:2.12.1")
    implementation("com.squareup.okhttp3:okhttp:4.2.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.assertj:assertj-core:3.14.0")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.apache.logging.log4j:log4j-core:2.12.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

val writeVersionFile: Task = tasks.create("writeVersionFile") {
    inputs.property("version", project.version)

    val mainResources = sourceSets.main.map { it.output.resourcesDir!!.absoluteFile }
    val versionFile = mainResources.map { it.resolve("${project.name}.version") }

    outputs.file(versionFile)

    doFirst {
        mainResources.get().mkdirs()
        versionFile.get().writeText(project.version as String)
    }
}

tasks.compileKotlin {
    dependsOn(writeVersionFile)
}

val sourcesJar = tasks.create<Jar>("sourcesJar") {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

val githubCredentials = rootProject.file("github-credentials.properties")
if (githubCredentials.exists()) {
    val properties = Properties()
    properties.load(githubCredentials.inputStream())
    properties.forEach { project.ext[it.key as String] = it.value }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/blaugold/apollo-engine-reporter-java")
            credentials {
                username = project.findProperty("github.username") as String?
                password = project.findProperty("github.password") as String?
            }
        }
        mavenLocal()
    }

    publications {
        register<MavenPublication>("reporter") {
            from(components["kotlin"])
            artifact(sourcesJar)
        }
    }
}
