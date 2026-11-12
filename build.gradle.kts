plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.17.13" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

val modGroup = providers.gradleProperty("mod_group").get()
val modVersion = providers.gradleProperty("mod_version").get()
val javaVersion = providers.gradleProperty("java_version").get().toInt()

subprojects {
    apply(plugin = "java")

    group = modGroup
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://api.modrinth.com/maven") {
            name = "Modrinth"
            content { includeGroup("maven.modrinth") }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion)
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) { rename { "LICENSE_dreamtvs" } }
    }
}

/** Properties expanded into fabric.mod.json / neoforge.mods.toml. */
extra["modProperties"] = listOf(
    "mod_id", "mod_name", "mod_version", "mod_description", "mod_license",
    "minecraft_version", "minecraft_version_range", "java_version",
    "neoforge_version", "neoforge_loader_range", "fabric_loader_version", "dreamdisplays_version",
).associateWith { providers.gradleProperty(it).get() }
