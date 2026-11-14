plugins {
	id("java")
}

dependencies {
	// order matters: platform-lwjgl's Platform* bodies shadow :platform's stubs
	implementation(project(":platform-lwjgl"))
	implementation(project(":platform"))
	implementation(project(":game"))
}

tasks.register<JavaExec>("eaglercraftDebugRuntime") {
	group = "eaglercraft"
	description = "Run the 26.2 desktop client (debug)"
	// platform-lwjgl first: its Platform* bodies must shadow the :platform stubs
	classpath = project(":platform-lwjgl").extensions.getByType<SourceSetContainer>()["main"].output +
		sourceSets["main"].runtimeClasspath
	mainClass = "net.lax1dude.eaglercraft.v1_8.internal.lwjgl.MainClass"
	workingDir = rootProject.layout.projectDirectory.dir("run").asFile
	javaLauncher = javaToolchains.launcherFor {
		languageVersion = JavaLanguageVersion.of(25)
	}
	jvmArgs(
		"-Xmx4G", "-Xms1G", "-Deagler.hosted=true",
		"-Djava.library.path=" + layout.projectDirectory.dir("natives").asFile.absolutePath,
		"--enable-native-access=ALL-UNNAMED"
	)
	args(
		"hide-renderdoc",
		"--version", "26.2-eagler",
		"--gameDir", ".",
		"--assetsDir", rootProject.layout.projectDirectory.dir("assets").asFile.absolutePath,
		"--assetIndex", "32",
		"--username", "Matej",
		"--accessToken", "0",
		"--userType", "legacy"
	)
	doFirst {
		workingDir.mkdirs()
	}
}

tasks.register<JavaExec>("buildAssetsEPK") {
	group = "eaglercraft"
	description = "Pack the 26.2 asset tree into assets.epk (the eaglercraft packager)"
	dependsOn(":game:processResources")
	// JavaExec already tracks its classpath and arguments. Declare the actual
	// resource tree and archive so unchanged packaging keeps the verified bytes.
	inputs.dir(project(":game").layout.projectDirectory.dir("src/main/resources"))
		.withPathSensitivity(PathSensitivity.RELATIVE)
	outputs.file(layout.buildDirectory.file("epk/assets.epk"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"
	args(
		project(":game").layout.projectDirectory.dir("src/main/resources").asFile.absolutePath,
		layout.buildDirectory.file("epk/assets.epk").get().asFile.absolutePath,
		"assets.epk"
	)
}

// Standalone EagleFine assets intentionally leave the audio manifest and audio files
// to sounds.epk/music.epk, where the generated original replacement pack lives.
tasks.register<JavaExec>("buildEagleFineAssetsEPK") {
	group = "eaglercraft"
	description = "Pack non-audio resources for the EagleFine standalone client"
	dependsOn(":game:processResources")

	val resourceRoot = project(":game").layout.projectDirectory.dir("src/main/resources")
	val stagedRoot = layout.buildDirectory.dir("eaglefine-assets-input")
	val archive = layout.buildDirectory.file("epk/eaglefine-assets.epk")

	inputs.dir(resourceRoot).withPathSensitivity(PathSensitivity.RELATIVE)
	outputs.file(archive)
	classpath = sourceSets["main"].runtimeClasspath
	mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"

	doFirst {
		val staged = stagedRoot.get().asFile
		staged.deleteRecursively()
		staged.mkdirs()
		project.copy {
			from(resourceRoot)
			into(staged)
			exclude("assets/minecraft/sounds.json")
			exclude("assets/minecraft/sounds/**")
		}
		archive.get().asFile.parentFile.mkdirs()
	}

	args(stagedRoot.get().asFile.absolutePath, archive.get().asFile.absolutePath, "assets.epk")
}

// Audit-only baseline for measuring the exact archive cost of entries excluded
// by buildAssetsEPK. It is deliberately not a dependency of any regular build.
tasks.register<JavaExec>("buildAssetsEPKWithWebUnreachable") {
	group = "verification"
	description = "Pack an audit baseline including web-unreachable assets"
	dependsOn(":game:processResources")
	inputs.dir(project(":game").layout.projectDirectory.dir("src/main/resources"))
		.withPathSensitivity(PathSensitivity.RELATIVE)
	outputs.file(layout.buildDirectory.file("epk/assets-with-web-unreachable.epk"))
	classpath = sourceSets["main"].runtimeClasspath
	mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"
	args(
		project(":game").layout.projectDirectory.dir("src/main/resources").asFile.absolutePath,
		layout.buildDirectory.file("epk/assets-with-web-unreachable.epk").get().asFile.absolutePath,
		"assets.epk",
		"--include-web-unreachable"
	)
}


// Generated EagleFine audio inputs stay under the ignored build-work directory.
fun registerEagleFineAudioArchive(taskName: String, archiveName: String) {
	val audioInput = rootProject.layout.projectDirectory.dir(".eaglefine-audio/epk-input/" + archiveName)
	val audioOutput = rootProject.layout.projectDirectory.file("target_teavm/build/web/" + archiveName + ".epk")
	tasks.register<JavaExec>(taskName) {
		group = "eaglercraft"
		description = "Package generated EagleFine " + archiveName + " OGG resources into an EPK"
		dependsOn("classes")
		inputs.dir(audioInput).withPathSensitivity(PathSensitivity.RELATIVE)
		outputs.file(audioOutput)
		classpath = sourceSets["main"].runtimeClasspath
		mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"
		doFirst {
			val inputDir = audioInput.asFile
			if (!inputDir.isDirectory || inputDir.walkTopDown().none { it.isFile }) {
				throw GradleException("Missing local audio input: " + inputDir.absolutePath +
					". Run node wasm-toolchain/generate-eaglefine-audio.js first.")
			}
			audioOutput.asFile.parentFile.mkdirs()
		}
		// Audio archives intentionally contain only sound resources, not the six
		// unrelated GUI files required by the full assets.epk audit.
		args(audioInput.asFile.absolutePath, audioOutput.asFile.absolutePath, archiveName, "--allow-empty")
	}
}

registerEagleFineAudioArchive("buildSoundsEPK", "sounds")
registerEagleFineAudioArchive("buildMusicEPK", "music")

tasks.register("buildAudioEPKs") {
	group = "eaglercraft"
	description = "Build sounds.epk and music.epk from generated EagleFine audio inputs"
	dependsOn("buildSoundsEPK", "buildMusicEPK")
}

// Audio-disabled developer builds still need a valid empty archive to boot.
// The normal standalone workflow generates or restores a full audio pack instead.
tasks.register<JavaExec>("buildSilentSoundsEPK") {
	group = "eaglercraft"
	description = "Create a valid empty sounds.epk for a silent browser test build"
	dependsOn("classes")

	val emptySoundsInput = layout.buildDirectory.dir("empty-sounds-input")
	val soundsOutput = rootProject.layout.projectDirectory.file("target_teavm/build/web/sounds.epk")
	outputs.file(soundsOutput)

	classpath = sourceSets["main"].runtimeClasspath
	mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"

	doFirst {
		val inputDir = emptySoundsInput.get().asFile
		inputDir.deleteRecursively()
		inputDir.mkdirs()
		soundsOutput.asFile.parentFile.mkdirs()
	}

	args(
		emptySoundsInput.get().asFile.absolutePath,
		soundsOutput.asFile.absolutePath,
		"sounds",
		"--allow-empty"
	)
}
