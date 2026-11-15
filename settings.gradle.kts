pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    // One source tree for every Minecraft version and loader; see docs/DEVELOPMENT.md.
    id("dev.kikugie.stonecutter") version "0.10"
    // Applies fabric-loom-remap on obfuscated versions (1.21.x) and fabric-loom on 26.x.
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /** A node per loader, `versions/{mc}-{loader}`, built by `build.{loader}.gradle.kts`. */
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        match("1.21.1", "fabric", "neoforge")
        match("1.21.11", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        // The state of the sources as committed. Switch the IDE's version with the "Set active project" tasks.
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "dreamtvs"

// Pure Java tooling: the compat recipe generator (no Minecraft, no Stonecutter).
include("tools")
