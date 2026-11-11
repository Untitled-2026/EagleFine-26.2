# EagleFine architecture

## Initial Java package

EagleFine-owned Java code lives under:

```text
game/src/main/java/dev/eaglefine/
```

The initial package deliberately avoids putting EagleFine classes in Minecraft's
`net.minecraft` namespace.

## Core classes

- `EagleFine`: process-wide entry point and startup initialization.
- `EagleFineConfig`: versioned feature-toggle storage using Eaglercraft's
  browser storage API. All features default to disabled.
- `EagleFineFeature`: metadata contract for a feature implementation.
- `EagleFineFeatureRegistry`: explicit registry that preserves registration
  order and rejects duplicate IDs.

## Startup integration

Minecraft initializes EagleFine immediately after constructing its normal
`Options` object. This ensures Eaglercraft's own settings have loaded before
EagleFine reads its independent configuration.

The configuration is stored under the Eaglercraft storage key
`eaglefine.config.v1` as gzip-compressed, versioned JSON. It is intentionally
separate from `options.txt` and the legacy Eaglercraft `g` storage key.

Invalid or unknown configuration data is ignored, leaving feature toggles
disabled. Feature state changes are saved immediately.

## Current limitations

EagleFine currently:

- initializes during client startup;
- loads and saves a separate feature-toggle configuration;
- exposes a feature registry, but does not register gameplay/render features;
- does not add a settings screen or key binding;
- does not change rendering, lighting, textures, or performance.

Those integrations will be added individually and tested against the browser
platform.

## Integration rule

Prefer adding an EagleFine-owned class and a narrow, documented integration
point over spreading project-specific state throughout Minecraft classes.
Any renderer modification must preserve the unmodified path when the feature
is disabled and must include a fallback for unsupported browser/GPU features.
