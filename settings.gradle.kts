pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    // mc-run is served by JitPack, which rewrites the groupId, so the plugin marker does not
    // resolve. Map the plugin id to the JitPack module by hand. The id keeps the old "n1ght3r"
    // namespace on purpose: it is what mc-run itself declares, only the GitHub owner was renamed.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.n1ght3r.mc-run") {
                useModule("com.github.whoisnighter:mc-run:${requested.version}")
            }
        }
    }
}

plugins {
    // Downloads the JDK that runServer/runClient need (21 for 1.21.x, 25 for 26.x) when missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "EnhancedEchest"