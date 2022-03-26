import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val spaceUsername: String by project
val spacePassword: String by project

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    application
    id("maven-publish")
}

group = "me.likda"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.postgresql:postgresql:42.3.2")
    implementation("org.xerial:sqlite-jdbc:3.36.0.3")

    implementation("org.jsoup:jsoup:1.14.3")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")

    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
    implementation("com.beust:klaxon:5.5")

    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}

publishing {
    apply(plugin = "maven-publish")
    configure<PublishingExtension> {
        repositories {
            maven {
                url = uri("https://maven.pkg.jetbrains.space/likco/p/dbhelper/maven")
                credentials {
                    username = spaceUsername
                    password = spacePassword
                }
            }
        }
        publications {
            register<MavenPublication>("gpr") {
                groupId = "liklibs.db"
                artifactId = "db-helper"
                version = "0.9"

                from(components["java"])
            }
        }
    }
}

