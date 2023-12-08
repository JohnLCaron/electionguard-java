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
    alias(libs.plugins.execforkPlugin)
    `maven-publish`

    kotlin("jvm") version "1.6.21"
}

version = "0.9.5-SNAPSHOT"
group = "com.sunya.electionguard"

description = "electionguard-java-core"

dependencies {
    api(platform(libs.protobufBom))

    implementation(libs.bytesLib)
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

java {
    // see Notes section in README.md 
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    register("fatJar", Jar::class.java) {
        archiveClassifier.set("all")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes("Main-Class" to "com.sunya.electionguard.Simple")
        }
        from(configurations.runtimeClasspath.get()
            // .onEach { println("add from dependencies: ${it.name}") }
            .map { if (it.isDirectory) it else zipTree(it) }
        )
        val sourcesMain = sourceSets.main.get(
        )
        exclude("/META-INF/PFOPENSO.*")
        // sourcesMain.allSource.forEach { println("add from sources: ${it.name}") }
        from(sourcesMain.output)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "edu.kit.kastel.electionguard.core"
            artifactId = "electionguard.core"
            version = "1.1"

            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kit-security/electionguard-java-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
