plugins {
    // `net.fabricmc.fabric-loom`, not the older `fabric-loom` id: this one is the marker that
    // turns obfuscation handling off entirely, which is what an unobfuscated Minecraft needs.
    // With the plain id, Loom still insists on a `mappings` dependency there are no mappings
    // for. 1.17 is the line that knows about Minecraft 26.x at all.
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

val mc = providers.gradleProperty("minecraftVersion").get()
val jei = providers.gradleProperty("jeiVersion").get()

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
    // No `mappings(...)` line, deliberately. Minecraft has shipped unobfuscated since the
    // 1.21.11 snapshots: there is no client_mappings download to fetch any more. The game
    // already has real names, which is also why the shared sources compile unchanged against
    // NeoForge's copy of it.
    // Plain configurations, not modImplementation/modCompileOnly: those exist only to remap a
    // dependency into the development mappings, and Loom does not create them at all when there
    // is no obfuscation to undo. Mod jars are now ordinary jars.
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("fabricLoaderVersion").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabricApiVersion").get()}")

    // Compile against JEI's loader-agnostic API; run against the full Fabric jar.
    compileOnly("mezz.jei:jei-$mc-common-api:$jei")
    compileOnly("mezz.jei:jei-$mc-fabric-api:$jei")
    runtimeOnly("mezz.jei:jei-$mc-fabric:$jei")
}

// The default client and server run configurations are left as Loom generates them: this is a
// client mod, but a dev server run costs nothing and one less bit of build DSL can break.
