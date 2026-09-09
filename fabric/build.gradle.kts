plugins {
    // Loom 1.17 is the line that knows about Minecraft 26.x; older lines cannot even find
    // the official Mojang mappings for it.
    id("fabric-loom") version "1.17.20"
}

val mc = property("minecraftVersion") as String
val jei = property("jeiVersion") as String

base { archivesName.set("craftbridge-client-$mc-fabric") }

sourceSets {
    main {
        // The shared half of the mod, compiled here against Fabric's Minecraft.
        java.srcDir("../common/src/main/java")
        resources.srcDir("../common/src/main/resources")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    // Mojang mappings, so the shared sources use the same names NeoForge does.
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabricLoaderVersion")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabricApiVersion")}")

    // Compile against JEI's loader-agnostic API; run against the full Fabric jar.
    modCompileOnly("mezz.jei:jei-$mc-common-api:$jei")
    modCompileOnly("mezz.jei:jei-$mc-fabric-api:$jei")
    modRuntimeOnly("mezz.jei:jei-$mc-fabric:$jei")
}

// The default client and server run configurations are left as Loom generates them: this is a
// client mod, but a dev server run costs nothing and one less bit of build DSL can break.
