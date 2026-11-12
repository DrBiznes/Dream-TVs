// Loader-agnostic code. Compiled against vanilla Minecraft (NeoForm, Mojang names) so nothing here can
// accidentally use Fabric or NeoForge classes. The loader projects compile these sources in themselves.
plugins {
    id("net.neoforged.moddev")
}

neoForge {
    neoFormVersion = providers.gradleProperty("neo_form_version").get()
}

dependencies {
    // Only the Dream Displays API classes are used; the NeoForge jar carries them unrelocated.
    compileOnly("maven.modrinth:dreamdisplays:${providers.gradleProperty("dreamdisplays_neoforge_id").get()}") {
        isTransitive = false
    }
}
