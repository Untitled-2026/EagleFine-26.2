package dev.eaglefine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores EagleFine feature toggles.
 *
 * <p>All features default to disabled. This first implementation is
 * intentionally in-memory only; persistence and a settings screen will be
 * added after the client integration points are understood and tested.</p>
 */
public final class EagleFineConfig {
    private final Map<String, Boolean> featureStates = new LinkedHashMap<String, Boolean>();

    /**
     * Returns whether a feature is enabled. Unknown features are disabled.
     */
    public boolean isFeatureEnabled(String featureId) {
        String id = requireFeatureId(featureId);
        Boolean enabled = featureStates.get(id);
        return enabled != null && enabled.booleanValue();
    }

    /**
     * Sets the enabled state for a feature identifier.
     */
    public void setFeatureEnabled(String featureId, boolean enabled) {
        String id = requireFeatureId(featureId);
        featureStates.put(id, Boolean.valueOf(enabled));
    }

    /**
     * Removes one feature's saved in-memory state, restoring its disabled default.
     */
    public void resetFeature(String featureId) {
        featureStates.remove(requireFeatureId(featureId));
    }

    /**
     * Resets every feature toggle to its disabled default.
     */
    public void reset() {
        featureStates.clear();
    }

    /**
     * Returns an immutable snapshot of explicitly stored feature states.
     */
    public Map<String, Boolean> getFeatureStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(featureStates));
    }

    private static String requireFeatureId(String featureId) {
        String id = Objects.requireNonNull(featureId, "featureId").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Feature ID must not be empty");
        }
        return id;
    }
}
