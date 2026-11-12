package dev.eaglefine;

import java.util.Collections;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.input.InputConstants;

/**
 * Hold-to-zoom camera feature, inspired by OptiFine's zoom control.
 *
 * <p>The key mapping uses Minecraft's normal controls system, including saved
 * and user-remappable key assignments. Zoom is applied to the world camera
 * only; the HUD FOV remains unchanged.</p>
 */
public final class EagleFineZoomFeature implements EagleFineFeature {
    public static final String ID = "zoom";
    private static final float SMOOTHING = 0.35F;

    private final KeyMapping keyMapping = new KeyMapping(
            "key.eaglefine.zoom", 67, KeyMapping.Category.MISC);
    private float currentZoom = 1.0F;
    private boolean keyMappingRegistered;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Zoom";
    }

    @Override
    public String getDescription() {
        return "Hold the assigned key to zoom the world camera.";
    }

    /**
     * Registers the zoom key after Minecraft has loaded its saved controls.
     */
    public boolean registerKeyMapping(Options options) {
        if (keyMappingRegistered) {
            return true;
        }

        InputConstants.Key savedKey = options.eaglerResolveSavedKeyMapping(keyMapping.getName());
        boolean registered = options.eaglerRegisterKeyMappings(
                Collections.singletonList(keyMapping),
                Collections.singletonList(savedKey));
        keyMappingRegistered = registered;
        return registered;
    }

    public boolean isKeyDown() {
        return keyMapping.isDown();
    }

    public KeyMapping getKeyMapping() {
        return keyMapping;
    }

    /**
     * Applies the configured zoom factor, smoothing transitions while the key
     * is pressed and released.
     */
    public float applyToFov(float fov, EagleFineConfig config) {
        float targetZoom = keyMapping.isDown() ? config.getZoomFactor() : 1.0F;
        if (config.isSmoothZoomEnabled()) {
            currentZoom += (targetZoom - currentZoom) * SMOOTHING;
            if (Math.abs(targetZoom - currentZoom) < 0.001F) {
                currentZoom = targetZoom;
            }
        } else {
            currentZoom = targetZoom;
        }

        return fov / Math.max(1.0F, currentZoom);
    }
}
