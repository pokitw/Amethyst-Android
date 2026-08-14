package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.os.Vibrator;

import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient;
import top.fifthlight.touchcontroller.proxy.client.MessageTransport;
import top.fifthlight.touchcontroller.proxy.client.PlatformCapability;
import top.fifthlight.touchcontroller.proxy.client.android.transport.UnixSocketTransportKt;
import top.fifthlight.touchcontroller.proxy.message.VibrateMessage;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.Set;

public class TouchControllerUtils {
    private TouchControllerUtils() {
    }

    public static LauncherProxyClient proxyClient;
    private static final String socketName = "Amethyst";

    private static class VibrationHandler implements LauncherProxyClient.VibrationHandler {
        private final Vibrator vibrator;

        public VibrationHandler(Vibrator vibrator) {
            this.vibrator = vibrator;
        }

        @Override
        @SuppressWarnings("DEPRECATION")
        public void vibrate(@NonNull VibrateMessage.Kind kind) {
            vibrator.vibrate(LauncherPreferences.PREF_TOUCHCONTROLLER_VIBRATE_LENGTH);
        }
    }

    private static final SparseIntArray pointerIdMap = new SparseIntArray();
    private static int nextPointerId = 1;

    /**
     * @param width  the game's own width, which is not always the view's: the reach setting can
     *               draw the game inside part of the screen, and these fractions have to be of
     *               the game the mod is talking about rather than of the panel around it.
     */
    public static void processTouchEvent(MotionEvent motionEvent, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (proxyClient == null) {
            return;
        }
        int pointerId;
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerId = nextPointerId++;
                pointerIdMap.put(motionEvent.getPointerId(0), pointerId);
                proxyClient.addPointer(pointerId, motionEvent.getX(0) / width, motionEvent.getY(0) / height);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerId = nextPointerId++;
                int actionIndex = motionEvent.getActionIndex();
                pointerIdMap.put(motionEvent.getPointerId(actionIndex), pointerId);
                proxyClient.addPointer(pointerId, motionEvent.getX(actionIndex) / width, motionEvent.getY(actionIndex) / height);
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < motionEvent.getPointerCount(); i++) {
                    pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                    if (pointerId == 0) {
                        Log.d("TouchController", "Move pointerId is 0");
                        continue;
                    }
                    proxyClient.addPointer(pointerId, motionEvent.getX(i) / width, motionEvent.getY(i) / height);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (proxyClient != null) {
                    proxyClient.clearPointer();
                    pointerIdMap.clear();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (proxyClient != null) {
                    int i = motionEvent.getActionIndex();
                    pointerId = pointerIdMap.get(motionEvent.getPointerId(i));
                    if (pointerId == 0) {
                        Log.d("TouchController", "Pointer up pointerId is 0");
                        break;
                    }
                    pointerIdMap.delete(pointerId);
                    proxyClient.removePointer(pointerId);
                }
                break;
        }
    }

    public static void initialize(Context context, TouchControllerInputView touchControllerInputView) {
        if (proxyClient != null) {
            return;
        }
        try {
            Os.setenv("TOUCH_CONTROLLER_PROXY_SOCKET", socketName, true);
        } catch (ErrnoException e) {
            Log.w("TouchController", "Failed to set TouchController environment variable", e);
        }
        MessageTransport transport = UnixSocketTransportKt.UnixSocketTransport(socketName);
        proxyClient = new LauncherProxyClient(transport, Set.of(PlatformCapability.TEXT_STATUS, PlatformCapability.KEYBOARD_SHOW));
        proxyClient.run();
        touchControllerInputView.setClient(proxyClient);
        Vibrator vibrator = ContextCompat.getSystemService(context, Vibrator.class);
        if (vibrator != null) {
            LauncherProxyClient.VibrationHandler vibrationHandler = new VibrationHandler(vibrator);
            proxyClient.setVibrationHandler(vibrationHandler);
        }
    }
}
