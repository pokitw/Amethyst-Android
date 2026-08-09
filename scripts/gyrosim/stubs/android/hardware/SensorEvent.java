package android.hardware;
public class SensorEvent {
    public final float[] values = new float[3];
    public long timestamp;
    public Sensor sensor;
}
