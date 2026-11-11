package dev.eaglefine;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.EaglerInputStream;
import net.lax1dude.eaglercraft.v1_8.EaglerOutputStream;
import net.lax1dude.eaglercraft.v1_8.EaglerZLIB;
import org.slf4j.Logger;

/**
 * Stores EagleFine feature toggles.
 *
 * <p>All features default to disabled. Settings are saved as versioned,
 * compressed JSON in Eaglercraft's browser storage, rather than in a separate
 * desktop-only file. This keeps configuration available in the browser build.</p>
 */
public final class EagleFineConfig {
    private static final String STORAGE_KEY = "eaglefine.config.v1";
    private static final int CONFIG_VERSION = 1;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, Boolean> featureStates = new LinkedHashMap<String, Boolean>();

    /**
     * Loads the last saved configuration. Missing or invalid data leaves all
     * features at their safe, disabled defaults.
     */
    public synchronized void load() {
        final byte[] stored = EagRuntime.getStorage(STORAGE_KEY);
        if (stored == null || stored.length == 0) {
            return;
        }

        try {
            final String json;
            try (InputStream input = EaglerZLIB.newGZIPInputStream(new EaglerInputStream(stored))) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("Root value is not a JSON object");
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonElement versionElement = root.get("version");
            JsonElement featuresElement = root.get("features");
            if (versionElement == null || !versionElement.isJsonPrimitive()
                    || !versionElement.getAsJsonPrimitive().isNumber()
                    || versionElement.getAsInt() != CONFIG_VERSION
                    || featuresElement == null || !featuresElement.isJsonObject()) {
                LOGGER.warn("Ignoring EagleFine configuration with an unsupported or invalid format");
                return;
            }

            Map<String, Boolean> loaded = new LinkedHashMap<String, Boolean>();
            for (Map.Entry<String, JsonElement> entry : featuresElement.getAsJsonObject().entrySet()) {
                String id = entry.getKey();
                JsonElement value = entry.getValue();
                if (!isValidFeatureId(id) || value == null || !value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive().isBoolean()) {
                    continue;
                }
                loaded.put(id, Boolean.valueOf(value.getAsBoolean()));
            }

            featureStates.clear();
            featureStates.putAll(loaded);
        } catch (Exception exception) {
            LOGGER.warn("Could not load EagleFine configuration; using disabled defaults", exception);
        }
    }

    /**
     * Returns whether a feature is enabled. Unknown features are disabled.
     */
    public synchronized boolean isFeatureEnabled(String featureId) {
        String id = requireFeatureId(featureId);
        Boolean enabled = featureStates.get(id);
        return enabled != null && enabled.booleanValue();
    }

    /**
     * Sets and persists the enabled state for a feature identifier.
     */
    public synchronized void setFeatureEnabled(String featureId, boolean enabled) {
        String id = requireFeatureId(featureId);
        Boolean previous = featureStates.put(id, Boolean.valueOf(enabled));
        if (previous == null || previous.booleanValue() != enabled) {
            save();
        }
    }

    /**
     * Removes one feature's saved state, restoring its disabled default.
     */
    public synchronized void resetFeature(String featureId) {
        if (featureStates.remove(requireFeatureId(featureId)) != null) {
            save();
        }
    }

    /**
     * Resets every feature toggle to its disabled default.
     */
    public synchronized void reset() {
        if (!featureStates.isEmpty()) {
            featureStates.clear();
            save();
        }
    }

    /**
     * Returns an immutable snapshot of explicitly stored feature states.
     */
    public synchronized Map<String, Boolean> getFeatureStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(featureStates));
    }

    /**
     * Writes a complete configuration payload before replacing the browser
     * storage value. If serialization or compression fails, the previous
     * stored configuration remains intact.
     */
    public synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", CONFIG_VERSION);

        JsonObject features = new JsonObject();
        for (Map.Entry<String, Boolean> entry : featureStates.entrySet()) {
            features.addProperty(entry.getKey(), entry.getValue().booleanValue());
        }
        root.add("features", features);

        EaglerOutputStream output = new EaglerOutputStream();
        try (OutputStream gzip = EaglerZLIB.newGZIPOutputStream(output)) {
            gzip.write(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            LOGGER.error("Could not save EagleFine configuration", exception);
            return;
        }

        EagRuntime.setStorage(STORAGE_KEY, output.toByteArray());
    }

    private static String requireFeatureId(String featureId) {
        Objects.requireNonNull(featureId, "featureId");
        String id = featureId.trim();
        if (!isValidFeatureId(id)) {
            throw new IllegalArgumentException("Invalid EagleFine feature ID: " + featureId);
        }
        return id;
    }

    private static boolean isValidFeatureId(String id) {
        if (id == null || id.isEmpty() || id.length() > 96) {
            return false;
        }

        for (int i = 0; i < id.length(); ++i) {
            char character = id.charAt(i);
            if (!(Character.isLetterOrDigit(character) || character == '.' || character == '_' || character == '-')) {
                return false;
            }
        }
        return true;
    }
}
