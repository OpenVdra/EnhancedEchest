import java.util.Properties

plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

val shade: Configuration = configurations.create("shade")
configurations {
    implementation.get().extendsFrom(shade)
}

group = "com.enhancedechest"
version = "1.3.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.faststats.dev/releases")
}

val faststatsToken: String = System.getenv("FASTSTATS_TOKEN")
    ?: rootProject.file("secrets.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("faststatsToken", "")
    }
    ?: (project.findProperty("faststatsToken") as String?)
    ?: ""

dependencies {
    compileOnly(libs.paper.api)

    shade(libs.hikaricp)
    shade(libs.mariadb)
    shade(libs.postgresql) {
        exclude(group = "org.checkerframework")
    }
    shade(libs.bstats)
    shade(libs.faststats)
    shade(libs.jedis) {
        // Paper already ships gson on the server classpath; don't bundle a second relocated copy.
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // From the server classpath; must NOT be relocated (see libs.versions.toml).
    compileOnly(libs.sqlite)
    compileOnly(libs.gson)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-nowarn", "-Xlint:-deprecation"))
}

// The deliverable is the shadow jar; keep the plain jar out of the way.
tasks.jar {
    archiveBaseName.set("EnhancedEchestPlain")
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("EnhancedEchest")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    configurations = listOf(shade)

    // INCLUDE so duplicate META-INF/services/* files reach mergeServiceFiles instead of being
    // dropped: the JDBC drivers each ship a java.sql.Driver service file, MariaDB several more.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
    exclude("org/slf4j/**")
    exclude("META-INF/native-image/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/licenses/**")
    exclude("org/checkerframework/**")
    exclude("com/google/errorprone/**")

    relocate("com.zaxxer.hikari", "com.enhancedechest.libs.hikari")
    relocate("org.mariadb.jdbc", "com.enhancedechest.libs.mariadb")
    relocate("org.postgresql", "com.enhancedechest.libs.postgresql")
    relocate("com.ongres", "com.enhancedechest.libs.ongres")
    relocate("org.bstats", "com.enhancedechest.libs.bstats")
    relocate("dev.faststats", "com.enhancedechest.libs.faststats")
    relocate("redis.clients", "com.enhancedechest.libs.jedis")
    relocate("org.apache.commons.pool2", "com.enhancedechest.libs.commonspool2")
    relocate("org.json", "com.enhancedechest.libs.json")
    // gson is intentionally not relocated — it resolves from the server classpath.

    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// A build is a "dev" build when its task graph includes run-paper's `runServer` (i.e. the developer
// is launching a local test server). A plain `build`/`shadowJar` — what produces the jar shipped to
// users — leaves this false. Baked into build-info.properties below so the plugin can gate
// developer-only logging out of release jars.
val devBuild = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').equals("runServer", ignoreCase = true)
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
    // Bake the dev/release flag into build-info.properties (see devBuild above).
    inputs.property("devBuild", devBuild)
    filesMatching("build-info.properties") {
        expand("devBuild" to devBuild)
    }
}

tasks.runServer {
    downloadPlugins {
        modrinth("viaversion", "5.10.0")
    }
    minecraftVersion("1.21.11")
}
