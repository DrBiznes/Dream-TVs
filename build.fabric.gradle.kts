// Build script for every Fabric node (versions/{mc}-fabric). Sources are the shared src/ tree, with Stonecutter
// comments resolved for this node; NeoForge-only files compile to empty classes here.
plugins {
    // fabric-loom-remap on 1.21.x, fabric-loom on 26.x (unobfuscated)
    id("dev.kikugie.loom-back-compat")
}

fun prop(name: String): String = sc.properties[name]
val mc: String = sc.current.version
val javaVersion: Int = prop("deps.java").toInt()

group = prop("mod.group")
version = prop("mod.version")
base.archivesName = "${prop("mod.id")}-fabric-$mc"

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

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImplementation("maven.modrinth:dreamdisplays:${prop("deps.dreamdisplays_id")}")
    // Optional: Mod Menu opens the settings screen (DreamTvsModMenu). In the dev client so it can be tried.
    modCompileOnly("maven.modrinth:modmenu:${prop("deps.modmenu")}")
    modLocalRuntime("maven.modrinth:modmenu:${prop("deps.modmenu")}")
    // Optional: when LuckPerms is installed we check its nodes, like Dream Displays does (plan §5.7).
    compileOnly("net.luckperms:api:${prop("deps.luckperms_api")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":tools"))
}

loom {
    runs {
        named("client") {
            client()
            configName = "Fabric Client $mc"
            runDir("../../run/fabric-$mc-client")
            ideConfigGenerated(true)
        }
        named("server") {
            server()
            configName = "Fabric Server $mc"
            runDir("../../run/fabric-$mc-server")
            ideConfigGenerated(true)
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
        "mod_id" to prop("mod.id"),
        "mod_name" to prop("mod.name"),
        "mod_version" to prop("mod.version"),
        "mod_description" to prop("mod.description"),
        "mod_license" to prop("mod.license"),
        "minecraft_compat" to prop("mod.mc_compat"),
        "java_version" to javaVersion.toString(),
        "fabric_loader_version" to prop("deps.fabric_loader"),
        "dreamdisplays_version" to prop("deps.dreamdisplays"),
        "pack_formats" to packFormats,
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("dreamtvs.mixins.json") { expand(props) }
    filesMatching("resourcepacks/*/pack.mcmeta") { expand(props) }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { rename { "LICENSE_dreamtvs" } }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds the mod jar and copies it to build/libs/{mod version}/ in the root project."
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}
