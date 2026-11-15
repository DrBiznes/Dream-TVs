// Build script for every NeoForge node (versions/{mc}-neoforge). Sources are the shared src/ tree, with
// Stonecutter comments resolved for this node; Fabric-only files compile to empty classes here.
plugins {
    id("net.neoforged.moddev") version "2.0.148"
    id("neoforge-mutex")
}

fun prop(name: String): String = sc.properties[name]
fun propOrNull(name: String): String? = sc.properties.getOrNull(name)
val mc: String = sc.current.version
val javaVersion: Int = prop("deps.java").toInt()
val modId = prop("mod.id")

group = prop("mod.group")
version = prop("mod.version")
base.archivesName = "$modId-neoforge-$mc"

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    mavenCentral()
}

sourceSets.main {
    // The recipes and their index, written by `gradlew :tools:generateCompatRecipes`. 1.21.2 changed how
    // recipes name their ingredients, so there's one recipe folder per format.
    resources.srcDir(rootProject.file("src/generated/common"))
    // Hand-written data whose format changed: loot tables (26.x replaced `conditions` lists with `condition`).
    resources.srcDir(rootProject.file(if (sc.current.parsed >= "26.1") "src/main/resources-26" else "src/main/resources-1.21"))
    resources.srcDir(rootProject.file(if (sc.current.parsed >= "1.21.2") "src/generated/recipes-1.21.2" else "src/generated/recipes-1.21.1"))
}

// GameTests (plan §8) live in their own source set so they never ship in the mod jar.
val gametest: SourceSet = sourceSets.create("gametest")
configurations[gametest.compileClasspathConfigurationName].extendsFrom(configurations.compileClasspath.get())
configurations[gametest.runtimeClasspathConfigurationName].extendsFrom(configurations.runtimeClasspath.get())

dependencies {
    "gametestImplementation"(sourceSets.main.get().output)
    implementation("maven.modrinth:dreamdisplays:${prop("deps.dreamdisplays_id")}")
    // Optional: when LuckPerms is installed we check its nodes, like Dream Displays does (plan §5.7).
    compileOnly("net.luckperms:api:${prop("deps.luckperms_api")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":tools"))
    // Minecraft's libraries aren't on MDG's test classpath; DreamTvsConfig's parser needs Gson.
    testImplementation("com.google.code.gson:gson:2.10.1")
}

neoForge {
    version = prop("deps.neoforge")
    addModdingDependenciesTo(gametest)

    runs {
        // gradlew :1.21.1-neoforge:runGameTestServer  (exits non-zero if a test fails)
        register("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametest
            gameDirectory = rootProject.file("run/neoforge-$mc-gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        register("client") {
            client()
            gameDirectory = rootProject.file("run/neoforge-$mc-client")
            programArguments.addAll("--username", "Dev")
        }
        // A second player for multiplayer tests (plan M3 checklist). Not an op by default.
        register("client2") {
            client()
            gameDirectory = rootProject.file("run/neoforge-$mc-client2")
            programArguments.addAll("--username", "Dev2")
        }
        register("server") {
            server()
            programArgument("--nogui")
            gameDirectory = rootProject.file("run/neoforge-$mc-server")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(gametest)
        }
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.test {
    useJUnitPlatform()
    systemProperty("dreamtvs.root", rootProject.projectDir.absolutePath)
}

// The built-in resource packs (resourcepacks/ in the jar) say which resource format they're for. Formats up to
// 64 are a single "pack_format"; later ones are a "min_format" and "max_format".
val packFormats: String = prop("mod.resource_format").toInt().let {
    if (it > 64) "\"min_format\": $it, \"max_format\": $it" else "\"pack_format\": $it"
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_description" to prop("mod.description"),
        "mod_license" to prop("mod.license"),
        "minecraft_compat" to prop("mod.mc_compat"),
        "java_version" to javaVersion.toString(),
        "neoforge_version" to prop("deps.neoforge"),
        "dreamdisplays_version" to prop("deps.dreamdisplays"),
        "pack_formats" to packFormats,
        // NeoForge for 26.x deprecated logoFile; our icon is square, so it's an iconFile there.
        "icon_key" to if (sc.current.parsed >= "26.1") "iconFile" else "logoFile",
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    filesMatching("dreamtvs.mixins.json") { expand(props) }
    filesMatching("resourcepacks/*/pack.mcmeta") { expand(props) }
    exclude("fabric.mod.json")
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_dreamtvs" } }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the mod jar and copies it to build/libs/{mod version}/ in the root project."
    from(tasks.jar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}

// Optional mods for compatibility testing (plan M3: Sable sub-levels), 1.21.1 only. Never on the compile
// classpath and not in the GameTest run. `installTestMods` copies them into the client, client2 and server
// mods/ folders; `removeTestMods` takes them out again. Versions are Modrinth version IDs.
val testModIds = listOf("sable", "create", "create-aeronautics", "kotlin-for-forge", "dreamdisplays-sable")
    .associateWith { propOrNull("test.${it.replace('-', '_')}") }
if (testModIds.values.all { it != null }) {
    val testMods: Configuration by configurations.creating { isTransitive = false }
    dependencies {
        for ((project, id) in testModIds) testMods("maven.modrinth:$project:$id")
    }
    val testModDirs = listOf("client", "client2", "server").map { rootProject.file("run/neoforge-$mc-$it/mods") }

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
}
