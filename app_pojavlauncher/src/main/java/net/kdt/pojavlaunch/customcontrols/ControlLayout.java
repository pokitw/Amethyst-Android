package net.kdt.pojavlaunch.customcontrols;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static org.lwjgl.glfw.CallbackBridge.isGrabbing;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonSyntaxException;
import com.kdt.pickafile.FileListView;
import com.kdt.pickafile.FileSelectedListener;

import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlButton;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlSubButton;
import net.kdt.pojavlaunch.customcontrols.handleview.ControlHandleView;
import net.kdt.pojavlaunch.customcontrols.textures.ControlTextures;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.ui.controls.ControlEditorHost;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControlLayout extends FrameLayout {
	protected CustomControls mLayout;
	/* Accessible when inside the game by ControlInterface implementations, cached for perf. */
	private MinecraftGLSurface mGameSurface = null;

	/* Cache to buttons for performance purposes */
	private List<ControlInterface> mButtons;
	private boolean mModifiable = false;
	private boolean mIsModified;
	private boolean mControlVisible = false;

	private ControlEditorHost mControlEditor = null;
	private ControlHandleView mHandleView;

	/* ---------------------------------------------------------------- the editor's own drawing.
	 *
	 * All three of these are drawn AFTER the children, in dispatchDraw, and none of them is a
	 * view. That is deliberate: a control layout is a thing you drag buttons around on, and every
	 * view added over it is a view that can swallow a drag (12.9). An overlay that only ever
	 * draws cannot take a touch from anything.
	 *
	 * They also only draw while modifiable. In a game this same class sits over the GL surface
	 * and must stay completely transparent, so a backdrop painted unconditionally would cover
	 * Minecraft with a picture of a hill.
	 */
	private final Paint mEditorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF mEditorRect = new RectF();
	private Shader mSkyShader;
	private int mSkyShaderHeight = -1;
	/** The control the handle is attached to, outlined so the selection is visible on the layout. */
	private ControlInterface mSelected;
	private boolean mShowingResize;
	private float mResizeWidthDp, mResizeHeightDp;
	/**
	 * Whether a test session is running: the layout live, in game mode, with no game behind it.
	 *
	 * Separate from {@link #mModifiable} because it is the third state this view has. Editing
	 * draws the world and takes every touch before a button sees it; testing draws the world
	 * <i>and</i> the game's own furniture, and lets the buttons have the touches. A running game
	 * draws nothing at all, which is why every one of these is gated.
	 */
	private boolean mTestMode;
	private boolean mTestGuides = true;
	/** The slide-to-repeat threshold, ringed around the selected control while it is being set. */
	private boolean mShowingSlide;
	private float mSlideRadiusPx;
	private PathEffect mSlideDash;
	/** Where the selection outline was last drawn, so a control that has moved can be spotted. */
	private float mSelectionX, mSelectionY;
	private int mSelectionWidth, mSelectionHeight;

	/**
	 * Redraw the overlay when the selected control moves or changes size.
	 *
	 * <b>Without this the outline is left behind at the position the control has just been dragged
	 * away from.</b> A hardware-accelerated view moved with setX has only its own render node
	 * transformed; the parent's display list is not re-recorded, so whatever this class drew last
	 * time is still what it is showing. The child moves and the ring around it does not.
	 *
	 * A watcher rather than an invalidate on the drag path, because the drag path is not the only
	 * thing that moves a control: the editor panel's position and size sliders move one with no
	 * touch event reaching this layout at all, and so does a snap. Everything that can move a
	 * control has to draw it before the next frame, and this is the last point common to all of
	 * them. It costs four comparisons per frame, and only while something is selected.
	 *
	 * The cache is updated here rather than in the draw, so a selection that is invisible or
	 * offscreen cannot leave it stale and ask for a redraw on every frame forever.
	 */
	private final ViewTreeObserver.OnPreDrawListener mSelectionWatcher =
			new ViewTreeObserver.OnPreDrawListener() {
		@Override
		public boolean onPreDraw() {
			if (mSelected == null) return true;
			View view = mSelected.getControlView();
			if (view.getX() == mSelectionX && view.getY() == mSelectionY
					&& view.getWidth() == mSelectionWidth
					&& view.getHeight() == mSelectionHeight) {
				return true;
			}
			mSelectionX = view.getX();
			mSelectionY = view.getY();
			mSelectionWidth = view.getWidth();
			mSelectionHeight = view.getHeight();
			invalidate();
			return true;
		}
	};
	private ControlButtonMenuListener mMenuListener;
	public String mLayoutFileName;

	public ControlLayout(Context ctx) {
		super(ctx);
	}

	public ControlLayout(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
	}


	public void loadLayout(String jsonPath) throws IOException, JsonSyntaxException {
		CustomControls layout = LayoutConverter.loadAndConvertIfNecessary(jsonPath);
		if(layout != null) {
			loadLayout(layout);
			updateLoadedFileName(jsonPath);
			return;
		}

		throw new IOException("Unsupported control layout version");
	}

	public void loadLayout(CustomControls controlLayout) {
		// Every way of loading a layout arrives here, which makes this the one place the artwork
		// has to be current: the buttons about to be built each take a reference to it, and the
		// game re-runs this after a trip to Settings, where the style may well have changed.
		// Keyed on the pack's name, so the ordinary case is a string comparison, and it cannot
		// throw — a texture that will not load is a flat button, never a launch that fails.
		ControlTextures.ensureLoaded(getContext(), ControlSkin.texturePack());

		boolean sanitizedModified = false;
		if(controlLayout != null) {
			sanitizedModified = LayoutSanitizer.sanitizeLayout(controlLayout);
		}
		removeAllButtons();
		if(mLayout != null) {
			mLayout.mControlDataList = null;
			mLayout = null;
		}

		System.gc();
		mapTable.clear();

		// Cleanup buttons only when input layout is null
		if (controlLayout == null) return;

		mLayout = controlLayout;
		

		// Joystick(s) first, to workaround the touch dispatch
		for(ControlJoystickData joystick : mLayout.mJoystickDataList){
			addJoystickView(joystick);
		}

		//CONTROL BUTTON
		for (ControlData button : controlLayout.mControlDataList) {
			addControlView(button);
		}

		//CONTROL DRAWER
		for(ControlDrawerData drawerData : controlLayout.mDrawerDataList){
			ControlDrawer drawer = addDrawerView(drawerData);
			if(mModifiable) drawer.areButtonsVisible = true;
		}

		mLayout.scaledAt = LauncherPreferences.PREF_BUTTONSIZE;

		setModified(sanitizedModified);
		mButtons = null;
		getButtonChildren(); // Force refresh
	} // loadLayout

	//CONTROL BUTTON
	public void addControlButton(ControlData controlButton) {
		mLayout.mControlDataList.add(controlButton);
		addControlView(controlButton);
	}

	private void addControlView(ControlData controlButton) {
		final ControlButton view = new ControlButton(this, controlButton);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);

		setModified(true);
	}

	// CONTROL DRAWER
	public void addDrawer(ControlDrawerData drawerData){
		mLayout.mDrawerDataList.add(drawerData);
		addDrawerView();
	}

	private void addDrawerView(){
		addDrawerView(null);
	}

	private ControlDrawer addDrawerView(ControlDrawerData drawerData){

		final ControlDrawer view = new ControlDrawer(this,drawerData == null ? mLayout.mDrawerDataList.get(mLayout.mDrawerDataList.size()-1) : drawerData);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);
		//CONTROL SUB BUTTON
		for (ControlData subButton : view.getDrawerData().buttonProperties) {
			addSubView(view, subButton);
		}

		setModified(true);
		return view;
	}

	//CONTROL SUB-BUTTON
	public void addSubButton(ControlDrawer drawer, ControlData controlButton){
		//Yep there isn't much here
		drawer.getDrawerData().buttonProperties.add(controlButton);
		addSubView(drawer, drawer.getDrawerData().buttonProperties.get(drawer.getDrawerData().buttonProperties.size()-1 ));
	}

	private void addSubView(ControlDrawer drawer, ControlData controlButton){
		final ControlSubButton view = new ControlSubButton(this, controlButton, drawer);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}else{
			view.setVisible(true);
		}

		addView(view);
		drawer.addButton(view);


		setModified(true);
	}

	// JOYSTICK BUTTON
	public void addJoystickButton(ControlJoystickData data){
		mLayout.mJoystickDataList.add(data);
		addJoystickView(data);
	}

	private void addJoystickView(ControlJoystickData data){
		ControlJoystick view = new ControlJoystick(this, data);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);

	}


	private void removeAllButtons() {
		for(ControlInterface button : getButtonChildren()){
			removeView(button.getControlView());
		}

		System.gc();
		//i wanna be sure that all the removed Views will be removed after a reload
		//because if frames will slowly go down after many control changes it will be warm and bad
	}

	public void saveLayout(String path) throws Exception {
		mLayout.save(path);
		setModified(false);
	}

	public void toggleControlVisible(){
		mControlVisible = !mControlVisible;
		setControlVisible(mControlVisible);
	}

	public float getLayoutScale(){
		return mLayout.scaledAt;
	}

	public CustomControls getLayout(){
		return mLayout;
	}

	public void setControlVisible(boolean isVisible) {
		if (mModifiable) return; // Not using on custom controls activity

		mControlVisible = isVisible;
		for(ControlInterface button : getButtonChildren()){
			button.setVisible(((button.getProperties().displayInGame && isGrabbing()) || (button.getProperties().displayInMenu && !isGrabbing())) && isVisible);
		}
	}

	public void setModifiable(boolean isModifiable) {
		if(!isModifiable && mModifiable){
			removeEditWindow();
		}
		mModifiable = isModifiable;
		// The backdrop and the overlay are both gated on this flag, so the moment it changes the
		// layout has to be redrawn or the editor opens onto the game's transparent ground.
		invalidate();
		if(isModifiable){
			// In edit mode, all controls have to be shown
			for(ControlInterface button : getButtonChildren()){
				button.setVisible(true);
			}
		}
	}

	public boolean getModifiable(){
		return mModifiable;
	}

	/**
	 * The world the controls are arranged against.
	 *
	 * <b>The editor used to be near-black, which is the one background that flatters every
	 * button and tells you nothing.</b> Controls are translucent, and what matters when placing
	 * them is whether they will still be legible over a bright world: the same argument the
	 * texture pack picker already settled (14), where the previews are drawn over sky and grass
	 * because against a dark surface every pack read as the same dark rectangle. These are that
	 * picker's exact colours, so the two places the launcher previews controls agree.
	 *
	 * Drawn rather than set as a background drawable so it costs nothing in a game, where this
	 * method is never reached.
	 */
	private void drawEditorBackdrop(Canvas canvas) {
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) return;
		float horizon = height * 0.70f;

		if (mSkyShader == null || mSkyShaderHeight != height) {
			mSkyShader = new LinearGradient(0, 0, 0, horizon,
					0xFF63819C, 0xFF8FAAC2, Shader.TileMode.CLAMP);
			mSkyShaderHeight = height;
		}
		mEditorPaint.setShader(mSkyShader);
		mEditorPaint.setStyle(Paint.Style.FILL);
		canvas.drawRect(0, 0, width, horizon, mEditorPaint);
		mEditorPaint.setShader(null);
		mEditorPaint.setColor(0xFF4C6438);
		canvas.drawRect(0, horizon, width, height, mEditorPaint);
		// The one line that is not in the picker: a horizon needs an edge or the two flat bands
		// read as a colour bug rather than as ground meeting sky.
		mEditorPaint.setColor(0xFF3E542D);
		canvas.drawRect(0, horizon, width, horizon + Tools.dpToPx(1.5f), mEditorPaint);
	}

	/**
	 * How far the finger has to slide to start a repeat, drawn at its real size.
	 *
	 * A slider that says "20 dp" is a number nobody has an intuition for, and the question it is
	 * actually being asked is whether the gesture fits inside the button or runs off it. That is a
	 * question about a distance next to a size, so it is answered by drawing both.
	 *
	 * Centred on the control, which is the honest average rather than the truth: the real gesture
	 * is measured from wherever the thumb landed, so a press near an edge reaches the threshold
	 * sooner on one side. Drawing every possible circle would say less than drawing one.
	 */
	private void drawSlideRing(Canvas canvas, View view) {
		if (mSlideDash == null) {
			mSlideDash = new DashPathEffect(
					new float[]{Tools.dpToPx(7), Tools.dpToPx(5)}, 0);
		}
		float cx = view.getX() + view.getWidth() / 2f;
		float cy = view.getY() + view.getHeight() / 2f;
		mEditorPaint.setStyle(Paint.Style.STROKE);
		mEditorPaint.setPathEffect(mSlideDash);
		// Dashed, because a solid ring reads as a boundary the control has and this is a distance
		// the finger travels. Same two-pass keyline as the selection, for the same reason.
		mEditorPaint.setStrokeWidth(Tools.dpToPx(3.4f));
		mEditorPaint.setColor(0x8C0E0B12);
		canvas.drawCircle(cx, cy, mSlideRadiusPx, mEditorPaint);
		mEditorPaint.setStrokeWidth(Tools.dpToPx(1.6f));
		mEditorPaint.setColor(0xFFC08CE8);
		canvas.drawCircle(cx, cy, mSlideRadiusPx, mEditorPaint);
		// The paint is shared with the backdrop and the readout pill, so both of these have to go
		// back or the next thing drawn with it comes out dashed and hollow.
		mEditorPaint.setPathEffect(null);
		mEditorPaint.setStyle(Paint.Style.FILL);
	}

	/** The selected control, outlined, and the size while it is being dragged. */
	private void drawEditorOverlay(Canvas canvas) {
		if (mSelected != null && mSelected.getControlView().isShown()) {
			View view = mSelected.getControlView();
			if (mShowingSlide) drawSlideRing(canvas, view);
			float inset = Tools.dpToPx(2);
			mEditorRect.set(view.getX() - inset, view.getY() - inset,
					view.getX() + view.getWidth() + inset,
					view.getY() + view.getHeight() + inset);
			float radius = Tools.dpToPx(6);
			mEditorPaint.setStyle(Paint.Style.STROKE);
			// A dark keyline under the accent one, so the selection is visible over both the sky
			// and the grass rather than only over whichever the button happens to sit on.
			mEditorPaint.setStrokeWidth(Tools.dpToPx(3.5f));
			mEditorPaint.setColor(0x8C0E0B12);
			canvas.drawRoundRect(mEditorRect, radius, radius, mEditorPaint);
			mEditorPaint.setStrokeWidth(Tools.dpToPx(1.8f));
			mEditorPaint.setColor(0xFFC08CE8);
			canvas.drawRoundRect(mEditorRect, radius, radius, mEditorPaint);
			mEditorPaint.setStyle(Paint.Style.FILL);
		}

		if (!mShowingResize) return;
		String text = Math.round(mResizeWidthDp) + " x " + Math.round(mResizeHeightDp);
		mEditorPaint.setTextSize(Tools.dpToPx(15));
		float padding = Tools.dpToPx(10);
		float textWidth = mEditorPaint.measureText(text);
		Paint.FontMetrics metrics = mEditorPaint.getFontMetrics();
		float pillHeight = (metrics.descent - metrics.ascent) + padding;
		float pillWidth = textWidth + padding * 2;
		// Top centre: the corner being dragged is under a thumb, and anywhere near it would be
		// under the hand doing the dragging.
		float left = (getWidth() - pillWidth) / 2f;
		float top = Tools.dpToPx(16);
		mEditorRect.set(left, top, left + pillWidth, top + pillHeight);
		float radius = pillHeight / 2f;
		mEditorPaint.setColor(0xF01D1826);
		canvas.drawRoundRect(mEditorRect, radius, radius, mEditorPaint);
		mEditorPaint.setColor(0xFFC08CE8);
		canvas.drawText(text, left + padding, top + padding / 2f - metrics.ascent, mEditorPaint);
	}

	/**
	 * Where the game's own hotbar and crosshair will be, so a control can be seen covering them.
	 *
	 * <b>This is the one question the editor could not answer and a real game could.</b> A button
	 * placed at the bottom middle looks fine on an empty screen and steals hotbar slot taps in
	 * play, which is the exact reason the shipped Bedrock layout's third column rides the action
	 * cluster instead of sitting along the bottom (14). Drawn from Minecraft's own automatic GUI
	 * scale, which is the largest whole number at which a 320 by 240 interface still fits, and at
	 * its own measurements: the hotbar is 182 by 22 at scale 1, nine slots of 20 with a one pixel
	 * frame, sitting flush with the bottom of the screen.
	 *
	 * <p>A <b>guide</b>, and it says so on the panel rather than here: GUI scale is a setting, and
	 * a player who has changed it gets a hotbar of another size. Automatic is what it ships as and
	 * what nearly everyone leaves it on.
	 */
	private void drawGameGuides(Canvas canvas) {
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) return;
		int scale = Math.max(1, Math.min(width / 320, height / 240));

		float barWidth = 182f * scale;
		float barHeight = 22f * scale;
		float left = (width - barWidth) / 2f;
		float top = height - barHeight;

		mEditorPaint.setStyle(Paint.Style.FILL);
		mEditorPaint.setColor(0x40000000);
		canvas.drawRect(left, top, left + barWidth, height, mEditorPaint);
		mEditorPaint.setStyle(Paint.Style.STROKE);
		mEditorPaint.setStrokeWidth(Math.max(1f, scale * 0.6f));
		mEditorPaint.setColor(0x66FFFFFF);
		canvas.drawRect(left, top, left + barWidth, height, mEditorPaint);
		// The nine slots, because an empty rectangle reads as a panel and a divided one reads as
		// the hotbar, which is the whole point of drawing it.
		for (int slot = 1; slot < 9; slot++) {
			float x = left + scale + slot * 20f * scale;
			canvas.drawLine(x, top + scale, x, height - scale, mEditorPaint);
		}

		float centreX = width / 2f;
		float centreY = height / 2f;
		float arm = 7.5f * scale;
		mEditorPaint.setStrokeWidth(Math.max(1f, scale));
		mEditorPaint.setColor(0x99FFFFFF);
		canvas.drawLine(centreX - arm, centreY, centreX + arm, centreY, mEditorPaint);
		canvas.drawLine(centreX, centreY - arm, centreX, centreY + arm, mEditorPaint);
		mEditorPaint.setStyle(Paint.Style.FILL);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		// The backdrop cannot go here: dispatchDraw runs after the view's own background and
		// before the children, so painting it at the top of this method is what puts it behind
		// the buttons. The overlay goes after them.
		if (mModifiable || mTestMode) drawEditorBackdrop(canvas);
		if (mTestMode && mTestGuides) drawGameGuides(canvas);
		super.dispatchDraw(canvas);
		if (mModifiable) drawEditorOverlay(canvas);
	}

	/**
	 * Run the layout as a game would, or stop.
	 *
	 * Turning it on puts the layout out of edit mode, which is what makes the buttons take their
	 * own touches rather than having them intercepted for dragging, and applies the visibility
	 * rules so a control marked for menus only is hidden exactly as it would be in play.
	 */
	public void setTestMode(boolean testing) {
		mTestMode = testing;
		setModifiable(!testing);
		if (testing) {
			// mControlVisible starts false and is only ever turned on by the game, because the
			// editor never reads it. Applying the visibility rules without setting it first hides
			// every control on the layout, which is a test session that opens onto an empty
			// screen: the exact failure this is built to help somebody find, staged by the tool
			// itself. It has to be set through the setter, which is guarded on mModifiable, so
			// this can only run after the line above has left edit mode.
			setControlVisible(true);
			applyTestGrabState(true);
		}
		invalidate();
	}

	public boolean isTesting() {
		return mTestMode;
	}

	/**
	 * Show the layout as it would look while playing, or while a screen is open.
	 *
	 * The rule this exercises is the one that most often reads as a broken layout (17): every GUI
	 * ungrabs the cursor, the ungrabbed state draws only the controls marked for menus, and a
	 * layout with none of them shows an empty screen and looks like it failed to load. Being able
	 * to flip between the two answers here is the cheapest way to find that out.
	 */
	public void applyTestGrabState(boolean grabbing) {
		for (ControlInterface button : getButtonChildren()) button.onGrabState(grabbing);
	}

	public void applyTestGuides(boolean guides) {
		mTestGuides = guides;
		invalidate();
	}

	public void setModified(boolean isModified) {
		mIsModified = isModified;
	}

	public List<ControlInterface> getButtonChildren(){
		if(mModifiable || mButtons == null){
			mButtons = new ArrayList<>();
			for(int i=0; i<getChildCount(); ++i){
				View v = getChildAt(i);
				if(v instanceof ControlInterface)
					mButtons.add(((ControlInterface) v));
			}
		}

		return mButtons;
	}

	public void refreshControlButtonPositions(){
		for(ControlInterface button : getButtonChildren()){
			button.setDynamicX(button.getProperties().dynamicX);
			button.setDynamicY(button.getProperties().dynamicY);
		}
	}

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        // The control being edited has just been taken off screen, so the panel is now editing
        // something that no longer exists.
        if(child instanceof ControlInterface && mControlEditor != null){
            mControlEditor.close();
        }
    }

    /**
	 * The editor panel, which the hosting activity owns because it is Compose and this is not.
	 *
	 * Set once, before anything can be edited. Without it the layout is still perfectly usable —
	 * buttons drag and snap — there is simply nothing to edit them with, which is what the null
	 * checks are for rather than an assumption that it is always there.
	 */
	public void setEditorHost(ControlEditorHost editorHost){
		mControlEditor = editorHost;
	}

	/** Open the editor on one control, and put the resize handles around it. */
	public void editControlButton(ControlInterface button){
		if(mControlEditor == null) return;

		// Before open, not after: the editor state is built synchronously in there and asks for
		// the slide ring from its constructor, so a reset afterwards would wipe it again.
		mSelected = button;
		mShowingSlide = false;
		mControlEditor.open(button);

		if(mHandleView == null){
			mHandleView = new ControlHandleView(getContext());
			addView(mHandleView);
		}
		mHandleView.setControlButton(button);
		// Removed first because addOnPreDrawListener does not deduplicate, and selecting a second
		// control without closing the panel comes straight back through here.
		getViewTreeObserver().removeOnPreDrawListener(mSelectionWatcher);
		getViewTreeObserver().addOnPreDrawListener(mSelectionWatcher);
		invalidate();
	}

	/**
	 * Show or hide the ring that says how far a slide has to go to start a repeat.
	 *
	 * Driven from the editor panel rather than from the control's own data, because the point of
	 * it is to answer the slider while the slider is being dragged.
	 */
	public void showSlideRadius(float radiusPx, boolean visible) {
		mShowingSlide = visible;
		mSlideRadiusPx = radiusPx;
		invalidate();
	}

	/**
	 * The size a resize is passing through, shown while the finger is still down.
	 *
	 * Drawn here rather than by the grip because this is the canvas that is always the right size
	 * and is already on top of everything; the grip is a 34dp square pinned to a corner, and a
	 * readout drawn inside it would either be illegible or would need the view enlarged into
	 * something that starts blocking touches on its neighbours.
	 */
	public void showResizeReadout(float widthPx, float heightPx, boolean visible) {
		mShowingResize = visible;
		mResizeWidthDp = Tools.pxToDp(widthPx);
		mResizeHeightDp = Tools.pxToDp(heightPx);
		invalidate();
	}

	/** Swap the panel if the button position requires it */
	public void adaptPanelPosition(){
		if(mControlEditor != null) mControlEditor.reposition();
	}


	final HashMap<View, ControlInterface> mapTable = new HashMap<>();

	//While this is called onTouch, this should only be called from a ControlButton.
	public void onTouch(View v, MotionEvent ev) {
		ControlInterface lastControlButton = mapTable.get(v);

		// Map location to screen coordinates
		ev.offsetLocation(v.getX(), v.getY());


		//Check if the action is cancelling, reset the lastControl button associated to the view
		if (ev.getActionMasked() == MotionEvent.ACTION_UP
				|| ev.getActionMasked() == MotionEvent.ACTION_CANCEL
				|| ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
			if (lastControlButton != null) lastControlButton.sendKeyPresses(false);
			mapTable.put(v, null);
			return;
		}

		if (ev.getActionMasked() != MotionEvent.ACTION_MOVE) return;


		//Optimization pass to avoid looking at all children again
		if (lastControlButton != null) {
			// The line that was here printed four floats to stdout on every move event of every
			// swipe, which is the hot path of a running game. Whatever it was for, it was left in.
			if (ev.getX() > lastControlButton.getControlView().getX()
					&& ev.getX() < lastControlButton.getControlView().getX() + lastControlButton.getControlView().getWidth()
					&& ev.getY() > lastControlButton.getControlView().getY()
					&& ev.getY() < lastControlButton.getControlView().getY() + lastControlButton.getControlView().getHeight()) {
				return;
			}
		}

		//Release last keys
		if (lastControlButton != null) lastControlButton.sendKeyPresses(false);
		mapTable.remove(v);

		// Update the state of all swipeable buttons
		for (ControlInterface button : getButtonChildren()) {
			if (!button.getProperties().isSwipeable) continue;

			if (ev.getX() > button.getControlView().getX()
					&& ev.getX() < button.getControlView().getX() + button.getControlView().getWidth()
					&& ev.getY() > button.getControlView().getY()
					&& ev.getY() < button.getControlView().getY() + button.getControlView().getHeight()) {

				//Press the new key
				if (!button.equals(lastControlButton)) {
					button.sendKeyPresses(true);
					mapTable.put(v, button);
					return;
				}

			}
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mModifiable && event.getActionMasked() != MotionEvent.ACTION_UP || mControlEditor == null)
			return true;

		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

		// When the input window cannot be hidden, it returns false
		if(!imm.hideSoftInputFromWindow(getWindowToken(), 0)){
			if(mControlEditor.dismissLayer() && mHandleView != null){
				mHandleView.hide();
			}
		}
		return true;
	}

	public void removeEditWindow() {
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

		// When the input window cannot be hidden, it returns false
		imm.hideSoftInputFromWindow(getWindowToken(), 0);
		if(mControlEditor != null) mControlEditor.close();
		if(mHandleView != null) mHandleView.hide();
		getViewTreeObserver().removeOnPreDrawListener(mSelectionWatcher);
		mSelected = null;
		mShowingResize = false;
		mShowingSlide = false;
		invalidate();
	}

	public void save(String path){
		try {
			mLayout.save(path);
		} catch (IOException e) {Log.e("ControlLayout", "Failed to save the layout at:" + path);}
	}


	public boolean hasMenuButton() {
		for(ControlInterface controlInterface : getButtonChildren()){
			for (int keycode : controlInterface.getProperties().keycodes) {
				if (keycode == ControlData.SPECIALBTN_MENU) return true;
			}
		}
		return false;
	}

	public void setMenuListener(ControlButtonMenuListener menuListener) {
		this.mMenuListener = menuListener;
	}

	public void notifyAppMenu() {
		if(mMenuListener != null) mMenuListener.onClickedMenu();
	}

	public void notifyGameKeyboard() {
		if(mMenuListener != null) mMenuListener.onClickedGameKeyboard();
	}

	public void notifyVoice(boolean down) {
		if(mMenuListener != null) mMenuListener.onClickedVoice(down);
	}

	public void notifyVoiceShortcut(boolean down) {
		if(mMenuListener != null) mMenuListener.onVoiceShortcut(down);
	}

	public void notifyScreenshot() {
		if(mMenuListener != null) mMenuListener.onClickedScreenshot();
	}

	/** Cached getter for perf purposes */
	public MinecraftGLSurface getGameSurface(){
		if(mGameSurface == null){
			mGameSurface = findViewById(R.id.main_game_render_view);
		}
		return mGameSurface;
	}

	public void askToExit(EditorExitable editorExitable) {
		if(mIsModified) {
			openSaveDialog(editorExitable);
		}else{
			openExitDialog(editorExitable);
		}
	}

	public void updateLoadedFileName(String path) {
		path = path.replace(Tools.CTRLMAP_PATH, ".");
		path = path.substring(0, path.length() - 5);
		mLayoutFileName = path;
	}

	public String saveToDirectory(String name) throws Exception{
		String jsonPath = Tools.CTRLMAP_PATH + "/" + name + ".json";
		saveLayout(jsonPath);
		return jsonPath;
	}

	/**
	 * Hand the layout that is open to another app.
	 * It is written out first, because a layout edited but never saved has no file to send.
	 * @param activity the activity to launch the chooser from
	 */
	public void shareLayout(Activity activity) {
		try {
			String name = mLayoutFileName == null ? "controls" : mLayoutFileName;
			Uri contentUri = DocumentsContract.buildDocumentUri(
					activity.getString(R.string.storageProviderAuthorities), saveToDirectory(name));

			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
			shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			shareIntent.setType("application/json");
			activity.startActivity(Intent.createChooser(shareIntent, name));
		} catch (Throwable th) {
			Tools.showError(activity, th);
		}
	}

	class OnClickExitListener implements View.OnClickListener {
		private final AlertDialog mDialog;
		private final EditText mEditText;
		private final EditorExitable mListener;

		public OnClickExitListener(AlertDialog mDialog, EditText mEditText, EditorExitable mListener) {
			this.mDialog = mDialog;
			this.mEditText = mEditText;
			this.mListener = mListener;
		}

		@Override
		public void onClick(View v) {
			Context context = v.getContext();
			if (mEditText.getText().toString().isEmpty()) {
				mEditText.setError(context.getString(R.string.global_error_field_empty));
				return;
			}
			try {
				String jsonPath = saveToDirectory(mEditText.getText().toString());
				Toast.makeText(context, context.getString(R.string.global_save) + ": " + jsonPath, Toast.LENGTH_SHORT).show();
				mDialog.dismiss();
				if(mListener != null) mListener.exitEditor();
			} catch (Throwable th) {
				Tools.showError(context, th, mListener != null);
			}
		}
	}

	public void openSaveDialog(EditorExitable editorExitable) {
		final Context context = getContext();
		final EditText edit = new EditText(context);
		edit.setSingleLine();
		edit.setText(mLayoutFileName);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.global_save);
		builder.setView(edit);
		builder.setPositiveButton(android.R.string.ok, null);
		builder.setNegativeButton(android.R.string.cancel, null);
		if(editorExitable != null) builder.setNeutralButton(R.string.global_save_and_exit, null);
		final AlertDialog dialog = builder.create();
		dialog.setOnShowListener(dialogInterface -> {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE)
					.setOnClickListener(new OnClickExitListener(dialog, edit, null));
			if(editorExitable != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
					.setOnClickListener(new OnClickExitListener(dialog, edit, editorExitable));
		});
		dialog.show();
	}

	public void openLoadDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.global_load);
		builder.setPositiveButton(android.R.string.cancel, null);

		final AlertDialog dialog = builder.create();
		FileListView flv = new FileListView(dialog, "json");
		if(Build.VERSION.SDK_INT < 29)flv.listFileAt(new File(Tools.CTRLMAP_PATH));
		else flv.lockPathAt(new File(Tools.CTRLMAP_PATH));
		flv.setFileSelectedListener(new FileSelectedListener(){

			@Override
			public void onFileSelected(File file, String path) {
				try {
					loadLayout(path);
				}catch (IOException e) {
					Tools.showError(getContext(), e);
				}
				dialog.dismiss();
			}
		});
		dialog.setView(flv);
		dialog.show();
	}

	public void openSetDefaultDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.customctrl_selectdefault);
		builder.setPositiveButton(android.R.string.cancel, null);

		final AlertDialog dialog = builder.create();
		FileListView flv = new FileListView(dialog, "json");
		flv.lockPathAt(new File(Tools.CTRLMAP_PATH));
		flv.setFileSelectedListener(new FileSelectedListener(){

			@Override
			public void onFileSelected(File file, String path) {
				try {
					LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", path).apply();
					LauncherPreferences.PREF_DEFAULTCTRL_PATH = path;loadLayout(path);
				}catch (IOException|JsonSyntaxException e) {
					Tools.showError(getContext(), e);
				}
				dialog.dismiss();
			}
		});
		dialog.setView(flv);
		dialog.show();
	}

	public void openExitDialog(EditorExitable exitListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setTitle(R.string.customctrl_editor_exit_title);
		builder.setMessage(R.string.customctrl_editor_exit_msg);
		builder.setPositiveButton(R.string.global_yes, (d,w)->exitListener.exitEditor());
		builder.setNegativeButton(R.string.global_no, (d,w)->{});
		builder.show();
	}

	public boolean areControlVisible(){
		return mControlVisible;
	}
}
