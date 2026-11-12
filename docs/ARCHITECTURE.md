# EagleFine architecture

## Initial Java package

EagleFine-owned Java code lives under:

```text
game/src/main/java/dev/eaglefine/
```

The package deliberately avoids putting EagleFine classes in Minecraft's
`net.minecraft` namespace.

## Core classes

- `EagleFine`: process-wide entry point and startup initialization.
- `EagleFineConfig`: versioned feature-toggle and graphics-preference storage
  using Eaglercraft's browser storage API.
- `EagleFineFeature`: metadata contract for feature implementations.
- `EagleFineFeatureRegistry`: explicit feature registry.
- `EagleFineZoomFeature`: hold-to-zoom feature and registered Minecraft key.
- `EagleFineDynamicLightsFeature`: metadata for the Dynamic Lights setting.
- `EagleFineDynamicLights`: held-item light-source tracking, section invalidation,
  and distance-based light sampling.

## Startup integration

Minecraft initializes EagleFine immediately after constructing its normal
`Options` object. This ensures Eaglercraft's own settings have loaded before
EagleFine reads its independent configuration and registers its key mapping.

The configuration is stored under `eaglefine.config.v1` as gzip-compressed,
versioned JSON. Version 2 adds the Dynamic Lights toggle and migrates existing
version-1 configurations without discarding Zoom preferences. The data remains
separate from `options.txt` and the legacy Eaglercraft `g` storage key.

## Zoom

Zoom is applied to the world-camera FOV after normal FOV effects have been
calculated. The HUD FOV is unchanged. The default key is C and the mapping is
registered through Eaglercraft's key-mapping extension so saved and remapped
keys are retained.

The default zoom factor is 4x; configurable range is 2x through 16x. Smooth
zoom is enabled by default. These preferences are stored in EagleFine's own
browser configuration.

## Dynamic Lights — first implementation

The first Dynamic Lights pass supports emissive block items held in either hand
by the local player, plus glow berries and a lava bucket. It calculates
distance-attenuated block light with a maximum 15-level source.

When a held light source changes block position or intensity, the surrounding
chunk sections are marked dirty. Inline section compilation reads dynamic light
through `RenderSectionRegion.getBrightness`. The mesh-worker snapshot builder
writes the same values into block-light nibbles before dispatch, so both
rendering paths use the same source data.

This is an initial implementation, not full OptiFine parity. It does not yet
include dropped items, other players or mobs as light sources, or occlusion by
intervening solid blocks. The result requires visual and performance testing in
the browser client.

## Settings screen

The EagleFine entry in Minecraft's Options screen opens a dedicated settings page.
It exposes the Zoom Strength slider, Smooth Zoom toggle, Dynamic Lights toggle,
a direct shortcut to the Key Binds screen for changing the Zoom key, and a
reset-to-defaults action.

## Current limitations

EagleFine currently:

- initializes during client startup;
- loads and saves a separate configuration;
- registers a hold-to-zoom key;
- smoothly changes the world-camera FOV while the key is held;
- provides a first-pass local-player held-item Dynamic Lights implementation;
- does not yet implement connected textures or shader packs;
- still needs sound restoration and further browser/GPU compatibility testing.

## Integration rule

Prefer EagleFine-owned classes and narrow, documented Minecraft integration
points. Renderer modifications must preserve the original path when a feature
is inactive and include fallbacks for unsupported browser/GPU features.

## First standalone test build

The checked-in workspace does not include the original OGG sound files or
optional music EPK. The current browser test build therefore generates a valid
empty `sounds.epk`, skips the optional music pack, and runs silently. It is
intended to validate startup, settings, Zoom and graphics experiments; it is
not a sound-enabled release build.
