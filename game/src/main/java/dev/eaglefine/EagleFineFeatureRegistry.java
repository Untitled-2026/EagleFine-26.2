package dev.eaglefine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maintains the features available to EagleFine.
 *
 * <p>Registration is explicit. This registry does not load classes
 * reflectively, discover mods, or invoke rendering hooks.</p>
 */
public final class EagleFineFeatureRegistry {
    private final Map<String, EagleFineFeature> features =
            new LinkedHashMap<String, EagleFineFeature>();

    /**
     * Registers a feature.
     *
     * @throws NullPointerException if {@code feature} or its ID is null
     * @throws IllegalArgumentException if the ID is empty or already registered
     */
    public void register(EagleFineFeature feature) {
        Objects.requireNonNull(feature, "feature");

        String id = Objects.requireNonNull(feature.getId(), "feature.getId()").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Feature ID must not be empty");
        }
        if (features.containsKey(id)) {
            throw new IllegalArgumentException("Feature is already registered: " + id);
        }

        features.put(id, feature);
    }

    /**
     * Returns the feature registered under the given ID, or {@code null}.
     */
    public EagleFineFeature get(String featureId) {
        if (featureId == null) {
            return null;
        }
        return features.get(featureId.trim());
    }

    /**
     * Returns an immutable snapshot in registration order.
     */
    public List<EagleFineFeature> getAll() {
        return Collections.unmodifiableList(new ArrayList<EagleFineFeature>(features.values()));
    }

    /**
     * Returns the number of registered features.
     */
    public int size() {
        return features.size();
    }

    /**
     * Returns whether no features have been registered.
     */
    public boolean isEmpty() {
        return features.isEmpty();
    }
}
