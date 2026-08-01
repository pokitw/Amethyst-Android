package net.kdt.pojavlaunch;

import android.os.Bundle;
import android.view.View;

import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.ui.game.ControlCenterCallbacks;
import net.kdt.pojavlaunch.ui.game.ControlCenterHost;

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

		View pullButton = findViewById(R.id.drawer_button);
		pullButton.setOnClickListener(v -> mControlCenter.open());

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
	}

	@Override
	public void onBackPressed() {
		if(mControlCenter.isOpen()) {
			mControlCenter.close();
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
	@Override public void onCustomControls() {}
	@Override public void onSendKeycode() {}
	@Override public void onQuickSettings() {}
	@Override public void onLogOutput() {}
	@Override public void onForceClose() {}
	@Override public long recordingElapsedMs() { return 0L; }
	@Override public long recordingBytes() { return 0L; }
}
