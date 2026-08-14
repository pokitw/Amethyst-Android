package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NONE;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_NORTH_WEST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH_EAST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_SOUTH_WEST;
import static net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick.DIRECTION_WEST;

import android.annotation.SuppressLint;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.ControlSkin;
import net.kdt.pojavlaunch.customcontrols.ControlTestBridge;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadJoystick;

import org.lwjgl.glfw.CallbackBridge;

import io.github.controlwear.virtual.joystick.android.JoystickView;

@SuppressLint("ViewConstructor")
public class ControlJoystick extends JoystickView implements ControlInterface {
    public final static int DIRECTION_FORWARD_LOCK = 8;
    // Directions keycode
    private final int[] mDirectionForwardLock = new int[]{LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL};
    private final int[] mDirectionForward = new int[]{LwjglGlfwKeycode.GLFW_KEY_W};
    private final int[] mDirectionRight = new int[]{LwjglGlfwKeycode.GLFW_KEY_D};
    private final int[] mDirectionBackward = new int[]{LwjglGlfwKeycode.GLFW_KEY_S};
    private final int[] mDirectionLeft = new int[]{LwjglGlfwKeycode.GLFW_KEY_A};
    private ControlJoystickData mControlData;
    private int mLastDirectionInt = GamepadJoystick.DIRECTION_NONE;
    private int mCurrentDirectionInt = GamepadJoystick.DIRECTION_NONE;

    /**
     * Double-tap to lock movement in that direction, so a long walk does not need a thumb sat on
     * the stick the whole way. See {@link #directionOfTap} for why the direction is read from
     * where the tap landed rather than from the stick's own reporting.
     */
    private boolean mAutoWalkLocked = false;
    private int mAutoWalkDirection = GamepadJoystick.DIRECTION_NONE;
    /**
     * Whether the lock's key-down was swallowed by a test session rather than sent to a game.
     *
     * A lock is the one thing here that outlives the touch that started it, so it is also the one
     * thing that can still be held when a test session ends. The session detaches the bridge
     * before the layout leaves test mode, and after that {@code ControlTestBridge.consume} returns
     * false, so a release sent on the way out would fall through to {@code CallbackBridge} and
     * look for a native symbol that is not in the launcher process. Nothing was ever really
     * pressed in that case, so nothing has to be really released.
     */
    private boolean mAutoWalkSimulated = false;
    private final GestureDetector mDoubleTapDetector;

    /** A tap nearer the middle than this could mean any direction, so it is not one. */
    private static final float AUTO_WALK_MIN_TAP_DP = 15f;

