plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
}

val modVersion = project.property("mod_version") as String
val createJar = file(project.property("create_jar") as String)

version = modVersion
group = project.property("mod_group_id") as String

base {
    archivesName = project.property("mod_id") as String
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = project.property("neo_version") as String

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        create(project.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Create's classes reference its jar-in-jar libraries (ponder, flywheel, registrate), so they're
// extracted and put on the compile classpath too.
val extractCreateLibs by tasks.registering(Copy::class) {
    from(zipTree(createJar)) {
        include("META-INF/jarjar/*.jar")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("createLibs"))
}

dependencies {
    compileOnly(files(createJar))
    compileOnly(fileTree(layout.buildDirectory.dir("createLibs")).builtBy(extractCreateLibs))
}

// Create bundles flywheel/ponder/registrate via jar-in-jar, so in dev it's loaded from run/mods
// like a normal installed mod instead of from the classpath.
val copyCreateToRun by tasks.registering(Copy::class) {
    from(createJar)
    into(layout.projectDirectory.dir("run/mods"))
}
tasks.matching { it.name.startsWith("prepare") && it.name.endsWith("Run") }.configureEach {
    dependsOn(copyCreateToRun)
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
