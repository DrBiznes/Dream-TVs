plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val common = project(":common")
fun prop(name: String) = providers.gradleProperty(name).get()

base { archivesName.set("${prop("mod_id")}-fabric-${prop("minecraft_version")}") }

sourceSets.main {
    java.srcDir(common.file("src/main/java"))
    resources.srcDir(common.file("src/main/resources"))
}

repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    "minecraft"("com.mojang:minecraft:${prop("minecraft_version")}")
    "mappings"(loom.officialMojangMappings())
    "modImplementation"("net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
    "modImplementation"("maven.modrinth:dreamdisplays:${prop("dreamdisplays_fabric_id")}")
}

loom {
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            runDir("../run/fabric-client")
            ideConfigGenerated(true)
        }
        named("server") {
            server()
            configName = "Fabric Server"
            runDir("../run/fabric-server")
            ideConfigGenerated(true)
        }
    }
}

@Suppress("UNCHECKED_CAST")
val modProperties = rootProject.extra["modProperties"] as Map<String, String>

tasks.processResources {
    inputs.properties(modProperties)
    filesMatching("fabric.mod.json") { expand(modProperties) }
}
