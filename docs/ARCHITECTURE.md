# EagleFine architecture

## Initial Java package

EagleFine-owned Java code lives under:

```text
game/src/main/java/dev/eaglefine/
```

The initial package deliberately avoids putting EagleFine classes in Minecraft's
`net.minecraft` namespace.

## Core classes

- `EagleFine`: process-wide entry point and access to shared core services.
- `EagleFineConfig`: in-memory feature toggle storage. Unknown features default
  to disabled. Persistent storage is not implemented yet.
- `EagleFineFeature`: metadata contract for a feature implementation.
- `EagleFineFeatureRegistry`: explicit registry that preserves registration
  order and rejects duplicate IDs.

## Current limitations

These classes are scaffolding only. At this stage EagleFine:

- does not register itself with Minecraft's startup lifecycle;
- does not add a settings screen or key binding;
- does not change rendering, lighting, textures, or performance;
- does not save configuration between sessions.

Those integrations will be added individually after the source compile check
has passed and the relevant browser platform APIs have been reviewed.

## Integration rule

Prefer adding an EagleFine-owned class and a narrow, documented integration
point over spreading project-specific state throughout Minecraft classes.
Any renderer modification must preserve the unmodified path when the feature
is disabled and must include a fallback for unsupported browser/GPU features.
