import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// `:app` resolves this build through dependency substitution, so the coordinates have to match the
// `com.example.authnz:bridge-core` dependency declared there.
group = "com.example.authnz"
version = "0.0.1-SNAPSHOT"

// No Android dependency on purpose: everything here has to compile and run on a plain JVM.
dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // Matches the app module, whose bytecode target comes from android.compileOptions.
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
