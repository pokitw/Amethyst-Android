package net.kdt.pojavlaunch.customcontrols.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;

/**
 * The corner grip that resizes a control.
 *
 * <b>It used to have no weight to it at all.</b> There was no press state, no haptic, no readout
 * and no floor: the size followed the finger exactly, printed both dimensions to stdout on every
 * single move event, and would happily take a button down to zero by zero, at which point it is
 * still in the layout and can never be grabbed again. Dragging it felt like nothing was there
 * because, apart from the number quietly changing behind the finger, nothing was.
 *
 * Four things give it heft, and none of them is decoration:
 * <ul>
 *   <li><b>A floor.</b> {@link #MIN_SIZE_DP} is small enough for a deliberately tiny button and
 *       large enough that whatever you make can still be grabbed back.</li>
 *   <li><b>A step.</b> Sizes land on {@link #STEP_DP} boundaries, which is what makes a drag feel
 *       like it is moving through something rather than sliding on glass, and it means the number
 *       you end on is a round one.</li>
 *   <li><b>A tick at every step</b>, plus one when the grip is taken hold of. The resistance is
 *       felt, not just seen.</li>
 *   <li><b>The size, on screen, while it changes</b>, drawn by the layout underneath. Showing
 *       state before the action rather than after the surprise is the handbook's rule (3), and
 *       until now the only way to know what size you had made was to go and read the slider.</li>
 * </ul>
 *
 * <b>The drag is measured in raw screen coordinates against the size at grab time</b>, never as a
 * delta from where the grip currently sits. The grip is placed by {@link #mPositionListener} at
 * whatever corner the button actually ended up with, so once a floor exists the grip stops being
 * where the finger is; deriving the next size from its position would then rubber-band, needing
 * the drag pulled all the way back before the button grew again.
 */
public class ControlHandleView extends View {
    /**
     * The smallest a control may be dragged to, in dp.
     *
     * Not the handbook's 48dp touch target (4): this is a floor against loss, not a
     * recommendation about size. Somebody may genuinely want a small button, and the editor is
     * not the place to overrule them. What it must not allow is a control shrunk to nothing,
     * which stays in the layout, draws nothing and can never be selected again.
     */
    public static final float MIN_SIZE_DP = 20f;

    /** The step sizes land on. Coarse enough to tick a few times a second at a normal drag. */
    public static final float STEP_DP = 4f;

    private final Drawable mDrawable = ResourcesCompat.getDrawable(
            getResources(), R.drawable.ic_ctrl_resize_grip, getContext().getTheme());
    private ControlInterface mView;

    /** Where the finger went down, and what the control measured then. Raw screen coordinates. */
    private float mDownRawX, mDownRawY, mStartWidth, mStartHeight;
    /** The last size a tick was played for, so one is played per step crossed and not per event. */
    private float mLastTickWidth, mLastTickHeight;

    private final ViewTreeObserver.OnPreDrawListener mPositionListener =
            new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if(mView == null || !mView.getControlView().isShown()){
                hide();
                return true;
            }

            setX(mView.getControlView().getX() + mView.getControlView().getWidth());
            setY(mView.getControlView().getY() + mView.getControlView().getHeight());
            return true;
        }
    };

    public ControlHandleView(Context context) {
        super(context);
        init();
    }

    public ControlHandleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init(){
        // Larger than the 22dp it was: this is a corner grip aimed at with a thumb, and the
        // handbook's floor for anything tappable is 48dp (4). The glyph fills it.
        int size = (int) Tools.dpToPx(34);
        mDrawable.setBounds(0, 0, size, size);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(size, size);
        setLayoutParams(params);
        setBackground(mDrawable);
        setTranslationZ(10.5F);
    }

    public void setControlButton(ControlInterface controlInterface){
        if(mView != null) mView.getControlView().getViewTreeObserver()
                .removeOnPreDrawListener(mPositionListener);

        setVisibility(VISIBLE);
        mView = controlInterface;
        mView.getControlView().getViewTreeObserver().addOnPreDrawListener(mPositionListener);

        setX(controlInterface.getControlView().getX() + controlInterface.getControlView().getWidth());
        setY(controlInterface.getControlView().getY() + controlInterface.getControlView().getHeight());
    }

    /**
     * The size a drag resolves to.
     *
     * <b>Snapped first, then floored.</b> The two orders agree at the constants shipped here, since
     * {@link #MIN_SIZE_DP} happens to be a whole number of {@link #STEP_DP}; they stop agreeing the
     * moment either one is changed to something off the other's grid, and then flooring first lets
     * the step round the result back under the minimum. Written in the order that is right for both.
     *
     * Static and free of any view so {@code scripts/resizesim} can drive it, since there is no
     * device in CI to drag anything on. That harness sweeps off-grid floors for exactly this
     * reason: with the shipped pair alone, reversing these two lines is invisible.
     *
     * @param rawPx  the size the finger is asking for, in pixels
     * @param minPx  the floor, in pixels
     * @param stepPx the step to land on, in pixels; zero or less means no stepping
     */
    public static float resolveSize(float rawPx, float minPx, float stepPx) {
        float resolved = rawPx;
        if (stepPx > 0) resolved = Math.round(resolved / stepPx) * stepPx;
        return Math.max(minPx, resolved);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mView == null) return false;
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                // Against the size at grab time and the raw finger position, so a floored size
                // cannot drag the reference point along with it. See the class comment.
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                mStartWidth = mView.getControlView().getWidth();
                mStartHeight = mView.getControlView().getHeight();
                mLastTickWidth = mStartWidth;
                mLastTickHeight = mStartHeight;
                setPressed(true);
                animate().scaleX(1.18f).scaleY(1.18f).setDuration(140).start();
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                readout(mStartWidth, mStartHeight, true);
                break;
            case MotionEvent.ACTION_MOVE: {
                float minPx = Tools.dpToPx(MIN_SIZE_DP);
                float stepPx = Tools.dpToPx(STEP_DP);
                float width = resolveSize(
                        mStartWidth + event.getRawX() - mDownRawX, minPx, stepPx);
                float height = resolveSize(
                        mStartHeight + event.getRawY() - mDownRawY, minPx, stepPx);

                // One tick per step actually crossed. Ticking per event would buzz continuously
                // on a fast drag and say nothing about how far it had got.
                if (width != mLastTickWidth || height != mLastTickHeight) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    mLastTickWidth = width;
                    mLastTickHeight = height;
                }

                mView.getProperties().setWidth(width);
                mView.getProperties().setHeight(height);
                mView.regenerateDynamicCoordinates();
                readout(width, height, true);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                readout(0, 0, false);
                break;
        }

        return true;
    }

    /** Ask the layout underneath to show the size while it is changing, since it owns the canvas. */
    private void readout(float widthPx, float heightPx, boolean visible) {
        ViewGroup parent = (ViewGroup) getParent();
        if (parent instanceof ControlLayout) {
            ((ControlLayout) parent).showResizeReadout(widthPx, heightPx, visible);
        }
    }

    public void hide(){
        if(mView != null)
            mView.getControlView().getViewTreeObserver().removeOnPreDrawListener(mPositionListener);
        readout(0, 0, false);
        setVisibility(GONE);
    }
}
