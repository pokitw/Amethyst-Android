package android.hardware;
public class Sensor {
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_GRAVITY = 9;
    public static final int TYPE_ACCELEROMETER = 1;
    private final int mType;
    public Sensor(int type) { mType = type; }
    public int getType() { return mType; }
}
