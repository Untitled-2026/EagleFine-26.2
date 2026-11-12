# EagleFine 26.2

EagleFine is an independent graphics and performance project built on the Eaglercraft 26.2 workspace. Its goal is to recreate selected OptiFine-style features using code that fits Eaglercraft's browser runtime and rendering architecture.

This is an early-stage project. Feature parity with desktop OptiFine is not guaranteed; each feature must be implemented and tested against the browser runtime.

## Current milestone: First playable client and Zoom

The first standalone EagleFine HTML test client has been built and opened in a browser. Initial testing looks good. Development continues on `eaglefine/bootstrap`; `main` remains the clean upstream baseline with only the shared project README and manually-triggered build workflow added.

### Implemented

- **EagleFine Core:** client startup initialization, feature registration foundation, and separate persisted configuration.
- **Zoom:** hold-to-zoom camera FOV feature, with smooth zoom and adjustable strength from 2x to 16x (default 4x).
- **EagleFine Settings:** a dedicated page accessible from Minecraft's Options screen, with Zoom Strength, Smooth Zoom, and Reset to Defaults.
- **Key binding access:** the EagleFine settings shortcut opens the Key Binds screen directly, where the Zoom key can be changed.
- **Main-menu branding:** the lower-left credit is changed to "Made by Untitled".

### Tested

- Java game-source compilation succeeds in GitHub Actions.
- A standalone HTML build completes successfully.
- The generated HTML client has been opened in a browser; initial visual testing looks good.

### Known limitations

- **No sound or music yet.** The current source workspace does not contain the Minecraft sound files or audio archives, and the GitHub Actions HTML workflow does not add them. The upstream build guide documents a separate "Get game audio" process for fetching official sound objects and rebuilding the sound archives; that process requires FFmpeg 6.1.1. We have not integrated that audio step into EagleFine's automated build yet.
- The latest menu-credit and Key Binds navigation changes need to be confirmed in a fresh HTML build.
- Browser/WebGL capabilities may limit some advanced OptiFine-style features.

## Next milestone: Polish and core graphics

1. Restore sound and music using the documented official audio-input process, then include the resulting archives in the automated build without committing restricted assets.
2. Re-test Zoom, Smooth Zoom, the key binding editor, and saved settings in the browser client.
3. Begin the next rendering feature, with Dynamic Lights as the next candidate.
4. Expand graphics settings as features become functional.

## Development

The project uses the upstream Eaglercraft 26.2 Gradle workspace. The documented Java source check uses Java 25:

```bash
./gradlew :game:compileJava --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The standalone HTML build is more memory-intensive than the Java compile check. GitHub Actions is configured to build it on a GitHub-hosted Ubuntu runner.

## Project principles

1. Preserve normal Eaglercraft gameplay and default rendering.
2. Keep EagleFine code clearly separated from upstream code where practical.
3. Implement features independently; do not copy OptiFine's proprietary implementation.
4. Test changes against the unmodified client baseline.
5. Preserve upstream attribution, license notices, and applicable terms.
6. Do not commit official Minecraft JAR files, credentials, or private build inputs.

EagleFine is an unofficial community project and is not affiliated with Mojang Studios, Microsoft, or the OptiFine project.
