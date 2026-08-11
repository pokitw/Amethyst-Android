package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.sendMouseButton;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlGlyphs;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.ControlSkin;
import net.kdt.pojavlaunch.customcontrols.textures.ControlTexture;
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextureDrawable;
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextures;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;
import java.util.Locale;

@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
public class ControlButton extends TextView implements ControlInterface {
    /** How much of the shorter side the action icon takes. Sized like a Pocket Edition button. */
    private static final float GLYPH_SIZE_RATIO = 0.46f;
    /** Near-black rather than black: a pure black label on stone reads as a hole. */
    private static final int DARK_CONTENT = 0xFF1A1A1A;

    /** The label size a button keeps when its name already fits across it. */
    private static final float BASE_TEXT_SP = 14f;
    /** Below this a label stops being readable, so it wraps instead of shrinking further. */
    private static final float MIN_TEXT_SP = 8f;

    /**
     * How long a chat button has to be held before it starts dictating.
     *
     * Well clear of a tap, which is nearer 100ms, and short enough that the hold does not feel
     * like waiting. Deliberately not PREF_LONGPRESS_TRIGGER: that one belongs to the gesture that
     * mines a block, and shortening it to break faster should not make chat harder to open.
     */
    private static final int HOLD_TO_DICTATE_MS = 400;

    /**
     * The keys that open a text field in vanilla Minecraft: chat, and the command prompt.
     *
     * Hardcoded because nothing on the launcher side can read the player's keybinds — the game
     * owns options.txt and there is no route to it from here. So the shortcut is offered for the
     * keys chat is bound to out of the box, is off until it is asked for, and says in Settings
     * exactly which keys it applies to rather than appearing to be general.
     */
    private static final int[] CHAT_KEYS = {
            LwjglGlfwKeycode.GLFW_KEY_T, LwjglGlfwKeycode.GLFW_KEY_SLASH
    };

    /**
     * How strongly the accent washes a button that is repeating.
     *
     * Stronger than the press flash (60) and than the toggle wash (128), because it is saying
     * something louder than either: this button is firing on its own and will keep doing it until
     * the finger comes off.
     */
    private static final int REPEAT_ALPHA = 150;

    private final Paint mRectPaint = new Paint();
    /** The accent wash a repeating button wears. Separate from {@link #mRectPaint}, which is the
     * press flash on an ordinary button and the latch tint on a toggle. */
    private final Paint mRepeatPaint = new Paint();
    protected ControlData mProperties;
    private final ControlLayout mControlLayout;

    /* Cache value from the ControlData radius for drawing purposes */
    private float mComputedRadius;

    /* The action icon this button carries instead of its name, or null when it keeps the name. */
    private Drawable mGlyph;

    protected boolean mIsToggled = false;
    protected boolean mIsPointerOutOfBounds = false;

    /** Whether the hold on this button has turned into a dictation that still has to be ended. */
    private boolean mDictating = false;
    private final Runnable mDictateRunnable = new Runnable() {
        @Override
        public void run() {
            mDictating = true;
            // The chat key must not stay down for the length of a sentence. It has already done
            // its job — the text field is open — so it is given back here rather than on release.
            sendKeyPresses(false);
            mControlLayout.notifyVoiceShortcut(true);
        }
    };

    public ControlButton(ControlLayout layout, ControlData properties) {
        super(layout.getContext());
        mControlLayout = layout;
        setGravity(Gravity.CENTER);
        setAllCaps(LauncherPreferences.PREF_BUTTON_ALL_CAPS);
        setTextColor(Color.WHITE);
        setPadding(4, 4, 4, 4);
        setOutlineProvider(null); // Disable shadow casting, removing one drawing pass

        //setOnLongClickListener(this);

        //When a button is created, the width/height has yet to be processed to fit the scaling.
        setProperties(preProcessProperties(properties, layout));

        injectBehaviors();
    }

    @Override
    public View getControlView() {return this;}

    public ControlData getProperties() {
        return mProperties;
    }

