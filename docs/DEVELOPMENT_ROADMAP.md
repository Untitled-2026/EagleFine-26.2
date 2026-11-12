# EagleFine development roadmap

## Milestone 0 — establish the baseline

- [x] Run the existing `:game:compileJava` task on Java 25 in GitHub Actions.
- [ ] Fix only build-environment issues in this milestone.
- [x] Record the successful upstream baseline before modifying game code.
- [ ] Keep the full HTML build separate until its memory and artifact requirements are validated.

## Milestone 1 — EagleFine Core

- [x] Add a small feature/configuration foundation under `dev.eaglefine`.
- [x] Define how options are loaded, saved, and reset.
- [x] Keep disabled-by-default features from changing vanilla behaviour.
- [x] Add tests or compile checks for each component where practical.

## Milestone 2 — settings and simple client features

- [x] Add an EagleFine settings entry point.
- [x] Implement a configurable hold-to-zoom feature (default C, 4x, smooth transition).
- [ ] Add safe visual options such as fog and animation controls where supported.
- [x] Add a persisted Dynamic Lights toggle.
- [ ] Verify persistence and keyboard/controller compatibility.

## Milestone 3 — graphics features

- [ ] Investigate the existing texture-atlas and block-model pipeline.
- [ ] Prototype connected-texture rules.
- [x] Implement a first-pass held-item Dynamic Lights path for inline and mesh-worker chunk compilation.
- [ ] Add visual comparisons and regression checks.

## Milestone 4 — performance

- [ ] Profile chunk building, mesh upload, texture upload, and frame time first.
- [ ] Identify measurable bottlenecks before changing algorithms.
- [ ] Add optimizations one at a time and compare results on supported browsers.
- [ ] Avoid trading stability or memory safety for headline FPS gains.

## Milestone 5 — shader experiments

- [ ] Map the actual WebGL/WebGPU capabilities exposed by the Eaglercraft build.
- [ ] Start with a small optional post-processing effect.
- [ ] Add graceful fallback when a browser or GPU lacks a required feature.
- [ ] Evaluate shader-pack compatibility separately; desktop OptiFine shader packs should not be assumed to work unchanged.

## Milestone 6 — release validation

- [ ] Build the standalone HTML client in a suitable CI runner.
- [ ] Test startup, world creation, rendering, settings persistence, and saving.
- [ ] Test on the Windows 11 and HP Pavilion g6 devices where practical.
- [ ] Publish only files that are permitted to be redistributed.

## Current scope

Milestones 0 and 1 are complete. Zoom, the settings page, credits branding, and a first-pass held-item Dynamic Lights implementation are in source. Dynamic Lights still requires browser visual/performance testing; the current test client remains silent until the audio-resource step is integrated.
