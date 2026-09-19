import org.springframework.boot.gradle.plugin.SpringBootPlugin

// The Flyway Gradle plugin (org.flywaydb.flyway) resolves database support (here,
// flyway-database-postgresql) and the JDBC driver from its OWN classloader, which is
// distinct from the project's compile/runtime classpath even though both are declared
// with the plugins{} DSL below. Adding them as ordinary project dependencies (tried first)
// still leaves flywayMigrate failing with "No Flyway database plugin found to handle ...",
// because the DatabaseType registry the plugin's bundled flyway-core uses is only
// populated from the classloader the plugin itself was loaded with. Declaring them here,
// on the buildscript classpath, puts them in that same classloader.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.postgresql:postgresql:42.7.13")
        classpath("org.flywaydb:flyway-database-postgresql:13.7.0")
    }
}

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.flyway)
}

group = "com.example.authnz"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    // Used only by LedgerAuthorityResolver / CustomerLedgerRepository (app.auth.authority-source=ledger).
    // flyway-core is deliberately NOT an application dependency: migrations are run out-of-band
    // (the flywayMigrate task below, or the compose Flyway CLI container), never at app startup.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Needed for LedgerAuthorityResolver / CustomerLedgerRepository's own JDBC connection at
    // application runtime. Also present on "runtimeClasspath" for flywayMigrate to locate the
    // db/migration resources on (see the flyway{} block below); the driver class itself for
    // flywayMigrate's own connection comes from the buildscript classpath above.
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Ledger-mode security tests run their own Postgres via Testcontainers and apply the
    // migrations themselves with Flyway, so they never depend on a developer's local
    // `docker compose up` state.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x renamed every module artifact with a "testcontainers-" prefix
    // (verified against org.testcontainers:testcontainers-bom:2.0.5, the version Spring
    // Boot 4.1.1 manages): it is "testcontainers-junit-jupiter", not "junit-jupiter".
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // Run in a single, ordinary JVM classpath (no Gradle-plugin classloader isolation), so
    // unlike the buildscript-only pair above, ServiceLoader discovery just works here.
    testImplementation("org.flywaydb:flyway-core:${libs.versions.flyway.get()}")
    testImplementation("org.flywaydb:flyway-database-postgresql:${libs.versions.flyway.get()}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Flyway CLI tasks (flywayMigrate / flywayInfo / flywayClean) against the customer ledger
// database started by the repository root's compose.yaml. Connection defaults match that
// file; override with DB_URL / DB_USER / DB_PASSWORD for a different database.
flyway {
    url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:54329/authnz"
    user = System.getenv("DB_USER") ?: "authnz"
    password = System.getenv("DB_PASSWORD") ?: "authnz"
    // This is a disposable development database (see compose.yaml); flywayClean is expected to work.
    cleanDisabled = false
    // Restrict which project configurations the plugin scans (for the compiled
    // db/migration resources) to runtimeClasspath; the default list also includes
    // testCompileClasspath / testRuntimeClasspath, which would pull in test-only resources.
    configurations = arrayOf("runtimeClasspath")
}
