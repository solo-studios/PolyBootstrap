/*
 * PolyBootstrap - A Discord bot for the Polyhedral Development discord server
 * Copyright (c) 2021-2021 solonovamax <solonovamax@12oclockpoint.com>
 *
 * The file build.gradle.kts is part of PolyBootstrap
 * Last modified on 22-12-2021 06:57 p.m.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * POLYBOOTSTRAP IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

var mainClassName: String by application.mainClass
mainClassName = "ca.solostudios.polybot.bootstrap.Main"
group = "ca.solostudios.polybot.bootstrap"
val versionObj = Version("0", "1", "0")
version = versionObj

repositories {
    mavenCentral()
}

@Suppress("GradlePackageUpdate")
dependencies {
    // implementation(kotlin("stdlib"))
    // implementation(kotlin("reflect"))
    
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")
    
    // SLF4J
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("ca.solo-studios:slf4k:0.3.1") // SLF4J extension library
    implementation("ch.qos.logback:logback-classic:1.2.6")
    
    // Kotlin HTTP api
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-coroutines:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    // implementation("com.github.kittinunf.fuel:fuel-reactor:2.3.1") // Use Reactor??
    
    // Jackson (JSON object serialization/deserialization)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.toString()
            apiVersion = "1.5"
            languageVersion = "1.5"
        }
    }
    
    withType<ShadowJar> {
        mergeServiceFiles()
        setExcludes(listOf(""))
    }
    
    withType<Jar> {
        manifest {
            attributes(
                    "Main-Class" to mainClassName,
                    "Built-By" to (System.getProperty("user.name")),
                    "Built-Jdk" to (System.getProperty("java.version")),
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                      )
        }
    }
}

/**
 * Version class that does version stuff.
 */
data class Version(val major: String, val minor: String, val patch: String) {
    override fun toString(): String = "$major.$minor.$patch"
}

val env: Map<String, String?>
    get() = System.getenv()