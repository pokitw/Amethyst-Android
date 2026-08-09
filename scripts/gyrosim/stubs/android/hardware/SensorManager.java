package android.hardware;
public class SensorManager {
    public Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_GRAVITY) return new Sensor(type);
        return null;
    }
    public boolean registerListener(SensorEventListener l, Sensor s, int us) { return true; }
    public void unregisterListener(SensorEventListener l) {}
}
