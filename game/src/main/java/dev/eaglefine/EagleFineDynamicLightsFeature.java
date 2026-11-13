package dev.eaglefine;

/** Metadata for EagleFine's held-item Dynamic Lights feature. */
public final class EagleFineDynamicLightsFeature implements EagleFineFeature {
    public static final String ID = "dynamic_lights";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Dynamic Lights";
    }

    @Override
    public String getDescription() {
        return "Adds block-light from held, dropped and displayed items, glowing or burning entities, primed TNT, and lightning.";
    }
}
