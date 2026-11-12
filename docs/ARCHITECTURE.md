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
- `EagleFineConfig`: versioned feature-toggle and zoom-preference storage using
  Eaglercraft's browser storage API.
- `EagleFineFeature`: metadata contract for a feature implementation.
- `EagleFineFeatureRegistry`: explicit feature registry.
- `EagleFineZoomFeature`: hold-to-zoom feature and registered Minecraft key.

## Startup integration

Minecraft initializes EagleFine immediately after constructing its normal
`Options` object. This ensures Eaglercraft's own settings have loaded before
EagleFine reads its independent configuration and registers its key mapping.

The configuration is stored under `eaglefine.config.v1` as gzip-compressed,
versioned JSON. It is separate from `options.txt` and the legacy Eaglercraft
`g` storage key.

## Zoom

Zoom is applied to the world-camera FOV after normal FOV effects have been
calculated. The HUD FOV is unchanged. The default key is C and the mapping is
registered through Eaglercraft's key-mapping extension so saved and remapped
keys are retained.

The default zoom factor is 4x; configurable range is 2x through 16x. Smooth
zoom is enabled by default. These preferences are stored in EagleFine's own
browser configuration.

## Current limitations

EagleFine currently:

- initializes during client startup;
- loads and saves a separate configuration;
- registers a hold-to-zoom key;
- smoothly changes the world-camera FOV while the key is held;
- does not yet add an EagleFine settings screen;
- does not yet implement dynamic lighting, connected textures, or shader packs.

## Integration rule

Prefer EagleFine-owned classes and narrow, documented Minecraft integration
points. Any renderer modification must preserve the original path when the
feature is inactive and include fallbacks for unsupported browser/GPU features.
