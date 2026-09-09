plugins {
    java
}

// Every setting is read with providers.gradleProperty rather than project.property: the latter
// also sees plugin-supplied properties, and `javaVersion` collides with one of those (it comes
// back as a Gradle JavaVersion object, not our string). gradleProperty reads gradle.properties
// and -P only, so what a build file asks for is what gradle.properties says.
fun Project.setting(name: String): String = providers.gradleProperty(name).get()

subprojects {
    apply(plugin = "java")

    group = setting("modGroup")
    version = setting("modVersion")

    repositories {
        mavenCentral()
        maven("https://maven.blamejared.com") { name = "JEI" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }

    val javaTarget = setting("javaVersion").toInt()

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaTarget))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaTarget)
    }

    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf(
            "modId" to setting("modId"),
            "modVersion" to setting("modVersion"),
            "minecraftVersion" to setting("minecraftVersion"),
            "fabricLoaderVersion" to setting("fabricLoaderVersion"),
            "neoforgeVersion" to setting("neoforgeVersion"),
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(props)
        }
    }
}
