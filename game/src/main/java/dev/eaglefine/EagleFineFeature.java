package dev.eaglefine;

/**
 * Describes one optional EagleFine feature.
 *
 * <p>Feature implementations should keep browser-specific rendering work
 * behind the relevant Eaglercraft integration layer. Registering a feature
 * does not automatically enable it or change game behaviour.</p>
 */
public interface EagleFineFeature {
    /**
     * Returns a stable identifier such as {@code zoom} or {@code dynamic_lights}.
     */
    String getId();

    /**
     * Returns the user-facing feature name.
     */
    String getDisplayName();

    /**
     * Returns a short explanation of the feature.
     */
    String getDescription();
}
