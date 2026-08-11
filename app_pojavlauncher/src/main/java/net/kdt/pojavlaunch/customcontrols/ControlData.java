package net.kdt.pojavlaunch.customcontrols;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;

import android.util.ArrayMap;

import androidx.annotation.Keep;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JSONUtils;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import org.lwjgl.glfw.CallbackBridge;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Keep
public class ControlData {

    public static final int SPECIALBTN_KEYBOARD = -1;
    public static final int SPECIALBTN_TOGGLECTRL = -2;
    public static final int SPECIALBTN_MOUSEPRI = -3;
    public static final int SPECIALBTN_MOUSESEC = -4;
    public static final int SPECIALBTN_VIRTUALMOUSE = -5;
    public static final int SPECIALBTN_MOUSEMID = -6;
    public static final int SPECIALBTN_SCROLLUP = -7;
    public static final int SPECIALBTN_SCROLLDOWN = -8;
    public static final int SPECIALBTN_MENU = -9;
    public static final int SPECIALBTN_MOUSEBCK = -10;
    public static final int SPECIALBTN_MOUSEFWD = -11;
    /**
     * The launcher's own on-screen keyboard, straight from a button.
     *
     * Distinct from {@link #SPECIALBTN_KEYBOARD}, which raises the system IME and which every
     * layout ever shared already depends on.
     *
     * New values are only ever APPENDED, and the reason is the on-disk format: the raw negative
     * integer is what gets written into saved layout JSON and what {@code LayoutConverter} copies
     * through unmigrated, so inserting one in the middle would silently re-point every button
     * anyone has ever saved. Keeping the array in {@code -(index + 1)} order costs nothing and is
     * what makes it auditable.
     */
    public static final int SPECIALBTN_GAMEKEYBOARD = -12;
    /** Speak, and the words are typed into whatever text field the game has open. */
    public static final int SPECIALBTN_VOICE = -13;
    /** A picture of the frame the game is presenting, with none of the controls in it. */
    public static final int SPECIALBTN_SCREENSHOT = -14;

    private static ControlData[] SPECIAL_BUTTONS;
    private static List<String> SPECIAL_BUTTON_NAME_ARRAY;
    private static WeakReference<ExpressionBuilder> builder = new WeakReference<>(null);
    private static WeakReference<ArrayMap<String, String>> conversionMap = new WeakReference<>(null);

    static {
        buildExpressionBuilder();
        buildConversionMap();
    }

    // Internal usage only
    public transient boolean isHideable;
    /**
     * Both fields below are dynamic position data, auto updates
     * X and Y position, unlike the original one which uses fixed
     * position, so it does not provide auto-location when a control
     * is made on a small device, then import the control to a
     * bigger device or vice versa.
     */
    public String dynamicX, dynamicY;
    public boolean isToggle, passThruEnabled;
    public String name;
    public int[] keycodes;      //Should store up to 4 keys
    public float opacity;       //Alpha value from 0 to 1;
    public int bgColor;
    public int strokeColor;
    public float strokeWidth;     // Dp instead of % now
    public float cornerRadius;  //0-100%
    public boolean isSwipeable;
    public boolean displayInGame;
    public boolean displayInMenu;
    /**
     * Fire the bound keys one after another instead of together.
     *
     * The community's ask, in their own words: swap to the pearl slot, use it, swap to the wind
     * charge, use it, on one button. The game reads the hotbar once a tick, so two slot switches
     * in one tick are one switch; the gap below is what makes each step land. Absent from old
     * layouts, so Gson leaves it false and every existing button behaves exactly as before.
     */
    public boolean sequence;
    /** Milliseconds between sequence steps. 0 means the default; one game tick (50) is the floor. */
    public int sequenceGap;
    /**
     * Tap it as a button; hold it and slide, and it keeps firing until the finger comes off.
     *
     * One control doing the two things a place button is asked to do. Placing a single block is a
     * tap, and there is no way to make that faster or better. Clutching is the same key sent as
     * fast as the game will take it, and every existing way to get that is a different button:
     * a toggle that has to be turned off again, a second control somewhere else on the screen, or
     * a finger. So the second job hangs off a gesture on the first button rather than off a
     * control of its own, which is the only arrangement where the thumb never has to move.
     *
     * The gesture is a slide because a slide is the one thing a button is otherwise deaf to. A
     * long press is taken (it is how the editor is opened, and how dictation is held), a double
     * tap costs every ordinary tap a delay before it can be sure, and both are far too easy to do
     * by accident with a thumb that is already pressing something.
     *
     * Absent from old layouts, so Gson leaves it false and every existing button behaves exactly
     * as before.
     */
    public boolean slideRepeat;
    /** How far the finger travels before a repeat arms, in dp. 0 means {@link #DEFAULT_SLIDE_DP}. */
    public float slideDistance;
    /** Milliseconds between repeated presses. 0 means the default; one game tick is the floor. */
    public int repeatGap;
    private float width;         //Dp instead of Px now
    private float height;        //Dp instead of Px now

