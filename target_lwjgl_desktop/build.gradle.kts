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


// Local audio restoration tasks. OGG files stay under the ignored local directory.
fun registerEagleFineAudioArchive(taskName: String, archiveName: String) {
	val audioInput = rootProject.layout.projectDirectory.dir(".eaglefine-audio/epk-input/" + archiveName)
	val audioOutput = rootProject.layout.projectDirectory.file("target_teavm/build/web/" + archiveName + ".epk")
	tasks.register<JavaExec>(taskName) {
		group = "eaglercraft"
		description = "Package local " + archiveName + " OGG resources into an EPK"
		dependsOn("classes")
		inputs.dir(audioInput).withPathSensitivity(PathSensitivity.RELATIVE)
		outputs.file(audioOutput)
		classpath = sourceSets["main"].runtimeClasspath
		mainClass = "net.lax1dude.eaglercraft.v1_8.sp.server.export.EPKPackagerMain"
		doFirst {
			val inputDir = audioInput.asFile
			if (!inputDir.isDirectory || inputDir.walkTopDown().none { it.isFile }) {
				throw GradleException("Missing local audio input: " + inputDir.absolutePath +
					". Run node wasm-toolchain/restore-game-audio.js first.")
			}
			audioOutput.asFile.parentFile.mkdirs()
		}
		args(audioInput.asFile.absolutePath, audioOutput.asFile.absolutePath, archiveName)
	}
}

registerEagleFineAudioArchive("buildSoundsEPK", "sounds")
registerEagleFineAudioArchive("buildMusicEPK", "music")

tasks.register("buildAudioEPKs") {
	group = "eaglercraft"
	description = "Build local sounds.epk and music.epk from restored audio inputs"
	dependsOn("buildSoundsEPK", "buildMusicEPK")
}

// Development-only sounds archive for the first EagleFine browser test build.
// It makes the client boot without audio when the original EPK audio inputs are
// not available in this source workspace. Replace this with the real sounds EPK
// before preparing a sound-enabled client.
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
