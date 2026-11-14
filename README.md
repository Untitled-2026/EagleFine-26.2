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
- **Dynamic Lights:** light-emitting items in either hand of nearby living entities, dropped items, item frames and item/block displays cast light. Glowing or burning entities, primed TNT and lightning also emit light. Light propagates through blocks according to their light dampening, so opaque blocks block it. Inline and mesh-worker chunk compilation use the same light field. Source-count and visited-cell caps have been removed; normal 15-level light falloff determines natural reach.

### Tested

- Java game-source compilation has passed in GitHub Actions for earlier milestones.
- A standalone HTML build completed successfully, and the generated client was opened in a browser.
- The credits and Dynamic Lights changes need a fresh build and in-client testing.

### Audio

- **Runtime audio selection:** when a player opens an audio-enabled standalone HTML, EagleFine shows an audio setup screen. The player can select their Minecraft Java `.minecraft` folder or its `assets` folder. EagleFine reads the matching asset index and referenced audio files locally, then creates temporary sound archives in the browser. It does not modify the installation or upload the selected audio.
- The HTML always includes original EagleFine fallback audio: 82 sound-effect variations across 41 families and 12 original music tracks. Skipping or cancelling folder selection, denying access, using an unsupported browser, or selecting an incomplete cache keeps the embedded audio active. Players do not need a separate resource pack or audio files.
- The build uses only the original generated audio, so no Minecraft installation is needed to build or share the HTML. FFmpeg with both `libvorbis` and `libopus` encoders is required for the fallback audio generation.

### Known limitations

- The generated sounds are a first-pass audio set. They cover the existing sound-event IDs with material- and event-aware sound families, but are not a one-to-one recreation of every original game recording.
- Dynamic Lights supports item/display sources, glowing and burning entities, primed TNT and lightning; not every visual particle or shader effect is treated as block light.
- Dynamic Lights has no artificial source-count or visited-cell cap. Large numbers of nearby sources can increase frame time and memory use, so performance testing is required.
- Browser/WebGL capabilities may limit some advanced OptiFine-style features.

## Next milestone: Performance and audio validation

1. Compare frame rate and memory with Dynamic Lights off and on in the same cave or crowded scene.
2. Test held, dropped and displayed light items, glowing/burning entities, primed TNT, lightning, and opaque-block occlusion.
3. Check the Dynamic Lights counters (`getLastFieldSourceCount`, `getLastFieldCellCount`, and `getLastFieldBuildNanos`) while moving through a light-heavy area.
4. Test the generated sound effects and soundtrack in the browser, checking block interactions, footsteps, creatures, ambience, music changes and music discs.
5. Improve the sound families and soundtrack after listening tests; continue with Connected Textures, Custom Sky, fog controls and shader experiments.

## Development

The project uses the upstream Eaglercraft 26.2 Gradle workspace. The documented Java source check uses Java 25:

```bash
./gradlew :game:compileJava --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The standalone HTML build is more memory-intensive than the Java compile check. GitHub Actions installs FFmpeg and builds a self-contained client using the original EagleFine fallback audio. A local build automatically uses a complete Minecraft Java 26.2 audio cache when one is available.

## Project principles

1. Preserve normal Eaglercraft gameplay and default rendering.
2. Keep EagleFine code clearly separated from upstream code where practical.
3. Implement features independently; do not copy OptiFine's proprietary implementation.
4. Test changes against the unmodified client baseline.
5. Preserve upstream attribution, license notices, and applicable terms.
6. Do not commit official Minecraft JAR files, credentials, or private build inputs.

EagleFine is an unofficial community project and is not affiliated with Mojang Studios, Microsoft, or the OptiFine project.
