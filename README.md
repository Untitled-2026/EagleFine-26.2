# EagleFine 26.2

EagleFine is an independent graphics and performance project built on the Eaglercraft 26.2 workspace. Its goal is to recreate selected OptiFine-style features using code that fits Eaglercraft's browser runtime and rendering architecture.

This is an early-stage project. Feature parity with desktop OptiFine is not guaranteed; each feature must be implemented and tested against the browser runtime.

## Current milestone: First playable client and core features

The first standalone EagleFine HTML test client has been built and opened in a browser. Initial testing looks good. Development continues on `eaglefine/bootstrap`; `main` is kept as the upstream baseline with the project README and manually triggered build workflow available there.

### Implemented

- **EagleFine Core:** client startup initialization, feature registration foundation, and separate persisted configuration.
- **Zoom:** hold-to-zoom camera FOV feature, with smooth zoom and adjustable strength from 2x to 16x (default 4x).
- **EagleFine Settings:** a dedicated page accessible from Minecraft's Options screen, with Zoom Strength, Smooth Zoom, Dynamic Lights, and Reset to Defaults.
- **Key binding access:** the EagleFine settings shortcut opens the Key Binds screen directly, where the Zoom key can be changed.
- **Main-menu branding:** the lower-left credit reads "Made by Untitled".
- **Credits panel branding:** the Eaglercraft credits panel also reads "Made by Untitled".
- **Dynamic Lights:** light-emitting items in either hand of nearby players and mobs, plus dropped light-emitting items, cast dynamic block light. Light propagates through blocks according to their light dampening, so opaque blocks block the light. The system updates when light sources move, appear, disappear, or when nearby block opacity changes. Inline and mesh-worker chunk compilation use the same light field.

### Tested

- Java game-source compilation has passed in GitHub Actions for earlier milestones.
- A standalone HTML build completed successfully, and the generated client was opened in a browser.
- The credits and Dynamic Lights changes need a fresh build and in-client testing.

### Known limitations

- **No sound or music yet.** The current source workspace does not contain the original Minecraft sound files or audio archives, and the GitHub Actions HTML workflow does not add them. The upstream build guide documents a separate official "Get game audio" process that rebuilds the audio archives and requires FFmpeg 6.1.1. That process has not yet been integrated into EagleFine's automated build.
- Dynamic Lights is item-based: arbitrary glowing entities, projectiles, and other non-item light sources are not included.
- The light-field build is bounded to 64 nearby sources and 131,072 affected block cells to protect browser performance. Crowded scenes may therefore omit the farthest sources or the tail of the field.
- Browser/WebGL capabilities may limit some advanced OptiFine-style features.

## Next milestone: Validate Dynamic Lights and restore audio

1. Test held torches, lanterns, glow berries and lava buckets in dark areas.
2. Test dropped light items and light-emitting items held by nearby players or mobs.
3. Check that solid walls block dynamic light and that placing or removing blocks updates the lighting.
4. Measure chunk rebuild cost and frame-time impact while moving with a light source.
5. Integrate the documented audio-resource build step without committing restricted assets, then continue with Connected Textures, Custom Sky, fog controls and shader experiments.

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
