import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.5.30"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

var mainClassName: String by application.mainClass
mainClassName = "ca.solostudios.polybot.bootstrap.Main"
group = "ca.solostudios.polybot.bootstrap"
val versionObj = Version("0", "0", "0")
version = versionObj

repositories {
    mavenCentral()
}

@Suppress("GradlePackageUpdate")
dependencies {
    // implementation(kotlin("stdlib"))
    // implementation(kotlin("reflect"))
    
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.3")
    
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")
    
    // SLF4J
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("ca.solo-studios:slf4k:0.3.1") // SLF4J extension library
    implementation("ch.qos.logback:logback-classic:1.2.6")
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
            // freeCompilerArgs += "-Xopt-in=kotlin.time.ExperimentalTime"
        }
    }
    
    withType<ShadowJar>() {
        // com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
        mergeServiceFiles()
        setExcludes(listOf(""))
    }
    
    withType<Jar>() {
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