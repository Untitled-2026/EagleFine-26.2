# EagleFine development roadmap

## Milestone 0 — establish the baseline

- [ ] Run the existing `:game:compileJava` task on Java 25 in GitHub Actions.
- [ ] Fix only build-environment issues in this milestone.
- [ ] Record the successful upstream baseline before modifying game code.
- [ ] Keep the full HTML build separate until its memory and artifact requirements are validated.

## Milestone 1 — EagleFine Core

- [ ] Add a small feature/configuration foundation under `dev.eaglefine`.
- [ ] Define how options are loaded, saved, and reset.
- [ ] Keep disabled-by-default features from changing vanilla behaviour.
- [ ] Add tests or compile checks for each component where practical.

## Milestone 2 — settings and simple client features

- [ ] Add an EagleFine settings entry point.
- [ ] Implement a configurable zoom feature.
- [ ] Add safe visual options such as fog and animation controls where supported.
- [ ] Verify persistence and keyboard/controller compatibility.

## Milestone 3 — graphics features

- [ ] Investigate the existing texture-atlas and block-model pipeline.
- [ ] Prototype connected-texture rules.
- [ ] Investigate dynamic lighting within the browser rendering budget.
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

Only Milestone 0 is active. No OptiFine-style feature should be described as implemented until it exists in the code and passes a test.
