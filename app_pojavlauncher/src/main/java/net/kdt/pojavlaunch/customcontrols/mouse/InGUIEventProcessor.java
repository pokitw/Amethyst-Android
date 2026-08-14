package net.kdt.pojavlaunch.customcontrols.mouse;

import android.view.GestureDetector;
import android.view.MotionEvent;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.SingleTapConfirm;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

public class InGUIEventProcessor implements TouchEventProcessor {
    public static final float FINGER_SCROLL_THRESHOLD = Tools.dpToPx(6);
    public static final float FINGER_STILL_THRESHOLD = Tools.dpToPx(5);

    private final PointerTracker mTracker = new PointerTracker();
    private final TapDetector mSingleTapDetector;
    private AbstractTouchpad mTouchpad;
    private boolean mIsMouseDown = false;
    private float mStartX, mStartY;
    private final Scroller mScroller = new Scroller(FINGER_SCROLL_THRESHOLD);

    /**
     * How far the fingers have travelled since the last one landed, while more than one is down.
     *
     * A second finger that taps and a second finger that begins a two-finger scroll look identical
     * at the moment it touches down, and only stop looking identical if it then moves. So the
     * click waits for the lift and asks this: a gesture that has scrolled is a scroll, whatever
     * the tap detector makes of how briefly it lasted.
     */
    private float mMultiTouchDrift = 0f;

    public InGUIEventProcessor() {
        mSingleTapDetector = new TapDetector(1, TapDetector.DETECTION_METHOD_BOTH);
    }

    @Override
    public boolean processTouchEvent(MotionEvent motionEvent) {
        boolean singleTap = mSingleTapDetector.onTouchEvent(motionEvent);

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTracker.startTracking(motionEvent);
                mMultiTouchDrift = 0f;
                if(!touchpadDisplayed()) {
                    sendTouchCoordinates(motionEvent.getX(), motionEvent.getY());

                    // disabled gestures means no scrolling possible, send gesture early
                    if (LauncherPreferences.PREF_DISABLE_GESTURES) enableMouse();
                    else setGestureStart(motionEvent);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                int pointerCount = motionEvent.getPointerCount();
                int pointerIndex = mTracker.trackEvent(motionEvent);
                if(pointerCount == 1 || LauncherPreferences.PREF_DISABLE_GESTURES) {
                    if(touchpadDisplayed()) {
                        mTouchpad.applyMotionVector(mTracker.getMotionVector());
                    } else {
                        float mainPointerX = motionEvent.getX(pointerIndex);
                        float mainPointerY = motionEvent.getY(pointerIndex);
                        sendTouchCoordinates(mainPointerX, mainPointerY);

                        if(!mIsMouseDown) {
                            if(!hasGestureStarted()) setGestureStart(motionEvent);
                            if(!LeftClickGesture.isFingerStill(mStartX, mStartY, FINGER_STILL_THRESHOLD))
                                enableMouse();
                        }

                    }
                } else {
                    float[] vector = mTracker.getMotionVector();
                    mMultiTouchDrift += Math.abs(vector[0]) + Math.abs(vector[1]);
                    mScroller.performScroll(vector);
                }
                break;

            // A finger arriving or leaving while others are still down. The tap detector is
            // watching both edges already, which is the whole reason the click below is one
            // condition rather than a second gesture recogniser.
            case MotionEvent.ACTION_POINTER_DOWN:
                // The newcomer gets its own chance to be a tap, whatever the fingers before it
                // have been doing.
                mMultiTouchDrift = 0f;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if(secondFingerClicks() && !mIsMouseDown && singleTap
                        && mMultiTouchDrift <= FINGER_STILL_THRESHOLD) {
                    CallbackBridge.putMouseEventWithCoords(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
                            CallbackBridge.mouseX, CallbackBridge.mouseY);
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mScroller.resetScrollOvershoot();
                mTracker.cancelTracking();

                // Handle single tap on gestures
                if((!LauncherPreferences.PREF_DISABLE_GESTURES || touchpadDisplayed()) && !mIsMouseDown && singleTap) {
                    CallbackBridge.putMouseEventWithCoords(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, CallbackBridge.mouseX, CallbackBridge.mouseY);
                }

                if(mIsMouseDown) disableMouse();
                resetGesture();
        }


        return true;
    }

    private boolean touchpadDisplayed() {
        return mTouchpad != null && mTouchpad.getDisplayState();
    }

    /**
     * Whether a tap by a second finger should click where the pointer already is.
     *
     * Only while the virtual mouse is on the screen, which is the whole situation this answers:
     * without it a touch in a menu puts the cursor under the finger and taps there, so there is
     * nothing a second finger could usefully add. With it, the finger moving the pointer has to
     * be lifted before it can tap, and lifting it is exactly what somebody keeping the pointer
     * somewhere precise does not want to do.
     */
    private boolean secondFingerClicks() {
        return LauncherPreferences.PREF_GUI_SECOND_FINGER_CLICK && touchpadDisplayed();
    }

    public void setAbstractTouchpad(AbstractTouchpad touchpad) {
        mTouchpad = touchpad;
    }

    /**
     * Put the cursor where the finger is, held inside the game's window.
     *
     * The clamp is for the reach setting: touches arrive from the whole panel so that a drag
     * anywhere still works, but this path is the one that places the cursor absolutely, and a
     * finger landing in the surround would otherwise ask for a position the game's window does
     * not contain. Without an inset the coordinates are already inside it and nothing is clamped.
     */
    private void sendTouchCoordinates(float x, float y) {
        float gameX = x * LauncherPreferences.PREF_SCALE_FACTOR;
        float gameY = y * LauncherPreferences.PREF_SCALE_FACTOR;
        CallbackBridge.sendCursorPos(
                Math.max(0, Math.min(CallbackBridge.windowWidth, gameX)),
                Math.max(0, Math.min(CallbackBridge.windowHeight, gameY)));
    }

    private void enableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, true);
        mIsMouseDown = true;
    }

    private void disableMouse() {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, false);
        mIsMouseDown = false;
    }

    private void setGestureStart(MotionEvent event) {
        mStartX = event.getX() * LauncherPreferences.PREF_SCALE_FACTOR;
        mStartY = event.getY() * LauncherPreferences.PREF_SCALE_FACTOR;
    }

    private void resetGesture() {
        mStartX = mStartY = -1;
    }

    private boolean hasGestureStarted() {
        return mStartX != -1 || mStartY != -1;
    }

    @Override
    public void cancelPendingActions() {
        mScroller.resetScrollOvershoot();
        disableMouse();
    }
}
