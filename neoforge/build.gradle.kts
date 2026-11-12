plugins {
    id("net.neoforged.moddev")
}

val common = project(":common")
fun prop(name: String) = providers.gradleProperty(name).get()

base { archivesName.set("${prop("mod_id")}-neoforge-${prop("minecraft_version")}") }

sourceSets.main {
    java.srcDir(common.file("src/main/java"))
    resources.srcDir(common.file("src/main/resources"))
}

// GameTests (plan §8) live in their own source set so they never ship in the mod jar.
val gametest: SourceSet = sourceSets.create("gametest")
configurations[gametest.compileClasspathConfigurationName].extendsFrom(configurations.compileClasspath.get())
configurations[gametest.runtimeClasspathConfigurationName].extendsFrom(configurations.runtimeClasspath.get())
dependencies {
    "gametestImplementation"(sourceSets.main.get().output)
}

neoForge {
    version = prop("neoforge_version")
    addModdingDependenciesTo(gametest)

    runs {
        // gradlew :neoforge:runGameTestServer  (exits non-zero if a test fails)
        create("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = file("../run/neoforge-gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", prop("mod_id"))
        }
        create("client") {
            client()
            gameDirectory = file("../run/neoforge-client")
        }
        create("server") {
            server()
            programArgument("--nogui")
            gameDirectory = file("../run/neoforge-server")
        }
    }

    mods {
        create(prop("mod_id")) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }
}

dependencies {
    implementation("maven.modrinth:dreamdisplays:${prop("dreamdisplays_neoforge_id")}")
}

@Suppress("UNCHECKED_CAST")
val modProperties = rootProject.extra["modProperties"] as Map<String, String>

tasks.processResources {
    inputs.properties(modProperties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(modProperties) }
}
