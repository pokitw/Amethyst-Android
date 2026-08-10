package net.kdt.pojavlaunch.optimiser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the settings were before performance mode touched them.
 *
 * <b>A switch that cannot be switched off is a button.</b> Performance mode changes a dozen
 * settings at once, and the only thing that makes that acceptable is being able to put them all
 * back exactly as they were. So the values are captured before anything is written, and turning
 * the mode off restores that capture rather than writing a launcher's idea of defaults over
 * choices somebody made deliberately.
 *
 * <b>Absence is a value.</b> This is the distinction an obvious implementation loses: a setting
 * that was never written is not a setting holding its default. Restoring the first has to REMOVE
 * the key so the launcher goes back to computing it, which several of these are (the memory
 * allocation is worked out from the device on first run, and writing a number there freezes it
 * forever on whatever phone happened to be in hand). A null in the restored map means exactly
 * that, and survives the round trip as a real null rather than being dropped.
 *
 * <b>Gson rather than org.json</b>, which is the other JSON on this platform, for one reason:
 * Gson is a jar this app already ships, so {@code scripts/perfsim} can compile and drive the
 * shipped class against the same library the phone will use. Verifying this against a hand-made
 * stub of the platform's JSON would be verifying the stub.
 */
public final class PerformanceBackup {

    private PerformanceBackup() {}

    /**
     * Record the current value of every key performance mode is about to write.
     *
     * @param current the values as they are now; a key absent here, or mapped to null, was never
     *                set and must come back as never set
     * @param keys    the keys performance mode owns
     */
    @NonNull
    public static String capture(@NonNull Map<String, Object> current, @NonNull String[] keys) {
        JsonObject json = new JsonObject();
        for (String key : keys) {
            Object value = current.get(key);
            if (value == null) {
                // Written explicitly. Leaving the key out would make "never set" and "not one of
                // ours" the same thing on the way back, and the restore would skip it.
                json.add(key, JsonNull.INSTANCE);
            } else if (value instanceof Boolean) {
                json.addProperty(key, (Boolean) value);
            } else if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else {
                json.addProperty(key, String.valueOf(value));
            }
        }
        return json.toString();
    }

    /**
     * Read a capture back.
     *
     * @return key to value, where a null value means the key was never set and must be removed
     *         rather than written. An unreadable capture gives an empty map, which the caller
     *         should treat as "there is nothing to restore" rather than as "restore nothing":
     *         the difference is whether the switch may claim it turned anything off.
     */
    @NonNull
    public static Map<String, Object> restore(@Nullable String captured) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (captured == null || captured.isEmpty()) return values;
        try {
            JsonElement parsed = JsonParser.parseString(captured);
            if (!parsed.isJsonObject()) return values;
            for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                JsonElement element = entry.getValue();
                if (element == null || element.isJsonNull()) {
                    values.put(entry.getKey(), null);
                    continue;
                }
                if (!element.isJsonPrimitive()) {
                    values.put(entry.getKey(), null);
                    continue;
                }
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    values.put(entry.getKey(), primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    // Every preference performance mode owns that is numeric is an int; reading
                    // it as one keeps SharedPreferences.putInt from being handed a Double.
                    values.put(entry.getKey(), primitive.getAsInt());
                } else {
                    values.put(entry.getKey(), primitive.getAsString());
                }
            }
        } catch (Throwable t) {
            return new LinkedHashMap<>();
        }
        return values;
    }

    /** Whether a capture holds anything, which is what decides if the mode can be turned off. */
    public static boolean isUsable(@Nullable String captured) {
        return captured != null && !captured.isEmpty() && !"{}".equals(captured);
    }
}
