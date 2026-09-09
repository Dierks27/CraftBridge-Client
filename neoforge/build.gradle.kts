plugins {
    id("net.neoforged.moddev") version "2.0.146"
}

val mc = providers.gradleProperty("minecraftVersion").get()
val jei = providers.gradleProperty("jeiVersion").get()

base { archivesName.set("craftbridge-client-$mc-neoforge") }

sourceSets {
    main {
        // The same shared sources as the Fabric module, compiled against NeoForge's Minecraft.
        java.srcDir("../common/src/main/java")
        resources.srcDir("../common/src/main/resources")
    }
}

neoForge {
    version = providers.gradleProperty("neoforgeVersion").get()
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
