import java.util.Properties

plugins {
    // AGP 9 carries its own Kotlin Gradle plugin, so org.jetbrains.kotlin.android is not applied.
    alias(libs.plugins.android.application)
}

/**
 * Settings are looked up in android/local.properties first (that file is never committed), then in
 * Gradle properties, then in the environment. Every one of them has a placeholder default so that
 * a debug build succeeds on a machine - or a CI runner - where nothing is configured.
 */
val localSettings = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun setting(key: String, environmentVariable: String, fallback: String): String =
    localSettings.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: providers.environmentVariable(environmentVariable).orNull
        ?: fallback

val auth0Domain = setting("auth0.domain", "AUTH0_DOMAIN", "example.auth0.com")
val auth0ClientId = setting("auth0.clientId", "AUTH0_CLIENT_ID", "")
val auth0Audience = setting("auth0.audience", "AUTH0_AUDIENCE", "https://api.example.com")

// Android App Links. Keep this on https; a custom scheme can be claimed by any other app.
val auth0Scheme = setting("auth0.scheme", "AUTH0_SCHEME", "https")

// Where the WebView points. On a device, `adb reverse tcp:4200 tcp:4200` makes localhost reachable.
val webAppUrl = setting("webApp.url", "WEB_APP_URL", "http://localhost:4200")

/*
 * A release build must never point the WebView at a plain http origin: the access token the bridge
 * hands over would then cross the network in the clear, and the debug-only network security
 * configuration that permits cleartext is not part of a release build anyway.
 *
 * The check is stated twice on purpose. Asking for a release build by name fails right away during
 * configuration, and the release outputs also depend on a task that fails, which covers the
 * aggregate tasks that reach a release output without naming it.
 */
val webAppUrlIsSecure = webAppUrl.startsWith("https://")
val insecureWebAppUrlMessage =
    "webApp.url must be https in a release build, but it is \"$webAppUrl\". " +
        "Set webApp.url in android/local.properties, as -PwebApp.url=... or as WEB_APP_URL."

if (!webAppUrlIsSecure) {
    val releaseRequested = gradle.startParameter.taskNames.any { requested ->
        val name = requested.substringAfterLast(':')
        name.startsWith("assembleRelease") ||
            name.startsWith("bundleRelease") ||
            name.startsWith("installRelease")
    }
    if (releaseRequested) throw GradleException(insecureWebAppUrlMessage)
}

val verifyReleaseWebAppUrl = tasks.register("verifyReleaseWebAppUrl") {
    group = "verification"
    description = "Fails when a release build would point the WebView at a plain http origin."
    doLast {
        if (!webAppUrlIsSecure) throw GradleException(insecureWebAppUrlMessage)
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseWebAppUrl)
}

android {
    namespace = "com.example.authnz.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.authnz.app"

        // Auth0.Android 4.x requires API 26 or later.
        minSdk {
            version = release(26)
        }
        targetSdk {
            version = release(36)
        }
        versionCode = 1
        versionName = "0.1.0"

        // The callback activity is declared by the Auth0.Android manifest with these placeholders.
        manifestPlaceholders["auth0Domain"] = auth0Domain
        manifestPlaceholders["auth0Scheme"] = auth0Scheme

        buildConfigField("String", "AUTH0_DOMAIN", "\"$auth0Domain\"")
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"$auth0ClientId\"")
        buildConfigField("String", "AUTH0_AUDIENCE", "\"$auth0Audience\"")
        buildConfigField("String", "AUTH0_SCHEME", "\"$auth0Scheme\"")
        buildConfigField("String", "WEB_APP_URL", "\"$webAppUrl\"")
    }

    buildFeatures {
        // AGP 9 no longer generates BuildConfig unless it is asked for.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Stated rather than assumed: the module has no Java sources, only Kotlin ones.
    sourceSets {
        getByName("main") { kotlin.directories.add("src/main/kotlin") }
    }
}

dependencies {
    // Substituted by the bridge-core build that android/settings.gradle.kts includes.
    implementation("com.example.authnz:bridge-core:0.0.1-SNAPSHOT")

    implementation(libs.auth0.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.biometric)
}