    /**
     * How far a finger slides before a repeat arms, when the button does not say.
     *
     * Far enough to be a movement somebody meant rather than a thumb settling on the glass, and
     * short enough to be reachable from the middle of a default 50dp button without the finger
     * having to leave it, which is the size most of the shipped controls are.
     */
    public static final float DEFAULT_SLIDE_DP = 20f;

    /**
     * The floor, and the default, for the gap between repeated presses.
     *
     * One game tick. Minecraft samples input once a tick, so presses closer together than this are
     * presses the game cannot see, and offering a faster setting would be offering a number that
     * does nothing. The same reasoning, and the same floor, as {@link #sequenceGap}.
     */
    public static final int REPEAT_GAP_FLOOR_MS = 50;

    /** The slide a button asks for, or the default when it asks for nothing sensible. */
    public static float slideDistanceDp(float configured) {
        return configured > 0 ? configured : DEFAULT_SLIDE_DP;
    }

    /** The gap a repeat runs at: what the button asks for, never under one game tick. */
    public static int repeatGapMs(int configured) {
        return Math.max(REPEAT_GAP_FLOOR_MS, configured);
    }

    /**
     * How long until the next edge of a repeat.
     *
     * Half the gap, so a press and a release each take half and press-to-press spacing is exactly
     * the gap. It needs no floor of its own: {@link #repeatGapMs} cannot return under one tick, so
     * half of it cannot come out under half a tick.
     */
    public static int repeatHalfGapMs(int configured) {
        return repeatGapMs(configured) / 2;
    }

    /**
     * Whether a drag has travelled far enough to arm a repeat.
     *
     * <b>Radially, and squared.</b> The obvious spelling of this compares the two axes separately,
     * which quietly makes the gesture easier along a diagonal than along either axis and is a
     * different distance depending on which way the player happened to slide. Squaring both sides
     * keeps it a real distance without a square root on the touch path, and taking dx and dy as
     * they come means sliding left or up arms it exactly as sliding right or down does.
     *
     * Strictly greater, so a threshold of zero cannot arm on a move event that has not moved.
     */
    public static boolean pastSlideThreshold(float dx, float dy, float thresholdPx) {
        return dx * dx + dy * dy > thresholdPx * thresholdPx;
    }

    public ControlData() {
        this("button");
    }

    public ControlData(String name) {
        this(name, new int[]{});
    }

    public ControlData(String name, int[] keycodes) {
        this(name, keycodes, Tools.currentDisplayMetrics.widthPixels / 2f, Tools.currentDisplayMetrics.heightPixels / 2f);
    }

    public ControlData(String name, int[] keycodes, float x, float y) {
        this(name, keycodes, x, y, 50, 50);
    }

    public ControlData(android.content.Context ctx, int resId, int[] keycodes, float x, float y, boolean isSquare) {
        this(ctx.getResources().getString(resId), keycodes, x, y, isSquare);
    }

    public ControlData(String name, int[] keycodes, float x, float y, boolean isSquare) {
        this(name, keycodes, x, y, isSquare ? 50 : 80, isSquare ? 50 : 30);
    }

    public ControlData(String name, int[] keycodes, float x, float y, float width, float height) {
        this(name, keycodes, Float.toString(x), Float.toString(y), width, height, false);
    }

    public ControlData(String name, int[] keycodes, String dynamicX, String dynamicY) {
        this(name, keycodes, dynamicX, dynamicY, 50, 50, false);
    }

    public ControlData(android.content.Context ctx, int resId, int[] keycodes, String dynamicX, String dynamicY, boolean isSquare) {
        this(ctx.getResources().getString(resId), keycodes, dynamicX, dynamicY, isSquare);
    }

    public ControlData(String name, int[] keycodes, String dynamicX, String dynamicY, boolean isSquare) {
        this(name, keycodes, dynamicX, dynamicY, isSquare ? 50 : 80, isSquare ? 50 : 30, false);
    }

