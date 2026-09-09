plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = property("modGroup") as String
    version = property("modVersion") as String

    repositories {
        mavenCentral()
        maven("https://maven.blamejared.com") { name = "JEI" }
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of((property("javaVersion") as String).toInt()))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set((property("javaVersion") as String).toInt())
    }

    tasks.withType<ProcessResources>().configureEach {
        val props = mapOf(
            "modId" to project.property("modId"),
            "modVersion" to project.version,
            "minecraftVersion" to project.property("minecraftVersion"),
            "fabricLoaderVersion" to project.property("fabricLoaderVersion"),
            "neoforgeVersion" to project.property("neoforgeVersion"),
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(props)
        }
    }
}
