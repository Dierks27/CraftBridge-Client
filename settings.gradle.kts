pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
        gradlePluginPortal()
        mavenCentral()
    }
}

// There is deliberately no `common` subproject: the shared sources are a plain directory
// that each loader module compiles itself, against its own Minecraft. Both use official
// Mojang mappings, so one copy of the shared code compiles unchanged on both sides, and
// there is no Architectury layer to keep in step with two moving toolchains.
rootProject.name = "craftbridge-client"
include("fabric")
include("neoforge")