    public ControlData(String name, int[] keycodes, String dynamicX, String dynamicY, float width, float height, boolean isToggle) {
        this(name, keycodes, dynamicX, dynamicY, width, height, isToggle, 1, 0x4D000000, 0xFFFFFFFF, 0, 0, true, true, false, false);
    }

    public ControlData(String name, int[] keycodes, String dynamicX, String dynamicY, float width, float height, boolean isToggle, float opacity, int bgColor, int strokeColor, float strokeWidth, float cornerRadius, boolean displayInGame, boolean displayInMenu, boolean isSwipable, boolean mousePassthrough) {
        this.name = name;
        this.keycodes = inflateKeycodeArray(keycodes);
        this.dynamicX = dynamicX;
        this.dynamicY = dynamicY;
        this.width = width;
        this.height = height;
        this.isToggle = isToggle;
        this.opacity = opacity;
        this.bgColor = bgColor;
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
        this.cornerRadius = cornerRadius;
        this.displayInGame = displayInGame;
        this.displayInMenu = displayInMenu;
        this.isSwipeable = isSwipable;
        this.passThruEnabled = mousePassthrough;
    }

    //Deep copy constructor
    public ControlData(ControlData controlData) {
        this(
                controlData.name,
                controlData.keycodes,
                controlData.dynamicX,
                controlData.dynamicY,
                controlData.width,
                controlData.height,
                controlData.isToggle,
                controlData.opacity,
                controlData.bgColor,
                controlData.strokeColor,
                controlData.strokeWidth,
                controlData.cornerRadius,
                controlData.displayInGame,
                controlData.displayInMenu,
                controlData.isSwipeable,
                controlData.passThruEnabled
        );
        // Not in the sixteen-argument constructor, which upstream code also calls: additive
        // fields ride along here so a duplicated button keeps its sequence behaviour.
        this.sequence = controlData.sequence;
        this.sequenceGap = controlData.sequenceGap;
        this.slideRepeat = controlData.slideRepeat;
        this.slideDistance = controlData.slideDistance;
        this.repeatGap = controlData.repeatGap;
    }

    public static ControlData[] getSpecialButtons() {
        if (SPECIAL_BUTTONS == null) {
            SPECIAL_BUTTONS = new ControlData[]{
                    new ControlData("Keyboard", new int[]{SPECIALBTN_KEYBOARD}, "${margin} * 3 + ${width} * 2", "${margin}", false),
                    new ControlData("GUI", new int[]{SPECIALBTN_TOGGLECTRL}, "${margin}", "${bottom} - ${margin}"),
                    new ControlData("PRI", new int[]{SPECIALBTN_MOUSEPRI}, "${margin}", "${screen_height} - ${margin} * 3 - ${height} * 3"),
                    new ControlData("SEC", new int[]{SPECIALBTN_MOUSESEC}, "${margin} * 3 + ${width} * 2", "${screen_height} - ${margin} * 3 - ${height} * 3"),
                    new ControlData("Mouse", new int[]{SPECIALBTN_VIRTUALMOUSE}, "${right}", "${margin}", false),

                    new ControlData("MID", new int[]{SPECIALBTN_MOUSEMID}, "${margin}", "${margin}"),
                    new ControlData("SCROLLUP", new int[]{SPECIALBTN_SCROLLUP}, "${margin}", "${margin}"),
                    new ControlData("SCROLLDOWN", new int[]{SPECIALBTN_SCROLLDOWN}, "${margin}", "${margin}"),
                    new ControlData("MENU", new int[]{SPECIALBTN_MENU}, "${margin}", "${margin}"),

                    new ControlData("BCK", new int[]{SPECIALBTN_MOUSEBCK}, "${margin}", "${margin}"),
                    new ControlData("FWD", new int[]{SPECIALBTN_MOUSEFWD}, "${margin}", "${margin}"),

                    // Appended, never inserted. See SPECIALBTN_GAMEKEYBOARD.
                    new ControlData("KEYS", new int[]{SPECIALBTN_GAMEKEYBOARD}, "${margin}", "${margin}"),
                    new ControlData("VOICE", new int[]{SPECIALBTN_VOICE}, "${margin}", "${margin}"),
                    new ControlData("SNAP", new int[]{SPECIALBTN_SCREENSHOT}, "${margin}", "${margin}"),
            };
        }

        return SPECIAL_BUTTONS;
    }

