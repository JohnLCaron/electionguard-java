import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

// bug in IntelliJ in which libs shows up as not being accessible
// see https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    base
    `java-library`
    alias(libs.plugins.protobufPlugin)
    alias(libs.plugins.owaspDepCheckPlugin)
    alias(libs.plugins.execforkPlugin)
}

tasks.wrapper {
    gradleVersion = "7.3.3"
    distributionSha256Sum = "c9490e938b221daf0094982288e4038deed954a3f12fb54cbf270ddf4e37d879"
    distributionType = Wrapper.DistributionType.ALL
}

version = "0.9.5-SNAPSHOT"
group = "com.sunya.electionguard"

dependencyCheck {
    analyzers.retirejs.enabled = false
    analyzers.assemblyEnabled = false
    data.setDirectory("$rootDir/project-files/owasp-dependency-check/nvd")
    scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
    suppressionFile = "$rootDir/project-files/owasp-dependency-check/dependency-check-suppression.xml"
    // fail the build if any vulnerable dependencies are identified (any CVSS score > 0).
    failBuildOnCVSS = 0F
}

description = "electionguard-java"

dependencies {
    api(platform(libs.protobufBom))

    implementation(platform(libs.grpcBom))
    implementation(libs.grpcProtobuf)
    implementation(libs.grpcStub)
    compileOnly(libs.tomcatAnnotationsApi)

    implementation(libs.flogger)
    implementation(libs.gson)
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

    testImplementation(libs.grpcTesting)
    testImplementation(libs.jqwik) // aggregate jqwik dependency
    testImplementation(libs.junitJupiter)
    testImplementation(libs.mockitoCore)
    testImplementation(libs.truth)
    testImplementation(libs.truthJava8Extension)
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
            }
        }
    }
}

// handle proto generated source and class files
sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("build/generated/source/proto/main/grpc")
        }
    }
}

tasks.test {
    useJUnitPlatform {
        includeEngines("jqwik")

        // Or include several Junit engines if you use them
        // includeEngines "jqwik", "junit-jupiter", "junit-vintage"

        // includeTags "fast", "medium"
        // excludeTags "slow"
    }

    include("**/*Properties.class")
    include("**/*Test.class")
    include("**/*Tests.class")
    include("**/Test*.class")
}

tasks.withType<Javadoc> {
    group = "documentation"

    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).docEncoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet("UTF-8")

    // When instances of JDK classes appear in our Javadoc (e.g. "java.lang.String"), create links out of them to
    // Oracle"s JavaSE 11 Javadoc.
    (options as StandardJavadocDocletOptions).links("https://docs.oracle.com/en/java/javase/11/docs/api/")

    // doclint="all" (the default) will identify 100s of errors in our docs and cause no Javadoc to be generated.
    // So, turn it off.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks {
    create("fatJar", Jar::class.java) {
        archiveClassifier.set("all")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes("Main-Class" to "com.sunya.electionguard.verifier.VerifyElectionRecord")
        }
        from(configurations.runtimeClasspath.get()
            .onEach { println("add from dependencies: ${it.name}") }
            .map { if (it.isDirectory) it else zipTree(it) })
        val sourcesMain = sourceSets.main.get()
        sourcesMain.allSource.forEach { println("add from sources: ${it.name}") }
        from(sourcesMain.output)
    }
}