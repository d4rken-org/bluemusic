plugins {
    id("projectConfig")
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
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