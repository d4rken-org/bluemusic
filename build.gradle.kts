plugins {
    id("projectConfig")
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.Kotlin.core}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${Versions.Dagger.core}")
    }
}

// Repositories are now managed in settings.gradle.kts via dependencyResolutionManagement
allprojects {
    // repositories moved to settings.gradle.kts
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}