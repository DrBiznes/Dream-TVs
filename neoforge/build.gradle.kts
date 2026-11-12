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

neoForge {
    version = prop("neoforge_version")

    runs {
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
