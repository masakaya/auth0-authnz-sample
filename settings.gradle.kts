rootProject.name = "auth0-authnz-sample"

// Composite build: each included build has its own settings file and plugin classpath.
// Tasks are addressed from the root as `./gradlew :backend:test`.
includeBuild("backend")

// The Android build is opt-in so that machines without the Android SDK can still build the backend.
//   -PincludeAndroid=true  include it
//   -PrequireAndroid       include it and fail when it cannot be included (use this in CI)
val requireAndroid = providers.gradleProperty("requireAndroid").isPresent
val includeAndroid = requireAndroid ||
    providers.gradleProperty("includeAndroid").map { it.toBoolean() }.getOrElse(false)

if (includeAndroid) {
    val androidDir = file("android")
    val sdkConfigured = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        androidDir.resolve("local.properties").exists()
    val problem = when {
        !androidDir.resolve("settings.gradle.kts").exists() -> "android/settings.gradle.kts does not exist"
        !sdkConfigured -> "Android SDK is not configured (set ANDROID_HOME or create android/local.properties)"
        else -> null
    }
    when {
        problem == null -> includeBuild("android")
        requireAndroid -> throw GradleException("Android build is required but cannot be included: $problem")
        else -> logger.warn("Android build is NOT included: $problem")
    }
} else {
    logger.lifecycle("Android build is not included (pass -PincludeAndroid=true to include it)")
}
