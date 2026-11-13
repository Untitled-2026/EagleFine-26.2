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
- `EagleFineDynamicLights`: item, display, glowing/burning-entity, TNT and lightning source tracking, opacity-aware propagation, section invalidation, and build statistics.

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

## Dynamic Lights

Dynamic Lights tracks block-emitting items held by nearby living entities,
dropped in the world, stored in item frames, or shown by item displays. It also
tracks emissive block displays and falling blocks, glowing and burning entities
(including charged Blazes), primed TNT, lightning, and glow item frames.

The manager builds a block-light field on the client thread and publishes an
immutable snapshot for rendering. Light propagates through neighboring blocks
using each block state's light dampening, so opaque blocks stop light while
less-opaque blocks reduce it. Both inline section compilation and mesh-worker
snapshot compilation read the same field.

There are no artificial source-count or visited-cell caps. Light naturally
terminates when its level attenuates to zero; with level-15 sources and at least
one attenuation per step, the maximum natural propagation reach is 14 blocks.
The manager exposes the most recent source count, lit-cell count, and rebuild
time so a browser session can be profiled.

Changed source positions are compared using packed block-position lookups and
their invalidation bounds are coalesced into one section-range update. Changes
to block light dampening near an active source mark the field dirty and
invalidate affected sections, so building or removing a wall updates lighting
on the next client tick.

This completes the core item/entity Dynamic Lights path. It still needs visual,
multiplayer, and performance testing in the browser client. Not every visual
glow shader or entity effect is treated as a block-light source.

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
- provides uncapped item/display/entity Dynamic Lights with block-opacity-aware propagation and build counters;
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