    public static List<String> buildSpecialButtonArray() {
        if (SPECIAL_BUTTON_NAME_ARRAY == null) {
            List<String> nameList = new ArrayList<>();
            for (ControlData btn : getSpecialButtons()) {
                nameList.add("SPECIAL_" + btn.name);
            }
            SPECIAL_BUTTON_NAME_ARRAY = nameList;
            Collections.reverse(SPECIAL_BUTTON_NAME_ARRAY);
        }

        return SPECIAL_BUTTON_NAME_ARRAY;
    }

    private static float calculate(String math) {
        setExpression(math);
        return (float) builder.get().build().evaluate();
    }

    private static int[] inflateKeycodeArray(int[] keycodes) {
        int[] inflatedArray = new int[]{GLFW_KEY_UNKNOWN, GLFW_KEY_UNKNOWN, GLFW_KEY_UNKNOWN, GLFW_KEY_UNKNOWN};
        System.arraycopy(keycodes, 0, inflatedArray, 0, keycodes.length);
        return inflatedArray;
    }

    /**
     * Create a builder, keep a weak reference to it to use it with all views on first inflation
     */
    private static void buildExpressionBuilder() {
        ExpressionBuilder expressionBuilder = new ExpressionBuilder("1 + 1")
                .function(new Function("dp", 1) {
                    @Override
                    public double apply(double... args) {
                        return Tools.pxToDp((float) args[0]);
                    }
                })
                .function(new Function("px", 1) {
                    @Override
                    public double apply(double... args) {
                        return Tools.dpToPx((float) args[0]);
                    }
                });
        builder = new WeakReference<>(expressionBuilder);
    }

    /**
     * wrapper for the WeakReference to the expressionField.
     *
     * @param stringExpression the expression to set.
     */
    private static void setExpression(String stringExpression) {
        if (builder.get() == null) buildExpressionBuilder();
        builder.get().expression(stringExpression);
    }

    /**
     * Build a shared conversion map without the ControlData dependent values
     * You need to set the view dependent values before using it.
     */
    private static void buildConversionMap() {
        // Values in the map below may be always changed
        ArrayMap<String, String> keyValueMap = new ArrayMap<>(10);
        keyValueMap.put("top", "0");
        keyValueMap.put("left", "0");
        keyValueMap.put("right", "DUMMY_RIGHT");
        keyValueMap.put("bottom", "DUMMY_BOTTOM");
        keyValueMap.put("width", "DUMMY_WIDTH");
        keyValueMap.put("height", "DUMMY_HEIGHT");
        keyValueMap.put("screen_width", "DUMMY_DATA");
        keyValueMap.put("screen_height", "DUMMY_DATA");
        keyValueMap.put("margin", Integer.toString((int) ControlInterface.getMarginDistance()));
        keyValueMap.put("preferred_scale", "DUMMY_DATA");

        conversionMap = new WeakReference<>(keyValueMap);
    }

    public float insertDynamicPos(String dynamicPos) {
        // Insert value to ${variable}
        String insertedPos = JSONUtils.insertSingleJSONValue(dynamicPos, fillConversionMap());

        // Calculate, because the dynamic position contains some math equations
        return calculate(insertedPos);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean containsKeycode(int keycodeToCheck) {
        for (int keycode : keycodes)
            if (keycodeToCheck == keycode)
                return true;

        return false;
    }

    //Getters || setters (with conversion for ease of use)
    public float getWidth() {
        return Tools.dpToPx(width);
    }

    public void setWidth(float widthInPx) {
        width = Tools.pxToDp(widthInPx);
    }

    public float getHeight() {
        return Tools.dpToPx(height);
    }

    public void setHeight(float heightInPx) {
        height = Tools.pxToDp(heightInPx);
    }

    /**
     * Fill the conversionMap with controlData dependent values.
     * The returned valueMap should NOT be kept in memory.
     *
     * @return the valueMap to use.
     */
    private Map<String, String> fillConversionMap() {
        ArrayMap<String, String> valueMap = conversionMap.get();
        if (valueMap == null) {
            buildConversionMap();
            valueMap = conversionMap.get();
        }

        valueMap.put("right", Float.toString(CallbackBridge.physicalWidth - getWidth()));
        valueMap.put("bottom", Float.toString(CallbackBridge.physicalHeight - getHeight()));
        valueMap.put("width", Float.toString(getWidth()));
        valueMap.put("height", Float.toString(getHeight()));
        valueMap.put("screen_width", Integer.toString(CallbackBridge.physicalWidth));
        valueMap.put("screen_height", Integer.toString(CallbackBridge.physicalHeight));
        valueMap.put("preferred_scale", Float.toString(LauncherPreferences.PREF_BUTTONSIZE));

        return valueMap;
    }

}
