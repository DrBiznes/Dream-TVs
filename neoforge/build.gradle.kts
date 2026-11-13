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
            programArguments.addAll("--username", "Dev")
        }
        // A second player for multiplayer tests (plan M3 checklist). Not an op by default.
        create("client2") {
            client()
            gameDirectory = file("../run/neoforge-client2")
            programArguments.addAll("--username", "Dev2")
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

// Optional mods for compatibility testing (plan M3: Sable sub-levels). Never on the compile classpath and not
// in the GameTest run. `gradlew :neoforge:installTestMods` copies them into the client, client2 and server
// mods/ folders; `:neoforge:removeTestMods` takes them out again. Versions are Modrinth version IDs.
val testMods: Configuration by configurations.creating {
    isTransitive = false
}
dependencies {
    testMods("maven.modrinth:sable:${prop("test_sable_id")}")
    testMods("maven.modrinth:create:${prop("test_create_id")}")
    testMods("maven.modrinth:create-aeronautics:${prop("test_aeronautics_id")}")
    testMods("maven.modrinth:kotlin-for-forge:${prop("test_kotlinforforge_id")}")
    testMods("maven.modrinth:dreamdisplays-sable:${prop("test_dreamdisplays_sable_id")}")
}
val testModDirs = listOf("neoforge-client", "neoforge-client2", "neoforge-server").map { rootProject.file("run/$it/mods") }

tasks.register("installTestMods") {
    group = "dreamtvs"
    description = "Copies Sable, Create, Aeronautics and dreamdisplays-sable into the dev run mods/ folders."
    val jars = testMods
    inputs.files(jars)
    doLast {
        for (dir in testModDirs) {
            dir.mkdirs()
            jars.files.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
        }
        logger.lifecycle("Installed ${jars.files.size} test mods into ${testModDirs.joinToString { it.parentFile.name }}")
    }
}

tasks.register("removeTestMods") {
    group = "dreamtvs"
    description = "Removes the mods installTestMods copied."
    val jars = testMods
    doLast {
        val names = jars.files.map { it.name }.toSet()
        for (dir in testModDirs) dir.listFiles()?.filter { it.name in names }?.forEach { it.delete() }
    }
}

@Suppress("UNCHECKED_CAST")
val modProperties = rootProject.extra["modProperties"] as Map<String, String>

tasks.processResources {
    inputs.properties(modProperties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(modProperties) }
}
