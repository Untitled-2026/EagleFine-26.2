# EagleFine 26.2

EagleFine is an independent graphics and performance project built on the Eaglercraft 26.2 workspace. Its goal is to recreate selected OptiFine-style features using code that fits Eaglercraft's browser runtime and rendering architecture.

This is an early-stage project. Features listed as planned are not necessarily implemented, and browser testing is required before considering a feature complete.

## Project status

The active development branch is `eaglefine/bootstrap`. It currently contains the EagleFine core/configuration work, the Zoom feature, and an in-game EagleFine settings page. The Java source compile check has passed. A successful Java compile does not replace a full browser build or in-game testing.

Current test-build limitations:
- The standalone HTML workflow creates a silent test client because the workspace does not currently include the required game audio/music archives.
- Zoom and the settings page still need to be tested in the generated browser client.
- Browser/WebGL capabilities may prevent exact parity with every desktop OptiFine feature.

## Download a standalone HTML test build

The repository includes a GitHub Actions workflow named **EagleFine standalone HTML test build**. It builds the source on a GitHub-hosted Ubuntu runner. Uploading the plain HTML file is a separate, opt-in step.

1. Open the repository on GitHub: [Untitled-2026/EagleFine-26.2](https://github.com/Untitled-2026/EagleFine-26.2).
2. Select the **Actions** tab.
3. In the left sidebar, select **EagleFine standalone HTML test build**.
4. Click **Run workflow** above the workflow run list.
5. In the **Branch** dropdown, select `eaglefine/bootstrap`. Do not select `main` for the EagleFine test build; `main` is kept as the upstream baseline.
6. Enable the checkbox named **Upload unencrypted HTML artifact**.
7. Click **Run workflow** and wait for the run to finish successfully.
8. Open that completed run and scroll to the **Artifacts** section.
9. Download `EagleFine-26.2-silent-test-plain`. GitHub downloads it as a ZIP archive.
10. Extract the ZIP. The extracted folder contains `EagleFine-26.2.html`.

The artifact is retained for **7 days**. Run the workflow again to produce a fresh build after that period.

### Important artifact note

The repository is public. An unencrypted HTML artifact should therefore be treated as publicly accessible to people who can access the repository. Only enable the upload option when you are comfortable publishing that build.

If the **Run workflow** button is missing, make sure you are viewing the correct workflow in the Actions tab. GitHub requires a manually triggered workflow file to exist on the repository's default branch. A copy of the standalone-build workflow is kept on `main` for this reason; choose `eaglefine/bootstrap` in the Branch dropdown to build the actual EagleFine development code.

## Run the Java compile check locally

The Java source compile uses Java 25:

```bash
./gradlew :game:compileJava --console=plain --no-daemon --max-workers=2 -Dorg.gradle.parallel=false
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The complete standalone HTML build is more memory-intensive than the Java compile check. The GitHub Actions workflow is provided for that build.

## Planned components

- **Core:** configuration model, feature registration, and shared utilities.
- **Settings:** graphics options, key bindings, and saved preferences.
- **Graphics:** visual options and rendering integrations.
- **Performance:** measured optimizations and resource controls.
- **Shaders:** browser-compatible post-processing experiments.

Not every desktop OptiFine feature will necessarily be possible in a browser client. Implementation should be guided by the actual Eaglercraft renderer and browser platform constraints.

## Project principles

1. Preserve normal Eaglercraft gameplay and default rendering.
2. Keep EagleFine code clearly separated from upstream code where practical.
3. Implement features independently; do not copy OptiFine's proprietary implementation.
4. Test changes against the unmodified client baseline.
5. Preserve upstream attribution, license notices, and applicable terms.
6. Do not commit official Minecraft JAR files, credentials, or private build inputs.

EagleFine is an unofficial community project and is not affiliated with Mojang Studios, Microsoft, or the OptiFine project.
