package net.kdt.pojavlaunch;

import android.os.Bundle;
import android.view.View;

import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.testlaunch.TestLaunch;
import net.kdt.pojavlaunch.testlaunch.TestLaunchRequest;
import net.kdt.pojavlaunch.ui.game.ControlCenterCallbacks;
import net.kdt.pojavlaunch.ui.controls.ControlEditorHost;
import net.kdt.pojavlaunch.ui.game.ControlCenterHost;
import net.kdt.pojavlaunch.ui.game.ControlTestHost;

import java.io.IOException;


/**
 * The control layout editor, opened from Settings.
 *
 * Its menu was a right-edge drawer of {@code simple_list_item_1} rows — which is exactly what the
 * in-game menu was before it became the control center. Rather than redesign the same list twice,
 * this hosts the control center in its editor mode, so the editor reached from Settings and the
 * editor reached from inside a game are one screen with two ways in.
 *
 * Only the editor half of {@link ControlCenterCallbacks} does anything here. The rest belongs to a
 * running game — there is nothing to record, no log to read and nothing to force close — so they
 * are deliberately empty rather than faked.
 */
public class CustomControlsActivity extends BaseActivity implements EditorExitable, ControlCenterCallbacks {
	private ControlLayout mControlLayout;
	private ControlCenterHost mControlCenter;
	private ControlEditorHost mControlEditor;
	private ControlTestHost mControlTest;
	private View mPullButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_custom_controls);

		mControlLayout = findViewById(R.id.customctrl_controllayout);
		mControlCenter = new ControlCenterHost(
				findViewById(R.id.control_center),
				findViewById(R.id.control_center_pill),
				this);
		mControlCenter.setEditorMode(true);
		mControlCenter.applyTestable(true);
		mControlEditor = new ControlEditorHost(findViewById(R.id.control_editor), mControlLayout);
		mControlLayout.setEditorHost(mControlEditor);

		mPullButton = findViewById(R.id.drawer_button);
		mPullButton.setOnClickListener(v -> mControlCenter.open());
		// The way back out of a test is the panel's own Done, so the tab that opens the editor's
		// menu is taken away for the duration: tapping it mid-test would drop the editor's sheet
		// on top of the layout being tried out.
		mControlTest = new ControlTestHost(
				findViewById(R.id.control_test), mControlLayout,
				() -> mPullButton.setVisibility(View.VISIBLE));

		mControlLayout.setModifiable(true);
		try {
			mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
		}catch (IOException e) {
			Tools.showError(this, e);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mControlCenter.release();
		// The test seam is a static, so a session left attached by an activity that went away
		// would swallow every key the next one sent.
		mControlTest.release();
	}

	@Override
	public void onBackPressed() {
		// Back leaves the test before it means anything else, which is the innermost layer.
		if(mControlTest.isRunning()) {
			mControlTest.stop();
			return;
		}
		if(mControlCenter.isOpen()) {
			mControlCenter.close();
			return;
		}
		// Back closes one layer of the editor at a time — the key picker, then the panel — before
		// it means "leave", which is the order someone deep in a button's settings expects.
		if(mControlEditor.isOpen()) {
			mControlEditor.dismissLayer();
			return;
		}
		mControlLayout.askToExit(this);
	}

	@Override
	public void exitEditor() {
		super.onBackPressed();
	}

	/* Editor actions. The same seven the drawer used to list. */

	@Override
	public void onEditorAddButton() {
		mControlLayout.addControlButton(new ControlData("New"));
		mControlCenter.close();
	}

	@Override
	public void onEditorAddDrawer() {
		mControlLayout.addDrawer(new ControlDrawerData());
		mControlCenter.close();
	}

	@Override
	public void onEditorAddJoystick() {
		mControlLayout.addJoystickButton(new ControlJoystickData());
		mControlCenter.close();
	}

	/**
	 * Launch the real game into the control test world.
	 *
	 * The layout is saved first, because the game reads it from disk and an unsaved change would
	 * be tested by not being there. Then the editor gets out of the way: the launcher is the only
	 * activity that can raise the launch, so this finishes and leaves the request behind for it.
	 */
	@Override
	public void onEditorTest() {
		mControlCenter.close();
		try {
			mControlLayout.save(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
		} catch (Throwable t) {
			Tools.showError(this, t);
			return;
		}
		try {
			TestLaunch.prepare(this);
		} catch (Throwable t) {
			Tools.showError(this, t);
			return;
		}
		TestLaunchRequest.request();
		finish();
	}

	@Override
	public void onEditorTestHere() {
		mControlCenter.close();
		mPullButton.setVisibility(View.GONE);
		mControlTest.start();
	}

	@Override public void onEditorLoad() { mControlCenter.close(); mControlLayout.openLoadDialog(); }
	@Override public void onEditorSave() { mControlCenter.close(); mControlLayout.openSaveDialog(this); }

	@Override
	public void onEditorSetDefault() {
		mControlCenter.close();
		mControlLayout.openSetDefaultDialog();
	}

	@Override
	public void onEditorShare() {
		mControlCenter.close();
		mControlLayout.shareLayout(this);
	}

	@Override
	public void onEditorExit() {
		mControlCenter.close();
		mControlLayout.askToExit(this);
	}

	/* Game actions, which this activity has no game to perform them on. */

	@Override public void onToggleRecording() {}
	// There is no game rendering behind this activity, so there is no frame to take a picture of.
	@Override public void onScreenshot() {}
	@Override public void onToggleShutter() {}
	@Override public void onCustomControls() {}
	@Override public void onSendKeycode() {}
	// The editor has its own reporting, on the Press here session, so this would be a second
	// answer to a question already answered on this screen.
	@Override public void onToggleControlDebug() {}
	@Override public void onQuickSettings() {}
	@Override public void onLogOutput() {}
	@Override public void onForceClose() {}
	@Override public long recordingElapsedMs() { return 0L; }
	@Override public long recordingBytes() { return 0L; }
}