    public void setProperties(ControlData properties, boolean changePos) {
        mProperties = properties;
        ControlInterface.super.setProperties(properties, changePos);
        mComputedRadius = ControlInterface.super.computeCornerRadius(ControlSkin.cornerPercent(mProperties));

        if (mProperties.isToggle) {
            //For the toggle layer
            mRectPaint.setColor(accentColor());
            mRectPaint.setAlpha(128);
        } else {
            mRectPaint.setColor(Color.WHITE);
            mRectPaint.setAlpha(ControlSkin.isPocket() ? ControlSkin.PRESS_ALPHA : 60);
        }
        // Always the accent, whatever the press wash above turned out to be. A repeat and a
        // toggle are mutually exclusive, so the two washes can never be on the same button at
        // the same moment and there is nothing to tell apart.
        mRepeatPaint.setColor(accentColor());
        mRepeatPaint.setAlpha(REPEAT_ALPHA);

        mGlyph = resolveGlyph(properties);
        // A button showing an icon shows nothing else: two things fighting for the same 50dp is
        // how the old layouts ended up with "Third\nPerson" wrapped over two lines.
        setText(mGlyph == null ? properties.name : "");
        applyContentTone();
        fitTextSize(properties);
    }

    /** The launcher's accent, from the theme, which is where the controls' one colour lives. */
    private int accentColor() {
        final TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.colorAccent, value, true);
        return value.data;
    }

    /**
     * Make the label and the icon readable against whatever the button's face turned out to be.
     *
     * Both are white, and white is right over the translucent dark fills the flat skins draw. A
     * texture pack removes that guarantee: a sandstone or a light-stone face erases every label
     * and every glyph, and a glyph that cannot be read at a glance mid-fight is the one failure
     * these icons exist to avoid. The pack says which it is; light — today's white — is the
     * default, so a pack matching the current look needs to say nothing.
     */
    private void applyContentTone() {
        ControlTexture texture = ControlTextures.current();
        boolean dark = texture != null && texture.darkLabel;
        setTextColor(dark ? DARK_CONTENT : Color.WHITE);
        if (mGlyph != null) {
            // Safe because resolveGlyph already mutated it: an un-mutated drawable shares its
            // constant state, and the tint would land on every button on screen.
            if (dark) mGlyph.setColorFilter(DARK_CONTENT, PorterDuff.Mode.SRC_IN);
            else mGlyph.clearColorFilter();
        }
    }

    /**
     * Shrink a label until its longest word fits across the button.
     *
     * The text size used to be a flat 14sp whatever the button said, which was survivable only
     * while every recognisable action carried an icon instead. With icons off — and they are off
     * unless asked for — a 46dp button labelled "Keyboard" had to break the word across three
     * lines and clipped the outer two. Wrapping between words is fine and expected; breaking
     * inside one is the thing that reads as broken, so the longest word is what decides.
     *
     * Only the size changes. Nothing here touches the button's bounds, its background or its
     * touch handling, and a button whose label already fits keeps the full 14sp.
     */
    private void fitTextSize(ControlData properties) {
        setTextSize(BASE_TEXT_SP);
        if (mGlyph != null || properties.name == null || properties.name.isEmpty()) return;

        // Already in pixels, and already scaled by the user's button size, so this follows the
        // slider without needing to know about it.
        float available = properties.getWidth() - getPaddingLeft() - getPaddingRight();
        if (available <= 0) return;

        String longest = "";
        for (String word : properties.name.split("\\s+")) {
            if (word.length() > longest.length()) longest = word;
        }
        if (longest.isEmpty()) return;
        // Measured as it will be drawn: capitals are wider, and they are on by default.
        if (LauncherPreferences.PREF_BUTTON_ALL_CAPS) longest = longest.toUpperCase(Locale.ROOT);

        float needed = getPaint().measureText(longest);
        if (needed <= available) return;
        // Floored rather than shrunk without limit: past this a label is unreadable anyway, and a
        // word that still does not fit is better wrapped than turned into a grey smudge.
        setTextSize(Math.max(MIN_TEXT_SP, BASE_TEXT_SP * (available / needed)));
    }

    private Drawable resolveGlyph(ControlData properties) {
        if (!ControlSkin.isGlyphs()) return null;
        int glyphRes = ControlGlyphs.glyphFor(properties);
        if (glyphRes == 0) return null;
        Drawable drawable = ContextCompat.getDrawable(getContext(), glyphRes);
        // Mutated because the alpha below lives in the shared constant state otherwise, and every
        // button on screen would take the last one set.
        return drawable == null ? null : drawable.mutate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mGlyph != null) {
            int size = (int) (Math.min(getWidth(), getHeight()) * GLYPH_SIZE_RATIO);
            int left = (getWidth() - size) / 2;
            int top = (getHeight() - size) / 2;
            mGlyph.setBounds(left, top, left + size, top + size);
            mGlyph.draw(canvas);
        }
        if (!(mIsToggled || mRepeating || (!mProperties.isToggle && isActivated()))) return;

        // A repeat outranks the press flash under it. The flash is already steady rather than
        // blinking — sendRepeatEdge deliberately leaves the activated state alone, because a
        // press state driven at twenty a second is a strobe — so without this the button would
        // wear the ordinary held look and say nothing about what it is doing.
        Paint paint = mRepeating ? mRepeatPaint : mRectPaint;

        // Over a texture the highlight has to wear the artwork's silhouette, not a rounded
        // rectangle: mComputedRadius is the layout's own corner percentage, which is exactly the
        // number the texture stopped drawing, so the rectangle would bleed past a rounded face or
        // cut across a square one. The drawable redraws itself in one colour instead.
        //
        // Only for the latch and the repeat. A press is already the drawable's own business — it
        // swaps to the pack's pressed face, or tints itself — and drawing this on top as well
        // would light the button twice.
        Drawable background = getBackground();
        if (background instanceof ControlTextureDrawable) {
            if (mIsToggled || mRepeating) {
                ((ControlTextureDrawable) background)
                        .drawOverlay(canvas, paint.getColor(), paint.getAlpha());
            }
            return;
        }
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), mComputedRadius, mComputedRadius, paint);
    }


    /** Add another instance of the ControlButton to the parent layout */
    public void cloneButton(){
        ControlData cloneData = new ControlData(getProperties());
        cloneData.dynamicX = "0.5 * ${screen_width}";
        cloneData.dynamicY = "0.5 * ${screen_height}";
        ((ControlLayout) getParent()).addControlButton(cloneData);
    }

    /** Remove any trace of this button from the layout */
    public void removeButton() {
        getControlLayoutParent().getLayout().mControlDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Read before anything else touches the event. ControlLayout.onTouch, which the
        // out-of-bounds path below hands it to, calls offsetLocation on it.
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                //Send the event to be taken as a mouse action
                if(getProperties().passThruEnabled && CallbackBridge.isGrabbing()){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }

                // Before the bounds check, and deliberately not inside it: the slide that arms a
                // repeat is often longer than the button is wide, so on anything but a large
                // button the finger has left by the time the threshold is passed.
                maybeArmRepeat(x, y);

                //If out of bounds
                if(event.getX() < getControlView().getLeft() || event.getX() > getControlView().getRight() ||
                        event.getY() < getControlView().getTop()  || event.getY() > getControlView().getBottom()){
                    if(getProperties().isSwipeable && !mIsPointerOutOfBounds){
                        //Remove keys
                        if(!triggerToggle()) {
                            sendKeyPresses(false);
                        }
                    }
                    // A finger that has slid off is no longer holding this button, whatever it
                    // goes on to do, so the pending dictation goes with it.
                    endDictation();
                    mIsPointerOutOfBounds = true;
                    getControlLayoutParent().onTouch(this, event);
                    break;
                }

                //Else if we now are in bounds
                if(mIsPointerOutOfBounds) {
                    getControlLayoutParent().onTouch(this, event);
                    //RE-press the button
                    if(getProperties().isSwipeable && !getProperties().isToggle){
                        sendKeyPresses(true);
                    }
                }
                mIsPointerOutOfBounds = false;
                break;

            case MotionEvent.ACTION_DOWN: // 0
            case MotionEvent.ACTION_POINTER_DOWN: // 5
                mDownX = x;
                mDownY = y;
                if(!getProperties().isToggle){
                    sendKeyPresses(true);
                }
                scheduleDictation();
                break;

            case MotionEvent.ACTION_UP: // 1
            case MotionEvent.ACTION_CANCEL: // 3
            case MotionEvent.ACTION_POINTER_UP: // 6
                if(getProperties().passThruEnabled){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }
                if(mIsPointerOutOfBounds) getControlLayoutParent().onTouch(this, event);
                mIsPointerOutOfBounds = false;

                // Before the release below, so the cycle cannot post another edge behind it.
                // Both run on the main thread, so removeCallbacks here is the whole guarantee.
                stopRepeat();
                endDictation();
                if(!triggerToggle()) {
                    sendKeyPresses(false);
                }
                break;

            default:
                return false;
        }

        return super.onTouchEvent(event);
    }



    /**
     * Start the clock on hold-to-dictate, for a button that is bound to a chat key.
     *
     * Deliberately does not delay the key itself: holding chat still opens chat the instant it is
     * pressed, and only the dictation waits. A shortcut that made the ordinary press feel slower
     * would cost every player something to give some of them a feature.
     */
    private void scheduleDictation() {
        if(!LauncherPreferences.PREF_VOICE_HOLD_CHAT) return;
        if(mProperties.isToggle || mProperties.keycodes.length != 1) return;
        boolean isChatKey = false;
        for(int chatKey : CHAT_KEYS) {
            if(mProperties.keycodes[0] == chatKey) isChatKey = true;
        }
        if(!isChatKey) return;
        mDictating = false;
        postDelayed(mDictateRunnable, HOLD_TO_DICTATE_MS);
    }

    /** Drop a pending dictation, and close one that has already started. */
    private void endDictation() {
        removeCallbacks(mDictateRunnable);
        if(!mDictating) return;
        mDictating = false;
        mControlLayout.notifyVoiceShortcut(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        // A button can be removed mid-press in the editor, and a dictation left running would
        // have nothing left to end it.
        endDictation();
        // Same rule for a sequence: a posted step outlives the view it was posted on, so it is
        // cancelled here, and a key sent down by a step that already ran must be sent up or it
        // is held in the game forever. The keyboard host learned this first with latched keys.
        removeCallbacks(mSequenceAdvance);
        if(mSequenceHeldKey != GLFW_KEY_UNKNOWN){
            sendSingleKey(mSequenceHeldKey, false);
            mSequenceHeldKey = GLFW_KEY_UNKNOWN;
        }
        mSequenceRunning = false;
        // And a repeat, which is the same hazard: this is the one path that cancels it without an
        // ACTION_UP behind it, so whichever edge the cycle was left on has to be given back here
        // or the key stays down in the game with nothing remaining that could release it.
        if(mRepeating && mRepeatDown) sendRepeatEdge(false);
        stopRepeat();
        super.onDetachedFromWindow();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean triggerToggle(){
        //returns true a the toggle system is triggered
        if(mProperties.isToggle){
            mIsToggled = !mIsToggled;
            invalidate();
            sendKeyPresses(mIsToggled);
            return true;
        }
        return false;
    }

    public void sendKeyPresses(boolean isDown){
        setActivated(isDown);
        if(mProperties.sequence){
            // One press runs the whole sequence; the release edge has nothing to add, because
            // every step sends its own press and release. Ignoring the up edge is also what
            // stops a toggle-style half-run: the finger lifting mid-sequence must not cut the
            // pearl throw off between the slot switch and the click.
            if(isDown) startSequence();
            return;
        }
        for(int keycode : mProperties.keycodes){
            sendSingleKey(keycode, isDown);
        }
    }

    /** One key of this button, pressed or released, special or not. */
    private void sendSingleKey(int keycode, boolean isDown){
        if(keycode >= GLFW_KEY_UNKNOWN){
            sendKeyPress(keycode, EfficientAndroidLWJGLKeycode.getLwjglChar(keycode), CallbackBridge.getCurrentMods(), isDown);
            CallbackBridge.setModifiers(keycode, isDown);
        }else{
            Log.i("punjabilauncher", "sendSpecialKey("+keycode+","+isDown+")");
            sendSpecialKey(keycode, isDown);
        }
    }

    /* A sequence in flight. GLFW_KEY_UNKNOWN (0 in this codebase) doubles as "nothing held",
     * exactly as it doubles as "empty slot" in the keycodes array. */
    private boolean mSequenceRunning;
    private int mSequenceHeldKey = GLFW_KEY_UNKNOWN;
    private int mSequenceIndex;
    private final Runnable mSequenceAdvance = this::advanceSequence;

    /**
     * Fire the bound keys one after another, a game tick or more apart.
     *
     * The whole reason this exists is that Minecraft samples the hotbar once per tick: two slot
     * switches inside one tick collapse into one, so a pearl-then-wind-charge move needs each
     * step to land in its own tick. Every step is a full press and release, half the gap apart,
     * so press-to-press spacing is exactly the gap.
     *
     * The whole run is <b>one</b> self-advancing runnable: each call sends one edge and books
     * the next. That shape is what makes {@link #onDetachedFromWindow()} able to cancel it with
     * a single removeCallbacks, where a queue of anonymous lambdas could not be taken back.
     *
     * A press while a sequence is running is ignored rather than queued: queued repeats are how
     * a nervous double tap becomes four pearls.
     */
    private void startSequence(){
        if(mSequenceRunning) return;
        mSequenceRunning = true;
        mSequenceIndex = 0;
        advanceSequence();
    }

    private void advanceSequence(){
        final int gap = Math.max(50, mProperties.sequenceGap == 0 ? 50 : mProperties.sequenceGap);
        final int half = Math.max(25, gap / 2);
        if(mSequenceHeldKey != GLFW_KEY_UNKNOWN){
            // The release edge of the current step.
            sendSingleKey(mSequenceHeldKey, false);
            mSequenceHeldKey = GLFW_KEY_UNKNOWN;
            if(mSequenceIndex < mProperties.keycodes.length){
                postDelayed(mSequenceAdvance, half);
            }else{
                mSequenceRunning = false;
            }
            return;
        }
        // The press edge of the next bound key, skipping the array's empty slots.
        while(mSequenceIndex < mProperties.keycodes.length
                && mProperties.keycodes[mSequenceIndex] == GLFW_KEY_UNKNOWN){
            mSequenceIndex++;
        }
        if(mSequenceIndex >= mProperties.keycodes.length){
            mSequenceRunning = false;
            return;
        }
        int keycode = mProperties.keycodes[mSequenceIndex++];
        sendSingleKey(keycode, true);
        mSequenceHeldKey = keycode;
        postDelayed(mSequenceAdvance, half);
    }

    /* ------------------------------------------------------------------ slide to repeat.
     *
     * Where the press started, so the slide can be measured from it. In this view's coordinates,
     * which is what the bounds check above already uses; the button does not move under the
     * finger in a game, so a view-relative delta and a screen-relative one are the same number.
     */
    private float mDownX, mDownY;
    /** Whether this press has turned into a repeat, and which edge the cycle is currently on. */
    private boolean mRepeating, mRepeatDown;
    private final Runnable mRepeatAdvance = this::advanceRepeat;

    /**
     * Whether this button is one a slide may turn into a repeat.
     *
     * The four exclusions are enforced in the editor as well, where turning this on visibly turns
     * them off. They are repeated here because a layout file is a file: it can be hand-edited, and
     * it can be shared by someone whose launcher wrote it before a rule existed. A button carrying
     * a contradiction stays an ordinary button, which is the failure that cannot surprise anybody.
     *
     * A <b>toggle</b> holds its keys until the next tap, so there is no press for a slide to
     * modify. A <b>sequence</b> is already a clock running on one press, and two clocks driving
     * the same keys is a combination with no meaning a switch label could predict. <b>Swipe</b>
     * and <b>pass-through</b> both already spend the slide: one releases the keys when the finger
     * leaves, the other hands every move event to the game as mouse-look, so on either of them
     * every ordinary use of the button would arm a repeat by accident.
     */
    private boolean canRepeat(){
        ControlData p = getProperties();
        return p.slideRepeat && !p.isToggle && !p.sequence && !p.isSwipeable && !p.passThruEnabled;
    }

    /** Start repeating if the finger has slid far enough since it went down. */
    private void maybeArmRepeat(float x, float y){
        if(mRepeating || !canRepeat()) return;
        float threshold = Tools.dpToPx(ControlData.slideDistanceDp(getProperties().slideDistance));
        if(!ControlData.pastSlideThreshold(x - mDownX, y - mDownY, threshold)) return;

        mRepeating = true;
        // The key went down when the finger did and is still down, so the cycle picks up from
        // there: the first thing owed is a release, half a gap from now, and from then on the
        // spacing is regular.
        mRepeatDown = true;
        // A hold that has become a slide is no longer a hold, so a dictation waiting behind it
        // must not still be counting down.
        endDictation();
        // The one confirmation that it armed. Everything else about this gesture is invisible
        // until the button starts firing, and a thumb mid-clutch is not looking at the button.
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
        postDelayed(mRepeatAdvance, ControlData.repeatHalfGapMs(getProperties().repeatGap));
    }

    /**
     * One edge of the repeat, and the booking for the next.
     *
     * The same shape as {@link #advanceSequence()} and for the same reason: the whole run is one
     * self-advancing runnable, so {@link #stopRepeat()} can take it back with a single
     * removeCallbacks where a queue of anonymous lambdas could not.
     */
    private void advanceRepeat(){
        if(!mRepeating) return;
        mRepeatDown = !mRepeatDown;
        sendRepeatEdge(mRepeatDown);
        postDelayed(mRepeatAdvance, ControlData.repeatHalfGapMs(getProperties().repeatGap));
    }

    /**
     * Stop the cycle. Releasing the key is the caller's job, and both callers do it.
     *
     * Kept out of here on purpose: ACTION_UP releases everything a moment later whatever the
     * cycle was doing, and the detach path has to release the exact edge it was left on. A
     * release in the middle would be right for neither and invisible in both.
     */
    private void stopRepeat(){
        removeCallbacks(mRepeatAdvance);
        if(!mRepeating) return;
        mRepeating = false;
        mRepeatDown = false;
        invalidate();
    }

    /**
     * The bound keys, pressed or released, without touching the activated state.
     *
     * That omission is the point. {@link #sendKeyPresses(boolean)} sets it, and driven at twenty
     * edges a second it would make the button strobe; the steady accent wash in
     * {@link #onDraw(Canvas)} says what is happening instead, once.
     */
    private void sendRepeatEdge(boolean isDown){
        for(int keycode : mProperties.keycodes){
            sendSingleKey(keycode, isDown);
        }
    }

    private void sendSpecialKey(int keycode, boolean isDown){
        switch (keycode) {
            case ControlData.SPECIALBTN_KEYBOARD:
                if(isDown) MainActivity.switchKeyboardState();
                break;

            case ControlData.SPECIALBTN_TOGGLECTRL:
                if(isDown)getControlLayoutParent().toggleControlVisible();
                break;

            case ControlData.SPECIALBTN_VIRTUALMOUSE:
                if(isDown) MainActivity.toggleMouse(getContext());
                break;

            case ControlData.SPECIALBTN_MOUSEPRI:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEMID:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSESEC:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, isDown);
                break;

            case ControlData.SPECIALBTN_SCROLLDOWN:
                if (!isDown) CallbackBridge.sendScroll(0, 1f);
                break;

            case ControlData.SPECIALBTN_SCROLLUP:
                if (!isDown) CallbackBridge.sendScroll(0, -1f);
                break;

            case ControlData.SPECIALBTN_MENU:
                mControlLayout.notifyAppMenu();
                break;

            case ControlData.SPECIALBTN_MOUSEBCK:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEFWD:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5, isDown);
                break;

            // Guarded on isDown. sendSpecialKey runs on both edges of a press, so an unguarded
            // one-shot fires twice — SPECIALBTN_MENU above still does.
            case ControlData.SPECIALBTN_GAMEKEYBOARD:
                if (isDown) mControlLayout.notifyGameKeyboard();
                break;

            // The one special that wants both edges: a tap toggles listening and a hold listens
            // only while held, and the difference between those is how long the finger stayed
            // down — which only the receiver is in a position to measure.
            case ControlData.SPECIALBTN_VOICE:
                mControlLayout.notifyVoice(isDown);
                break;

            // On the press, not the release: the frame you meant is the one that was on screen
            // when your thumb landed, and a capture armed on the way up is a frame or two late.
            case ControlData.SPECIALBTN_SCREENSHOT:
                if (isDown) mControlLayout.notifyScreenshot();
                break;

            default:
                Toast.makeText(getContext(), R.string.error_key_unsupported, Toast.LENGTH_LONG).show();
                break;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
