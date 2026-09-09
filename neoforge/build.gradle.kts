plugins {
    id("net.neoforged.moddev") version "2.0.146"
}

val mc = property("minecraftVersion") as String
val jei = property("jeiVersion") as String

base { archivesName.set("craftbridge-client-$mc-neoforge") }

sourceSets {
    main {
        // The same shared sources as the Fabric module, compiled against NeoForge's Minecraft.
        java.srcDir("../common/src/main/java")
        resources.srcDir("../common/src/main/resources")
    }
}

neoForge {
    version = property("neoforgeVersion") as String
    runs {
        create("client") {
            client()
        }
    }
    mods {
        create(property("modId") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    compileOnly("mezz.jei:jei-$mc-common-api:$jei")
    compileOnly("mezz.jei:jei-$mc-neoforge-api:$jei")
    runtimeOnly("mezz.jei:jei-$mc-neoforge:$jei")
}
