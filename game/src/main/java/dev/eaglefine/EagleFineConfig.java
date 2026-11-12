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
 * Stores EagleFine feature toggles and graphics preferences using versioned,
 * compressed JSON in Eaglercraft's browser storage.
 */
public final class EagleFineConfig {
    private static final String STORAGE_KEY = "eaglefine.config.v1";
    private static final int CONFIG_VERSION = 1;
    private static final float DEFAULT_ZOOM_FACTOR = 4.0F;
    private static final float MIN_ZOOM_FACTOR = 2.0F;
    private static final float MAX_ZOOM_FACTOR = 16.0F;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, Boolean> featureStates = new LinkedHashMap<String, Boolean>();
    private float zoomFactor = DEFAULT_ZOOM_FACTOR;
    private boolean smoothZoomEnabled = true;

    /**
     * Loads the last saved configuration. Missing or invalid data leaves safe
     * defaults in place.
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

            float loadedZoomFactor = DEFAULT_ZOOM_FACTOR;
            JsonElement zoomElement = root.get("zoomFactor");
            if (zoomElement != null && zoomElement.isJsonPrimitive()
                    && zoomElement.getAsJsonPrimitive().isNumber()) {
                float candidate = zoomElement.getAsFloat();
                if (Float.isFinite(candidate)) {
                    loadedZoomFactor = clampZoomFactor(candidate);
                }
            }

            boolean loadedSmoothZoom = true;
            JsonElement smoothElement = root.get("smoothZoom");
            if (smoothElement != null && smoothElement.isJsonPrimitive()
                    && smoothElement.getAsJsonPrimitive().isBoolean()) {
                loadedSmoothZoom = smoothElement.getAsBoolean();
            }

            featureStates.clear();
            featureStates.putAll(loaded);
            zoomFactor = loadedZoomFactor;
            smoothZoomEnabled = loadedSmoothZoom;
        } catch (Exception exception) {
            LOGGER.warn("Could not load EagleFine configuration; using safe defaults", exception);
        }
    }

    public synchronized boolean isFeatureEnabled(String featureId) {
        String id = requireFeatureId(featureId);
        Boolean enabled = featureStates.get(id);
        return enabled != null && enabled.booleanValue();
    }

    public synchronized void setFeatureEnabled(String featureId, boolean enabled) {
        String id = requireFeatureId(featureId);
        Boolean previous = featureStates.put(id, Boolean.valueOf(enabled));
        if (previous == null || previous.booleanValue() != enabled) {
            save();
        }
    }

    public synchronized void resetFeature(String featureId) {
        if (featureStates.remove(requireFeatureId(featureId)) != null) {
            save();
        }
    }

    public synchronized void reset() {
        boolean changed = !featureStates.isEmpty()
                || Float.compare(zoomFactor, DEFAULT_ZOOM_FACTOR) != 0
                || !smoothZoomEnabled;
        if (changed) {
            featureStates.clear();
            zoomFactor = DEFAULT_ZOOM_FACTOR;
            smoothZoomEnabled = true;
            save();
        }
    }

    public synchronized float getZoomFactor() {
        return zoomFactor;
    }

    /**
     * Sets zoom strength. Values are limited to 2x through 16x.
     */
    public synchronized void setZoomFactor(float zoomFactor) {
        if (!Float.isFinite(zoomFactor)) {
            throw new IllegalArgumentException("Zoom factor must be a finite number");
        }
        float clamped = clampZoomFactor(zoomFactor);
        if (Float.compare(this.zoomFactor, clamped) != 0) {
            this.zoomFactor = clamped;
            save();
        }
    }

    public synchronized boolean isSmoothZoomEnabled() {
        return smoothZoomEnabled;
    }

    public synchronized void setSmoothZoomEnabled(boolean enabled) {
        if (smoothZoomEnabled != enabled) {
            smoothZoomEnabled = enabled;
            save();
        }
    }

    public synchronized Map<String, Boolean> getFeatureStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(featureStates));
    }

    /**
     * Writes a complete configuration payload before replacing browser
     * storage.
     */
    public synchronized void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", CONFIG_VERSION);
        root.addProperty("zoomFactor", zoomFactor);
        root.addProperty("smoothZoom", smoothZoomEnabled);

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

    private static float clampZoomFactor(float value) {
        return Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, value));
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
