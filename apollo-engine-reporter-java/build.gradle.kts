import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm")
}

dependencies {
    compileOnly("javax.servlet:javax.servlet-api:3.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.10.1")
    implementation("com.google.protobuf:protobuf-java:3.10.0")
    implementation("com.graphql-java:graphql-java:13.0")
    implementation("com.squareup.okhttp3:okhttp:4.2.2")
    implementation("org.slf4j:slf4j-api:1.7.28")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("javax.servlet:javax.servlet-api:3.1.0")
    testImplementation("org.apache.logging.log4j:log4j-slf4j18-impl:2.13.1")
    testImplementation("org.assertj:assertj-core:3.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
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
