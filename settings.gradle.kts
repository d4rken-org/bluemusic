pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://androidx.dev/snapshots/builds/13741527/artifacts/repository")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://androidx.dev/snapshots/builds/13508953/artifacts/repository")
        }
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Bluetooth Volume Manager"
include(":app")