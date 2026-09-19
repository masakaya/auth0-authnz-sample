rootProject.name = "android"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// bridge-core is a build of its own rather than a subproject of this build, because applying the
// Android Gradle plugin anywhere in a build fails when no SDK is installed. Keeping it separate
// lets `./gradlew -p android/bridge-core test` run the transport-independent logic on a machine
// that has no Android SDK, while `:app` still consumes it through dependency substitution.
includeBuild("bridge-core")

include(":app")
