// bug in IntelliJ in which libs shows up as not being accessible
// see https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    base
    `java-library`
}

version = "0.9.5-SNAPSHOT"
group = "com.sunya.electionguard.viewer"

description = "electionguard-java-viewer"

dependencies {
    api(project(":core"))
    api(platform(libs.protobufBom))

    implementation(libs.guava)
    implementation(libs.jcommander)
    implementation(libs.jsr305)
    implementation(libs.protobufJava)

    // TODO put viz into separate module
    // temporary until i get uibase hosted on a maven repo
    // implementation "edu.ucar:uibase:6.1.0-SNAPSHOT"
    implementation(files("libs/uibase.jar"))
    implementation(libs.jdom2)
    implementation(libs.slf4j)
    // implementation("com.jgoodies:jgoodies-forms:1.6.0")
    runtimeOnly(libs.slf4jJdk14)

    runtimeOnly(libs.grpcNettyShaded)
    runtimeOnly(libs.floggerBackend)
}

tasks {
    register("fatJar", Jar::class.java) {
        archiveClassifier.set("all")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes("Main-Class" to "com.sunya.electionguard.viz.ElectionGuardViewer")
        }
        from(configurations.runtimeClasspath.get()
            .onEach { println("add from dependencies: ${it.name}") }
            .map { if (it.isDirectory) it else zipTree(it) })
        val sourcesMain = sourceSets.main.get()
        sourcesMain.allSource.forEach { println("add from sources: ${it.name}") }
        from(sourcesMain.output)
    }
}