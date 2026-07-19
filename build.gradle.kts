import java.util.Properties

plugins {
    java
    id("com.gradleup.shadow") version "9.6.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val shade: Configuration = configurations.create("shade")
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.0.12"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    // FastStats metrics SDK
    maven {
        name = "faststatsReleases"
        url = uri("https://repo.faststats.dev/releases")
    }
}

// FastStats project token — kept OUT of source control. Resolved at build time from, in order:
//   1) FASTSTATS_TOKEN environment variable
//   2) faststatsToken in a gitignored secrets.properties at the repo root
//   3) faststatsToken Gradle property (e.g. ~/.gradle/gradle.properties)
// If none is set the token bakes in empty and the plugin simply skips FastStats at runtime.
val faststatsToken: String = System.getenv("FASTSTATS_TOKEN")
    ?: rootProject.file("secrets.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
            .getProperty("faststatsToken", "")
    }
    ?: (project.findProperty("faststatsToken") as String?)
    ?: ""

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Shaded and relocated — no server-side drivers required
    shade("com.zaxxer:HikariCP:7.1.0")
    shade("org.mariadb.jdbc:mariadb-java-client:3.5.9")   // compatible with MySQL 5.7+ and 8.x
    shade("org.postgresql:postgresql:42.7.13")
    shade("org.bstats:bstats-bukkit:3.2.1")
    shade("dev.faststats.metrics:bukkit:0.28.0")
    // Redis client for the cross-server owner-lock coordination (cross-server.enabled)
    shade("redis.clients:jedis:7.5.3")

    // Paper bundles sqlite-jdbc on the server classpath; compileOnly is sufficient
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    // Test-only: JUnit 5 + a real sqlite driver (compileOnly for the plugin, Paper bundles it at
    // runtime) + an slf4j binding so CachedStorage's logger has somewhere to print.
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.slf4j:slf4j-simple:2.0.18")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.53.2.0")

    // MockBukkit — mocks the Bukkit/Paper server so Bukkit-dependent code (listeners, scheduler,
    // commands) can run under plain JUnit instead of a live server. Pinned to the "v1.21" artifact
    // specifically because it targets paper-api 1.21.11 (the exact version this plugin compiles
    // against) on Java 21 (this project's toolchain) — the newer "v26.x" artifact line requires
    // Java 25 and would conflict with the toolchain above. MockBukkit declares paper-api as
    // compileOnly itself (not api), so it isn't pulled in transitively — repeat it here for tests
    // (compileOnly above doesn't reach the test source set either).
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

// The normal `test` task stays fast: it skips the heavy load simulation.
tasks.test {
    useJUnitPlatform()
    exclude("**/*Simulation*")
}

// ./gradlew stressTest — the 300–500 player concurrency/perf/leak simulation (no server needed).
tasks.register<Test>("stressTest") {
    description = "Concurrent 300–500 player load simulation against CachedStorage + SQLite."
    group = "verification"
    // A manually registered Test task must be pointed at the test source set explicitly
    // (unlike the built-in `test` task) or it reports NO-SOURCE.
    val testSourceSet = sourceSets["test"]
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform()
    include("**/*Simulation*")
    maxHeapSize = "384m"
    outputs.upToDateWhen { false }   // always re-run
    testLogging {
        showStandardStreams = true   // print the simulation report to the console
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:-deprecation"))
}

// Plain jar is not the deliverable; use shadowJar instead
tasks.jar {
    archiveBaseName.set("EnhancedEchestPlain")
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("EnhancedEchest")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    configurations = listOf(shade)

    // Let duplicate entries reach the transformers (mergeServiceFiles). The task default is
    // EXCLUDE, which drops duplicate META-INF/services/* files — the JDBC drivers each ship a
    // java.sql.Driver service file, and MariaDB ships several plugin service files — before the
    // ServiceFileTransformer can merge them, silently losing driver/plugin registrations.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    exclude("org/slf4j/**")

    relocate("com.zaxxer.hikari", "com.enhancedechest.libs.hikari")
    relocate("org.mariadb.jdbc", "com.enhancedechest.libs.mariadb")
    relocate("org.postgresql",   "com.enhancedechest.libs.postgresql")
    relocate("com.ongres",       "com.enhancedechest.libs.ongres")
    relocate("org.bstats",          "com.enhancedechest.libs.bstats")
    relocate("dev.faststats",       "com.enhancedechest.libs.faststats")
    relocate("redis.clients",       "com.enhancedechest.libs.jedis")
    relocate("org.apache.commons.pool2", "com.enhancedechest.libs.commonspool2")
    relocate("org.json",            "com.enhancedechest.libs.json")
    relocate("com.google.gson",     "com.enhancedechest.libs.gson")

    mergeServiceFiles()
    // destinationDirectory.set(file("C:\\Users\\Admin\\Desktop\\Folia\\plugins"))
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(props)
    }
    // Bake the FastStats token into faststats.properties at build time so it never lives in source.
    inputs.property("faststatsToken", faststatsToken)
    filesMatching("faststats.properties") {
        expand("faststatsToken" to faststatsToken)
    }
}

tasks.runServer {
    downloadPlugins {
        // modrinth("luckperms", "v5.5.53-bukkit")
        modrinth("viaversion", "5.10.0")
    }
    minecraftVersion("1.21.11")
}