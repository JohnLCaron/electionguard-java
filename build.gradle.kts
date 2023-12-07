// bug in IntelliJ in which libs shows up as not being accessible
// see https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    base
    `java-library`
    alias(libs.plugins.owaspDepCheckPlugin)
    `maven-publish`
}

subprojects {
    // See buildSrc/src/main/kotlin/cdm.java-conventions.gradle.kts and
    // buildSrc/src/main/kotlin/cdm.library-conventions.gradle.kts for
    // more details on how cdm projects are built.
    project.version = "0.9.5-SNAPSHOT"
    project.group = "com.sunya.electionguard"
}

tasks.wrapper {
    gradleVersion = "7.3.3"
    distributionSha256Sum = "c9490e938b221daf0094982288e4038deed954a3f12fb54cbf270ddf4e37d879"
    distributionType = Wrapper.DistributionType.ALL
}

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



publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "edu.kit.kastel.electionguard"
            artifactId = "electionguard"
            version = "1.1"

            from(components["java"])
        }
    }
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

