# EagleFine

EagleFine is an independent graphics and performance project for the Eaglercraft 26.2 workspace.

The long-term goal is to recreate selected OptiFine-style capabilities in a way that fits Eaglercraft's browser runtime and rendering architecture. This is an early-stage project: planned features are not implemented merely because they appear in the roadmap.

## Project status

**Current milestone:** establish a reproducible baseline Java compile before changing rendering or gameplay code.

## Development

The project uses the upstream Eaglercraft 26.2 Gradle workspace. The documented Java source check uses Java 25:

```bash
./gradlew :game:compileJava --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false
```

On Windows, run the same arguments with `gradlew.bat`.

The full standalone HTML build is a later milestone. It has a substantially higher memory requirement than the Java compile check.

## Planned components

- **Core:** configuration model, feature registration, and shared utilities.
- **Settings:** graphics options, key bindings, and saved preferences.
- **Graphics:** visual options and rendering integrations.
- **Performance:** measured optimizations and resource controls.
- **Shaders:** browser-compatible post-processing experiments.

Implementation will be guided by the actual Eaglercraft renderer and browser platform constraints. Not every desktop OptiFine feature is necessarily possible in a browser client.

## Project principles

1. Preserve normal Eaglercraft gameplay and default rendering.
2. Keep EagleFine code clearly separated from upstream code where practical.
3. Implement features independently; do not copy OptiFine's proprietary implementation.
4. Test each change against the unmodified client baseline.
5. Preserve upstream attribution, license notices, and applicable terms.
6. Do not commit official Minecraft JAR files, credentials, or private build inputs.

EagleFine is an unofficial community project and is not affiliated with Mojang Studios, Microsoft, or the OptiFine project.