    public ControlJoystick(ControlLayout parent, ControlJoystickData data) {
        super(parent.getContext());
        mDoubleTapDetector = new GestureDetector(parent.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                engageAutoWalk(directionOfTap(e));
                return true;
            }
        });
        init(data, parent);
    }

    private static void sendInput(int[] keys, boolean isDown) {
        for (int key : keys) {
            // The joystick has its own send rather than going through sendKeyPresses, which is
            // stubbed out on this class, so it needs the test seam separately. Missing it would
            // leave the one control that is hardest to place by eye untestable.
            if (ControlTestBridge.consume(key, isDown)) continue;
            CallbackBridge.sendKeyPress(key, CallbackBridge.getCurrentMods(), isDown);
        }
    }

    private void init(ControlJoystickData data, ControlLayout layout) {
        mControlData = data;
        setProperties(preProcessProperties(data, layout));
        setDeadzone(35);
        setFixedCenter(data.absolute);
        setAutoReCenterButton(true);

        injectBehaviors();

        setOnMoveListener(new OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // While locked the held keys are driven by the lock, not by wherever the stick's
                // knob happens to be sitting: every tap that engages or cancels the lock also
                // recentres the knob, and reading that recentre here would release the very keys
                // the lock is meant to be holding down.
                if (mAutoWalkLocked) return;

                mLastDirectionInt = mCurrentDirectionInt;
                mCurrentDirectionInt = getDirectionInt(angle, strength);

                if (mLastDirectionInt != mCurrentDirectionInt) {
                    sendDirectionalKeycode(mLastDirectionInt, false);
                    sendDirectionalKeycode(mCurrentDirectionInt, true);
                }
            }

            @Override
            public void onForwardLock(boolean isLocked) {
                sendInput(mDirectionForwardLock, isLocked);
            }
        });
    }

    /**
     * Touching the stick always takes manual control back, whatever it was doing.
     *
     * Cancelling on the first down of a fresh touch, rather than waiting to see if it turns into
     * a drag, is what lets the very next double-tap re-lock in a new direction without a dead
     * step in between: the first tap of that pair cancels the old lock here, and the second tap
     * engages the new one in {@link #engageAutoWalk}.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mAutoWalkLocked) {
            stopAutoWalk();
        }
        boolean handled = super.onTouchEvent(event);
        // Fed only when the setting is on, so a double-tap does nothing extra for anyone who has
        // not turned it on: the gesture costs nothing to a normal drag either way, since a tap
        // detector never fires on a single continuous hold.
        if (mControlData.autoWalk) mDoubleTapDetector.onTouchEvent(event);
        return handled;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // The layout can be torn down, or this control deleted in the editor, with the lock still
        // engaged. Whatever key it was holding must not outlive the view that was holding it.
        stopAutoWalk();
    }

    /**
     * Which of the eight directions a tap landed in, or none if it landed too near the middle.
     *
     * Deliberately read from where the tap touched the view rather than from
     * {@link #mCurrentDirectionInt}: the stick's own reporting is relative to its floating centre
     * in the (default) relative tracking mode, which recentres to wherever a touch begins, so a
     * stationary tap always measures zero distance from it and would never resolve to a
     * direction at all. Measuring against the view's own fixed centre instead works the same way
     * regardless of the absolute/relative setting, and a screen tap is exactly what a player
     * means by "point the way I want to walk" either way.
     */
    private int directionOfTap(MotionEvent e) {
        float dx = e.getX() - getWidth() / 2f;
        float dy = e.getY() - getHeight() / 2f;
        if (Math.hypot(dx, dy) < Tools.dpToPx(AUTO_WALK_MIN_TAP_DP)) return DIRECTION_NONE;
        // The same protractor the library's own getAngle() uses, so a tap at the top of the ring
        // and a drag to the top of the ring resolve to the same direction.
        double angle = Math.toDegrees(Math.atan2(-dy, dx));
        if (angle < 0) angle += 360;
        return getDirectionInt((int) angle, 1);
    }

    private void engageAutoWalk(int direction) {
        if (direction == DIRECTION_NONE) return;
        if (mAutoWalkLocked) sendDirectionalKeycode(mAutoWalkDirection, false);
        mAutoWalkLocked = true;
        mAutoWalkSimulated = ControlTestBridge.isActive();
        mAutoWalkDirection = direction;
        sendDirectionalKeycode(direction, true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        applyLockVisual(true);
    }

    private void stopAutoWalk() {
        if (!mAutoWalkLocked) return;
        // Sent only if there is still somewhere for it to go: a lock engaged inside a test
        // session whose bridge has since detached has nothing to release, and asking anyway
        // would take the real path into a symbol the launcher process does not have.
        if (!mAutoWalkSimulated || ControlTestBridge.isActive()) {
            sendDirectionalKeycode(mAutoWalkDirection, false);
        }
        forgetAutoWalk();
    }

    /**
     * Drop the lock without sending anything.
     *
     * Called when a test session ends, where the keys were never really pressed, and used by
     * {@link #stopAutoWalk()} for the state it has in common with a real release.
     */
    public void forgetAutoWalk() {
        if (!mAutoWalkLocked) return;
        mAutoWalkLocked = false;
        mAutoWalkSimulated = false;
        mAutoWalkDirection = DIRECTION_NONE;
        // The knob has already recentred by the time anything reaches here, so the next real
        // report is a fresh baseline rather than a diff against whatever the lock last claimed.
        mCurrentDirectionInt = DIRECTION_NONE;
        mLastDirectionInt = DIRECTION_NONE;
        applyLockVisual(false);
    }

    /** The one visible sign the stick is now driving itself: its ring wears the accent. */
    private void applyLockVisual(boolean locked) {
        if (locked) {
            setBorderColor(accentColor());
        } else {
            // Re-reads the skin from scratch rather than restoring a cached colour, the same way
            // every control's background is applied: the source of truth is the skin, not
            // whatever this class last painted.
            setBackground();
        }
    }

    /** The launcher's accent, from the theme, which is where the controls' one colour lives. */
    private int accentColor() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorAccent, value, true);
        return value.data;
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlData getProperties() {
        return mControlData;
    }

    @Override
    public void setProperties(ControlData properties, boolean changePos) {
        mControlData = (ControlJoystickData) properties;
        mControlData.isHideable = true;
        ControlInterface.super.setProperties(properties, changePos);
        postDelayed(() -> {
            setForwardLockDistance(mControlData.forwardLock ? (int) Tools.dpToPx(60) : 0);
            setFixedCenter(mControlData.absolute);
        }, 10);
    }

    @Override
    public void removeButton() {
        getControlLayoutParent().getLayout().mJoystickDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }

    @Override
    public void cloneButton() {
        ControlJoystickData data = new ControlJoystickData(mControlData);
        getControlLayoutParent().addJoystickButton(data);
    }


    @Override
    public void setBackground() {
        // Only the three setters the joystick library has always had; the knob keeps its own
        // colour rather than this reaching for an API the bundled version may not expose.
        if (ControlSkin.isPocket()) {
            setBorderWidth((int) Tools.dpToPx(ControlSkin.JOYSTICK_RING_DP * (getControlLayoutParent().getLayoutScale()/100f)));
            setBorderColor(ControlSkin.JOYSTICK_RING);
            setBackgroundColor(ControlSkin.JOYSTICK_FILL);
            return;
        }
        setBorderWidth((int) Tools.dpToPx(getProperties().strokeWidth * (getControlLayoutParent().getLayoutScale()/100f)));
        setBorderColor(getProperties().strokeColor);
        setBackgroundColor(getProperties().bgColor);
    }

    @Override
    public void sendKeyPresses(boolean isDown) {/*STUB since non swipeable*/ }

    private int getDirectionInt(int angle, int intensity) {
        if (intensity == 0) return DIRECTION_NONE;
        return (int) (((angle + 22.5) / 45) % 8);
    }

    private void sendDirectionalKeycode(int direction, boolean isDown) {
        switch (direction) {
            case DIRECTION_NORTH:
                sendInput(mDirectionForward, isDown);
                break;
            case DIRECTION_NORTH_EAST:
                sendInput(mDirectionForward, isDown);
                sendInput(mDirectionRight, isDown);
                break;
            case DIRECTION_EAST:
                sendInput(mDirectionRight, isDown);
                break;
            case DIRECTION_SOUTH_EAST:
                sendInput(mDirectionRight, isDown);
                sendInput(mDirectionBackward, isDown);
                break;
            case DIRECTION_SOUTH:
                sendInput(mDirectionBackward, isDown);
                break;
            case DIRECTION_SOUTH_WEST:
                sendInput(mDirectionBackward, isDown);
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_WEST:
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_NORTH_WEST:
                sendInput(mDirectionForward, isDown);
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_FORWARD_LOCK:
                sendInput(mDirectionForwardLock, isDown);
                break;
        }
    }

}
