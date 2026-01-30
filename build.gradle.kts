plugins {
    java
    kotlin("jvm") version "1.9.24"
    `maven-publish`
}

group = "io.github.lasilva"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.github.classgraph:classgraph:4.8.184")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.20.1")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}