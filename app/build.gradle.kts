import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

abstract class GitHashTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val workDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val hash = readGitHash()
        val dir = outputDir.get().asFile.resolve("com/brian/solwidget")
        dir.mkdirs()
        val file = dir.resolve("GitHash.kt")
        val text = """
            package com.brian.solwidget

            object GitHash {
                const val VALUE: String = "$hash"
            }
        """.trimIndent() + "\n"
        if (!file.exists() || file.readText() != text) {
            file.writeText(text)
        }
    }

    private fun readGitHash(): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
                .directory(workDir.get().asFile)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.waitFor() == 0 && text.matches(Regex("[0-9a-fA-F]{7,40}"))) {
                text.take(7)
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}

android {
    namespace = "com.brian.solwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brian.solwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug keystore so :installRelease is available without a production keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets.named("main") {
        java.srcDir(layout.buildDirectory.dir("generated/source/gitHash"))
    }
}

val generateGitHash = tasks.register<GitHashTask>("generateGitHash") {
    outputDir.set(layout.buildDirectory.dir("generated/source/gitHash"))
    workDir.set(rootProject.layout.projectDirectory)
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(generateGitHash)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
