import java.util.Properties

plugins {
    java
}

// Every setting is read with providers.gradleProperty rather than project.property: the latter
// also sees plugin-supplied properties, and `javaVersion` collides with one of those (it comes
// back as a Gradle JavaVersion object, not our string). gradleProperty reads gradle.properties
// and -P only, so what a build file asks for is what gradle.properties says.
fun Project.setting(name: String): String = providers.gradleProperty(name).get()

// The Minecraft-specific versions live in versions/<mcTarget>.properties, one file per target,
// so 26.2 and 26.3 build from the same sources with nothing but -PmcTarget to tell them apart.
// Each subproject gets them as extra properties: `extra["minecraftVersion"]` and so on.
val mcTarget = setting("mcTarget")
val targetFile = file("versions/$mcTarget.properties")
require(targetFile.isFile) {
    "mcTarget=$mcTarget has no versions/$mcTarget.properties; known targets: " +
        file("versions").list()!!.sorted().joinToString { it.removeSuffix(".properties") }
}
val target: Map<String, String> = Properties()
    .apply { targetFile.reader().use { load(it) } }
    .entries.associate { (k, v) -> k.toString() to v.toString() }

subprojects {
    apply(plugin = "java")

    target.forEach { (name, value) -> extra[name] = value }

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
            "minecraftVersion" to target.getValue("minecraftVersion"),
            "minecraftVersionRange" to target.getValue("minecraftVersionRange"),
            "fabricLoaderVersion" to target.getValue("fabricLoaderVersion"),
            "neoforgeVersion" to target.getValue("neoforgeVersion"),
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(props)
        }
    }
}
