package android.media;

import java.util.HashMap;
import java.util.Map;

public final class MediaFormat {
    public static final String KEY_MIME = "mime";
    public static final String KEY_DURATION = "durationUs";
    public static final String KEY_WIDTH = "width";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_MAX_INPUT_SIZE = "max-input-size";
    public static final String KEY_FRAME_RATE = "frame-rate";

    private final Map<String, Object> mValues = new HashMap<String, Object>();

    public void setString(String name, String value) { mValues.put(name, value); }
    public void setInteger(String name, int value) { mValues.put(name, Integer.valueOf(value)); }
    public void setLong(String name, long value) { mValues.put(name, Long.valueOf(value)); }
    public boolean containsKey(String name) { return mValues.containsKey(name); }
    public String getString(String name) { return (String) mValues.get(name); }
    public int getInteger(String name) { return ((Integer) mValues.get(name)).intValue(); }
    public long getLong(String name) { return ((Long) mValues.get(name)).longValue(); }
    public Map<String, Object> getMap() { return mValues; }
}
