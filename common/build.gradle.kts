// Loader-agnostic code. Compiled against vanilla Minecraft (NeoForm, Mojang names) so nothing here can
// accidentally use Fabric or NeoForge classes. The loader projects compile these sources in themselves.
plugins {
    id("net.neoforged.moddev")
}

neoForge {
    neoFormVersion = providers.gradleProperty("neo_form_version").get()
}

repositories {
    maven("https://maven.fabricmc.net/") { content { includeGroup("net.fabricmc") } }
}

dependencies {
    // Mixin annotations for com.dreamtvs.mixin (both loaders ship Mixin at runtime).
    compileOnly("net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7") { isTransitive = false }
    // The Dream Displays API classes, plus the few internals in DdInternals / DisplayManagerMixin;
    // the NeoForge jar carries them unrelocated.
    compileOnly("maven.modrinth:dreamdisplays:${providers.gradleProperty("dreamdisplays_neoforge_id").get()}") {
        isTransitive = false
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // DreamTvsConfig's parser; the version Minecraft 1.21.1 ships (the game's libraries aren't on the test classpath).
    testImplementation("com.google.code.gson:gson:2.10.1")
}

// The solver and planner (com.dreamtvs.screen.solve) and the config parser are pure Java and tested here.
tasks.test {
    useJUnitPlatform()
}
