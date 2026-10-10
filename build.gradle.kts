plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
}

val modVersion = project.property("mod_version") as String

repositories {
    maven("https://maven.createmod.net") {
        content { includeGroup("com.simibubi.create") }
    }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

// The full Create jar (with its jar-in-jar libraries), resolved from Create's maven.
val createJar: Configuration by configurations.creating {
    isTransitive = false
}

// Jade, for the dev runs that have Create (it's copied into their mods folders the same way).
val jadeJar: Configuration by configurations.creating {
    isTransitive = false
}

version = modVersion
group = project.property("mod_group_id") as String

base {
    archivesName = project.property("mod_id") as String
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

// Nothing in it: the classpath for the "clientBare" run.
val bareSourceSet: SourceSet = sourceSets.create("bare")

neoForge {
    version = project.property("neo_version") as String

    runs {
        create("client") {
            client()
        }
        // Same as "client", but without Create, to check the mod still works on its own.
        create("clientNoCreate") {
            client()
            gameDirectory = file("run-nocreate")
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        create("serverNoCreate") {
            server()
            programArgument("--nogui")
            gameDirectory = file("run-nocreate")
        }
        // A second game instance that joins the local "server" run, for testing as a player.
        create("clientJoin") {
            client()
            gameDirectory = file("run-client")
            programArguments.addAll("--quickPlayMultiplayer", "localhost:25565")
        }
        // A client without this mod joining the local "serverNoCreate" run: checks players don't need the mod.
        // Its own empty source set keeps the mod off the classpath, where NeoForge would find it anyway.
        create("clientBare") {
            client()
            gameDirectory = file("run-bare")
            sourceSet = bareSourceSet
            loadedMods = setOf()
            programArguments.addAll("--quickPlayMultiplayer", "localhost:25565")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    addModdingDependenciesTo(bareSourceSet)

    mods {
        create(project.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Create's classes reference its jar-in-jar libraries (ponder, flywheel, registrate), so they're
// extracted and put on the compile classpath too.
val extractCreateLibs by tasks.registering(Copy::class) {
    from({ zipTree(createJar.singleFile) }) {
        include("META-INF/jarjar/*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("createLibs"))
}

dependencies {
    createJar("com.simibubi.create:create-1.21.1:${project.property("create_version")}")
    compileOnly(files(createJar))
    compileOnly(fileTree(layout.buildDirectory.dir("createLibs")).builtBy(extractCreateLibs))

    compileOnly("maven.modrinth:jade:${project.property("jade_version")}")
    jadeJar("maven.modrinth:jade:${project.property("jade_version")}")
}

// Create bundles flywheel/ponder/registrate via jar-in-jar, so in dev it's loaded from run/mods
// like a normal installed mod instead of from the classpath. Jade comes along, so the NoCreate and Bare runs
// are just NeoForge (plus this mod, except for Bare).
// "client" and "server" share run/, "clientJoin" uses run-client/; the NoCreate and Bare runs go without it.
val copyCreateToRun by tasks.registering(Copy::class) {
    from(createJar) { rename { "create.jar" } }
    from(jadeJar) { rename { "jade.jar" } }
    into(layout.projectDirectory.dir("run/mods"))
    doFirst {
        // Jars an older version of this task copied in under their own names.
        delete(fileTree(layout.projectDirectory.dir("run/mods")) { include("create-1.21.1-*.jar") })
    }
}
val copyCreateToClientRun by tasks.registering(Copy::class) {
    from(createJar) { rename { "create.jar" } }
    from(jadeJar) { rename { "jade.jar" } }
    into(layout.projectDirectory.dir("run-client/mods"))
}
tasks.matching {
    it.name.startsWith("prepare") && it.name.endsWith("Run") && !it.name.contains("NoCreate") && !it.name.contains("Bare")
}.configureEach {
    dependsOn(copyCreateToRun, copyCreateToClientRun)
}

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val props = mapOf(
        "minecraft_version" to project.property("minecraft_version"),
        "neo_version" to project.property("neo_version"),
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to modVersion,
        "mod_authors" to project.property("mod_authors"),
    )
    inputs.properties(props)
    expand(props)
    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}
sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
