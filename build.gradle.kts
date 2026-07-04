import java.util.Properties

plugins {
    java
    id("com.gradleup.shadow") version "9.4.3"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

val shade: Configuration = configurations.create("shade")
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.0.6"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    // Folia library repository
    maven {
        name = "tcoded-releases"
        url = uri("https://repo.tcoded.com/releases")
    }

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
    shade("org.postgresql:postgresql:42.7.12")
    shade("com.tcoded:FoliaLib:0.5.2")
    shade("org.bstats:bstats-bukkit:3.2.1")
    shade("dev.faststats.metrics:bukkit:0.27.1")

    // Paper bundles sqlite-jdbc on the server classpath; compileOnly is sufficient
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
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
    relocate("com.tcoded.folialib", "com.enhancedechest.libs.folialib")
    relocate("org.bstats",          "com.enhancedechest.libs.bstats")
    relocate("dev.faststats",       "com.enhancedechest.libs.faststats")

    mergeServiceFiles()
    // destinationDirectory.set(file("C:\\Users\\Admin\\Desktop\\TestServer\\plugins"))
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
        // modrinth("axvaults", "2.15.0")
        modrinth("viaversion", "5.10.0")
    }
    minecraftVersion("1.21.11")
}