# EagleFine components

This directory is reserved for EagleFine-owned source and design material. The first code changes will be added only after the upstream Java compile check succeeds.

Planned logical components:

| Component | Responsibility |
| --- | --- |
| `core` | Feature lifecycle, shared interfaces, and configuration access |
| `settings` | User-facing graphics options and persistence |
| `graphics` | Rendering features compatible with the browser renderer |
| `performance` | Profiling-led performance and memory improvements |
| `shaders` | Optional post-processing effects and shader management |

The eventual Java package namespace will be `dev.eaglefine`. Keep new implementation code out of `net.minecraft` unless an integration point genuinely requires a small upstream modification.
