// The compat recipe generator (docs/COMPAT_RECIPES_PLAN.md). Pure Java, no Minecraft.
// src/compat holds the hand-written sources; the generator writes the recipes and the runtime index to
// src/generated, which is committed (like NeoForge datagen output) and shipped by every node.
// CompatRecipesUpToDateTest (src/test) fails the build when the committed files don't match the sources.
plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.register<JavaExec>("generateCompatRecipes") {
    group = "dreamtvs"
    description = "Writes the TV compat recipes from src/compat into src/generated."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.dreamtvs.compatgen.CompatRecipeGenerator"
    val input = rootProject.file("src/compat")
    val output = rootProject.file("src/generated")
    args(input.absolutePath, output.absolutePath)
    inputs.dir(input)
    outputs.dir(output)
}
