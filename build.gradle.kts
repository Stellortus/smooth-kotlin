plugins {
    kotlin("jvm") version "2.4.20"
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = providers.gradleProperty("artifactId").getOrElse(project.name)
        }
    }
}

tasks.register("printVersion") {
    val projectVersion = project.version.toString()
    doLast {
        println(projectVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}
