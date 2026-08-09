package org.lwjgl.glfw;
public class CallbackBridge {
    public static float mouseX, mouseY;
    public static boolean grabbing = true;
    public static boolean isGrabbing() { return grabbing; }
    public static void addGrabListener(net.kdt.pojavlaunch.GrabListener l) {}
    public static void removeGrabListener(net.kdt.pojavlaunch.GrabListener l) {}
    public static void sendCursorPos(float x, float y) { mouseX = x; mouseY = y; }
}
