package dev.eaglefine;

/**
 * Entry point and shared services for EagleFine.
 *
 * <p>This class currently provides project metadata and the in-memory core
 * services. Minecraft lifecycle hooks are intentionally not connected yet;
 * that integration will be added after the baseline build is verified.</p>
 */
public final class EagleFine {
    public static final String MOD_ID = "eaglefine";
    public static final String NAME = "EagleFine";
    public static final String VERSION = "0.1.0-dev";

    private static final EagleFine INSTANCE = new EagleFine();

    private final EagleFineConfig config = new EagleFineConfig();
    private final EagleFineFeatureRegistry featureRegistry = new EagleFineFeatureRegistry();

    private EagleFine() {
    }

    /**
     * Returns the process-wide EagleFine instance.
     */
    public static EagleFine getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the in-memory feature configuration.
     */
    public EagleFineConfig getConfig() {
        return config;
    }

    /**
     * Returns the registry of features known to EagleFine.
     */
    public EagleFineFeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }
}
