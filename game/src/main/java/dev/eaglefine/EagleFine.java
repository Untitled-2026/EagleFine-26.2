package dev.eaglefine;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Options;
import org.slf4j.Logger;

/**
 * Entry point and shared services for EagleFine.
 *
 * <p>Minecraft calls {@link #initialize(Options)} after its normal options
 * have loaded so EagleFine can register its own key mappings safely.</p>
 */
public final class EagleFine {
    public static final String MOD_ID = "eaglefine";
    public static final String NAME = "EagleFine";
    public static final String VERSION = "0.1.0-dev";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EagleFine INSTANCE = new EagleFine();

    private final EagleFineConfig config = new EagleFineConfig();
    private final EagleFineFeatureRegistry featureRegistry = new EagleFineFeatureRegistry();
    private final EagleFineZoomFeature zoomFeature = new EagleFineZoomFeature();
    private final EagleFineDynamicLightsFeature dynamicLightsFeature = new EagleFineDynamicLightsFeature();
    private boolean initialized;

    private EagleFine() {
        featureRegistry.register(zoomFeature);
        featureRegistry.register(dynamicLightsFeature);
    }

    public static EagleFine getInstance() {
        return INSTANCE;
    }

    /**
     * Loads settings and registers EagleFine key mappings once during startup.
     */
    public synchronized void initialize(Options options) {
        if (initialized) {
            return;
        }

        config.load();
        if (!zoomFeature.registerKeyMapping(options)) {
            LOGGER.error("Could not register the EagleFine zoom key mapping");
        }
        initialized = true;
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Applies the active zoom factor to a world-camera field of view.
     */
    public float applyZoomToFov(float fov) {
        if (!initialized) {
            return fov;
        }
        return zoomFeature.applyToFov(fov, config);
    }

    public EagleFineConfig getConfig() {
        return config;
    }

    public EagleFineFeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    public EagleFineZoomFeature getZoomFeature() {
        return zoomFeature;
    }
}
