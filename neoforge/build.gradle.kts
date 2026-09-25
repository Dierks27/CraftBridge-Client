plugins {
    id("net.neoforged.moddev") version "2.0.146"
}

// Per-target versions, set by the root build from versions/<mcTarget>.properties.
val mc = extra["minecraftVersion"] as String
val jei = extra["jeiVersion"] as String
val neoforgeBuild = extra["neoforgeVersion"] as String

base { archivesName.set("craftbridge-client-$mc-neoforge") }

sourceSets {
    main {
        // The same shared sources as the Fabric module, compiled against NeoForge's Minecraft.
        java.srcDir("../common/src/main/java")
        resources.srcDir("../common/src/main/resources")
    }
}

neoForge {
    version = neoforgeBuild
    runs {
        create("client") {
            client()
        }
    }
    mods {
        create(providers.gradleProperty("modId").get()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly("mezz.jei:jei-$mc-common-api:$jei")
    compileOnly("mezz.jei:jei-$mc-neoforge-api:$jei")
    runtimeOnly("mezz.jei:jei-$mc-neoforge:$jei")
}
