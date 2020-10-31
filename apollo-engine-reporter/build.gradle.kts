dependencies {
    compileOnly("javax.servlet:javax.servlet-api:3.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.3")
    implementation("com.google.protobuf:protobuf-java:3.13.0")
    implementation("com.graphql-java:graphql-java:15.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.0")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation(kotlin("reflect"))
    testImplementation("io.mockk:mockk:1.10.2")
    testImplementation("javax.servlet:javax.servlet-api:4.0.1")
    testImplementation("org.apache.logging.log4j:log4j-slf4j18-impl:2.13.3")
    testImplementation("org.assertj:assertj-core:3.18.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.0")
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
