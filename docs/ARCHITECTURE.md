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

## Settings screen

The EagleFine entry in Minecraft's Options screen opens a dedicated settings page.
It currently exposes the zoom-strength slider, Smooth Zoom toggle, a shortcut
into Controls for changing the Zoom key, and a reset-to-defaults action.

## Current limitations

EagleFine currently:

- initializes during client startup;
- loads and saves a separate configuration;
- registers a hold-to-zoom key;
- smoothly changes the world-camera FOV while the key is held;
- does not yet implement dynamic lighting, connected textures, or shader packs;
- still needs a standalone HTML build and in-client validation.

## Integration rule

Prefer EagleFine-owned classes and narrow, documented Minecraft integration
points. Any renderer modification must preserve the original path when the
feature is inactive and include fallbacks for unsupported browser/GPU features.


## First standalone test build

The checked-in workspace does not include the original OGG sound files or
optional music EPK. The first browser test build therefore generates a valid
empty `sounds.epk`, skips the optional music pack, and runs silently. It is
intended to validate startup, the EagleFine Options entry, and Zoom behavior;
it is not a sound-enabled release build.

Because this fork is public, the standalone HTML is encrypted before being
uploaded as a short-lived GitHub Actions artifact. Only the encrypted payload
is exposed by the workflow.
