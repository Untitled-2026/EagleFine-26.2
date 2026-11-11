package dev.eaglefine;

/**
 * Entry point and shared services for EagleFine.
 *
 * <p>This class provides project metadata and shared core services. Minecraft
 * calls {@link #initialize()} after its normal options have loaded.</p>
 */
public final class EagleFine {
    public static final String MOD_ID = "eaglefine";
    public static final String NAME = "EagleFine";
    public static final String VERSION = "0.1.0-dev";

    private static final EagleFine INSTANCE = new EagleFine();

    private final EagleFineConfig config = new EagleFineConfig();
    private final EagleFineFeatureRegistry featureRegistry = new EagleFineFeatureRegistry();
    private boolean initialized;

    private EagleFine() {
    }

    /**
     * Returns the process-wide EagleFine instance.
     */
    public static EagleFine getInstance() {
        return INSTANCE;
    }

    /**
     * Loads EagleFine configuration once during client startup.
     *
     * <p>This method is safe to call more than once.</p>
     */
    public synchronized void initialize() {
        if (initialized) {
            return;
        }

        config.load();
        initialized = true;
    }

    /**
     * Returns whether the client startup hook has initialized EagleFine.
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the shared feature configuration.
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
