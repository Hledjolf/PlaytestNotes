// Playtest Notes: a game-agnostic core plus one adapter per game.
// core/ is plain Java; adapters/hytale/ is the Hytale plugin, which merges core into its single jar.
plugins { base }

version = "1.0.0"
val modVersion = version.toString()

subprojects {
    version = rootProject.version
    apply(plugin = "java")
    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.11.4")
    }

    tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
    tasks.withType<Test> {
        useJUnitPlatform()
        systemProperty("playtestnotes.repo", rootProject.projectDir.absolutePath)
    }
}

val hytaleJar = project(":adapters:hytale").tasks.named<Jar>("jar")

// ---------------------------------------------------------------------------------------------------------
// checkAdapterBoundary: core/ is game-agnostic. No file under core/ may name a game API. Adapters may.
// ---------------------------------------------------------------------------------------------------------
val checkAdapterBoundary = tasks.register("checkAdapterBoundary") {
    group = "verification"
    description = "Fails if any file under core/ uses the Hytale API."
    val sources = fileTree("core") { include("**/*.java"); exclude("build/**") }
    inputs.files(sources)
    doLast {
        val offenders = sources.files.filter { f -> f.readLines().any { it.contains("com.hypixel") } }
        if (offenders.isNotEmpty()) {
            throw GradleException("Hytale API used in core:\n" + offenders.joinToString("\n") { " - " + it.relativeTo(projectDir) })
        }
    }
}

// The jar must never carry Hytale classes, the server jar, assets copied from Assets.zip or decompiled code.
val checkJarContents = tasks.register("checkJarContents") {
    group = "verification"
    description = "Fails if the plugin jar contains Hytale classes, source files or anything outside the allow list."
    dependsOn(hytaleJar)
    val jarFile = hytaleJar.flatMap { it.archiveFile }
    doLast {
        val allowed = Regex("""META-INF/.*|manifest\.json|LICENSE|playtestnotes/.*\.class|Settings/Hledjolf_PlaytestNotes\.settings\.json""")
        val names = java.util.zip.ZipFile(jarFile.get().asFile).use { zf ->
            zf.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
        }
        val bad = names.filter { !allowed.matches(it) }
        if (bad.isNotEmpty()) throw GradleException("Unexpected files in the jar:\n" + bad.joinToString("\n") { " - $it" })
        if ("Settings/Hledjolf_PlaytestNotes.settings.json" !in names) throw GradleException("The jar has no sidecar")
        if ("playtestnotes/core/NoteService.class" !in names) throw GradleException("The jar has no core classes")
    }
}

// checkNoProjectMarks: no public file (tracked, shipped in the jar or zip, or on the mod pages) may name the
// author's other, unreleased project. The words are split here so this file doesn't match itself.
val checkNoProjectMarks = tasks.register("checkNoProjectMarks") {
    group = "verification"
    description = "Fails if a tracked, shipped or page file mentions the author's unreleased project."
    dependsOn(hytaleJar)
    val marks = listOf("arch" + "mage", "spell" + "bound", "master" + " of " + "magic")
    val files = fileTree(projectDir) {
        include("core/src/main/**", "adapters/*/src/main/**", "README.md", "CHANGELOG.md", "LICENSE", "*.gradle.kts",
            "core/*.gradle.kts", "adapters/*/*.gradle.kts", "gradle.properties", ".gitignore", "docs/page/**")
        exclude("docs/page/images/**", "docs/page/screenshots/**")
    }
    val jarFile = hytaleJar.flatMap { it.archiveFile }
    inputs.files(files)
    doLast {
        fun hits(text: String) = marks.filter { text.contains(it, ignoreCase = true) }
        val bad = mutableListOf<String>()
        files.files.forEach { f -> hits(f.readText()).forEach { bad += "${f.relativeTo(projectDir)}: $it" } }
        java.util.zip.ZipFile(jarFile.get().asFile).use { zf ->
            zf.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                val text = String(zf.getInputStream(e).readAllBytes(), Charsets.ISO_8859_1)
                hits(e.name + "\n" + text).forEach { bad += "jar!/${e.name}: $it" }
            }
        }
        if (bad.isNotEmpty()) throw GradleException("Project marks in public files:\n" + bad.joinToString("\n") { " - $it" })
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(checkAdapterBoundary)
    dependsOn(checkJarContents)
    dependsOn(checkNoProjectMarks)
}

// ---------------------------------------------------------------------------------------------------------
// release: build/release/PlaytestNotes-<version>.jar (CurseForge) and PlaytestNotes-<version>.zip (Nexus: the
// jar plus README.md, LICENSE and CHANGELOG.md). Runs check first and verifies the jar: no test
// classes, no Hytale classes, the manifest version matching the build.
// ---------------------------------------------------------------------------------------------------------
val releaseDir = layout.buildDirectory.dir("release")

val releaseJar = tasks.register<Copy>("releaseJar") {
    group = "distribution"
    description = "Copies the checked plugin jar to build/release."
    dependsOn(tasks.named("check"))
    from(hytaleJar.flatMap { it.archiveFile })
    into(releaseDir)
    doLast {
        val jar = releaseDir.get().file("PlaytestNotes-$modVersion.jar").asFile
        java.util.zip.ZipFile(jar).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.toList()
            val bad = names.filter { it.startsWith("com/hypixel/") || it.contains("Test") && it.endsWith(".class") || it.endsWith(".java") }
            if (bad.isNotEmpty()) throw GradleException("Release jar contains test or Hytale files: $bad")
            val manifest = String(zf.getInputStream(zf.getEntry("manifest.json")).readAllBytes(), Charsets.UTF_8)
            if (!manifest.contains("\"Version\": \"$modVersion\"")) throw GradleException("manifest.json version does not match $modVersion")
        }
    }
}

val release = tasks.register<Zip>("release") {
    group = "distribution"
    description = "Builds build/release/PlaytestNotes-<version>.jar and .zip."
    dependsOn(releaseJar)
    archiveFileName.set("PlaytestNotes-${project.version}.zip")
    destinationDirectory.set(releaseDir)
    from(hytaleJar.flatMap { it.archiveFile })
    from(".") { include("README.md", "LICENSE", "CHANGELOG.md") }
    doLast { logger.lifecycle("Release: " + releaseDir.get().asFile) }
}
