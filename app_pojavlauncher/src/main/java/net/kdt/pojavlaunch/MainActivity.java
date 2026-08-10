package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.dialogForceClose;
import static net.kdt.pojavlaunch.Tools.hasMods;
import static net.kdt.pojavlaunch.Tools.runMethodbyReflection;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.keyboard.VoiceInput;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.customcontrols.mouse.HotbarView;
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.QuickSettingSideDialog;
import net.kdt.pojavlaunch.media.GalleryExport;
import net.kdt.pojavlaunch.recorder.GameRecorder;
import net.kdt.pojavlaunch.recorder.RecorderPreferences;
import net.kdt.pojavlaunch.recorder.RecorderService;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.ui.controls.ControlEditorHost;
import net.kdt.pojavlaunch.ui.game.ControlCenterCallbacks;
import net.kdt.pojavlaunch.ui.game.ControlCenterHost;
import net.kdt.pojavlaunch.ui.game.GameKeyboardHost;
import net.kdt.pojavlaunch.ui.game.ScreenshotHost;
import net.kdt.pojavlaunch.ui.game.TypingPreviewHost;
import net.kdt.pojavlaunch.ui.game.VoiceInputHost;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.TouchControllerInputView;
import net.kdt.pojavlaunch.utils.TouchControllerUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLSurface;
import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class MainActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection,
        TouchControllerInputView.InputAreaRectListener, ControlCenterCallbacks, VoiceInputHost.Gate {
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    public static final String TAG = "MainActivity";
    public static final String INTENT_MINECRAFT_VERSION = "intent_version";

    volatile public static boolean isInputStackCall;

    public static TouchCharInput touchCharInput;
    private TouchControllerInputView touchControllerInputView;
    private MinecraftGLSurface minecraftGLView;
    private static Touchpad touchpad;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private View mDrawerPullButton;
    private ControlCenterHost mControlCenter;
    private ControlEditorHost mControlEditor;
    private GameKeyboardHost mGameKeyboard;
    private VoiceInputHost mVoiceInput;
    private ScreenshotHost mScreenshot;
    private TypingPreviewHost mTypingPreview;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private FrameLayout contentFrame;

    @Nullable
    private RectF inputAreaRect;
    private int imeHeight;
    private boolean hasOngoingImeAnimation;

    MinecraftProfile minecraftProfile;

    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_RECORD_AUDIO = 1002;
    /**
     * Kept apart from {@link #REQUEST_RECORD_AUDIO}, which resumes a recording that was paused
     * mid-start. Granting the microphone to dictation resumes nothing: the button is still under
     * the player's thumb, and a dictation that began by itself on the way back from a system
     * dialog would be listening before anyone meant it to.
     */
    private static final int REQUEST_RECORD_AUDIO_VOICE = 1003;
    /** Set once the capture prompt has been explained, so it is only shown the first time. */
    private static final String PREF_KEY_CAPTURE_EXPLAINED = "recorderCaptureExplained";
    private GameRecorder mGameRecorder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        minecraftProfile = LauncherProfiles.getCurrentProfile();

        String gameDirPath = Tools.getGameDirPath(minecraftProfile).getAbsolutePath();
        MCOptionUtils.load(gameDirPath);

        Intent gameServiceIntent = new Intent(this, GameService.class);
        // Start the service a bit early
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);
        CallbackBridge.addGrabListener(touchpad);
        CallbackBridge.addGrabListener(minecraftGLView);
        CallbackBridge.addGrabListener(mTypingGrabListener);

        if (Tools.hasTouchController(new File(gameDirPath)) || LauncherPreferences.PREF_FORCE_ENABLE_TOUCHCONTROLLER) {
            TouchControllerUtils.initialize(this, touchControllerInputView);
        }

        mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);

        // Listen to IME insets animation
        ViewCompat.setWindowInsetsAnimationCallback(contentFrame, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
            @Override
            public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                    hasOngoingImeAnimation = true;
                }
            }

            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                refreshImeTranslation();
                return insets;
            }

            @Override
            public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                    hasOngoingImeAnimation = false;
                }
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(contentFrame, (v, insets) -> {
            // Only refresh translation if IME change insets itself, not the animation
            if (!hasOngoingImeAnimation) {
                imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                refreshImeTranslation();
            }
            return insets;
        });

        // Set the activity for the executor. Must do this here, or else Tools.showErrorRemote() may not
        // execute the correct method
        ContextExecutor.setActivity(this);
        //Now, attach to the service. The game will only start when this happens, to make sure that we know the right state.
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            // FIXME: is it safe for multi thread?
            GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            // Wrapped rather than replaced: the strip needs to see the characters the system
            // keyboard sends, and this is the one place all of them pass through. Returns the
            // sender itself when the preference is off, so nothing is in the way of typing.
            touchCharInput.setCharacterSender(mTypingPreview.watch(new LwjglCharSender()));

            touchControllerInputView.setInputAreaRectListener(this);

            if(minecraftProfile.pojavRendererName != null) {
                Log.i("RdrDebug","__P_renderer="+minecraftProfile.pojavRendererName);
                Tools.LOCAL_RENDERER = minecraftProfile.pojavRendererName;
                // TODO: Remove this jank when it's not relevant anymore
                // Shitty hack to make OSMZink smoothly transition into kopper
                if (minecraftProfile.pojavRendererName.equals("vulkan_zink")) Tools.LOCAL_RENDERER = "opengles3_desktopgl_zink_kopper";
            }

            setTitle("Minecraft " + minecraftProfile.lastVersionId);

            // Minecraft 1.13+

            String version = getIntent().getStringExtra(INTENT_MINECRAFT_VERSION);
            version = version == null ? minecraftProfile.lastVersionId : version;

            JMinecraftVersionList.Version mVersionInfo = Tools.getVersionInfo(version);
            isInputStackCall = mVersionInfo.arguments != null;
            CallbackBridge.nativeSetUseInputStackQueue(isInputStackCall);

            Tools.getDisplayMetrics(this);
            windowWidth = Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, 1f);
            windowHeight = Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, 1f);


            mControlCenter.setRecordingSummary(describeRecordingSettings());

            final String finalVersion = version;
            minecraftGLView.setSurfaceReadyListener(() -> {
                try {
                    // Setup virtual mouse right before launching
                    if (PREF_VIRTUAL_MOUSE_START) {
                        touchpad.post(() -> touchpad.switchState());
                    }

                    // At this time, correct size is known
                    touchControllerInputView.setSize(minecraftGLView.getWidth(), minecraftGLView.getHeight());

                    runCraft(finalVersion, mVersionInfo);
                }catch (Throwable e){
                    Tools.showErrorRemote(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        // Post to get the correct display dimensions after layout.
        LauncherPreferences.computeNotchSize(this);
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    /** Boilerplate binding */
    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        minecraftGLView = findViewById(R.id.main_game_render_view);
        touchpad = findViewById(R.id.main_touchpad);
        drawerLayout = findViewById(R.id.main_drawer_options);
        loggerView = findViewById(R.id.mainLoggerView);
        mControlLayout = findViewById(R.id.main_control_layout);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        touchControllerInputView = findViewById(R.id.touch_controller_input);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
        contentFrame = findViewById(R.id.content_frame);
        mControlCenter = new ControlCenterHost(
                findViewById(R.id.control_center),
                findViewById(R.id.control_center_pill),
                this);
        mControlEditor = new ControlEditorHost(findViewById(R.id.control_editor), mControlLayout);
        mControlLayout.setEditorHost(mControlEditor);
        mTypingPreview = new TypingPreviewHost(findViewById(R.id.typing_preview));
        mGameKeyboard = new GameKeyboardHost(findViewById(R.id.game_keyboard), mTypingPreview);
        // The sender here is deliberately not wrapped: dictation already shows its words in its own
        // overlay, and a second live copy of them at the top would be the same text twice. The
        // strip is told a dictation started instead, which is all it needs to stay honest.
        mVoiceInput = new VoiceInputHost(findViewById(R.id.voice_overlay), new VoiceInput(this),
                new LwjglCharSender(), this, mTypingPreview);
        mScreenshot = new ScreenshotHost(findViewById(R.id.screenshot_toast),
                findViewById(R.id.screenshot_shutter));
    }

    @Override
    public void onResume() {
        super.onResume();
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 1);
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1);
    }

    @Override
    protected void onPause() {
        mGyroControl.disable();
        // Before the escape below, not after: a key latched on the on-screen keyboard is still
        // down in the game, so leaving it there would send Ctrl+Esc instead of Esc — and would
        // hold that modifier for as long as the app stayed in the background.
        if(mControlCenter != null) mControlCenter.close();
        if(mGameKeyboard != null) mGameKeyboard.close();
        // The microphone belongs to whatever is in the foreground now. Dismissed rather than
        // cancelled: leaving the app is not a decision to throw away the half-sentence already
        // sitting in the chat box, and it will still be there on the way back.
        if(mVoiceInput != null) mVoiceInput.dismiss();
        if (CallbackBridge.isGrabbing()){
            sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 0);
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0);

        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onStop() {
        // The game surface is torn down when we stop being visible, and there is nothing left
        // worth capturing, so wrap up the recording instead of filling it with dead frames.
        if(mGameRecorder != null) mGameRecorder.stopIfRecording();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallbackBridge.removeGrabListener(touchpad);
        CallbackBridge.removeGrabListener(minecraftGLView);
        CallbackBridge.removeGrabListener(mTypingGrabListener);
        if(mControlCenter != null) mControlCenter.release();
        if(mGameKeyboard != null) mGameKeyboard.release();
        if(mVoiceInput != null) mVoiceInput.release();
        if(mScreenshot != null) mScreenshot.release();
        if(mTypingPreview != null) mTypingPreview.release();
        ContextExecutor.clearActivity();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(mGyroControl != null) mGyroControl.updateOrientation();
        // Layout resize is practically guaranteed on a configuration change, and `onConfigurationChanged`
        // does not implicitly start a layout. So, request a layout and expect the screen dimensions to be valid after the]
        // post.
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            // Child of mControlLayout, so refreshing size here is correct
            Tools.setFullscreen(this, setFullscreen());
            minecraftGLView.refreshSize();
            Tools.updateWindowSize(this);
            mControlLayout.refreshControlButtonPositions();
            touchControllerInputView.setSize(minecraftGLView.getWidth(), minecraftGLView.getHeight());
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(minecraftGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> minecraftGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                getRecorder().toggle(null); // consent refused, record the picture only
                return;
            }
            // The projection has to be fetched by a foreground service declaring the
            // mediaProjection type, otherwise Android 14 and up refuse to hand it over.
            RecorderService.requestProjection(this, resultCode, data, projection -> {
                if (projection == null) Log.w(TAG, "No media projection, recording without audio");
                getRecorder().toggle(projection);
            });
            return;
        }

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            // If the storage root got unmounted/unreadable we won't be able to load the file anyway,
            // and MissingStorageActivity will be started.
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runCraft(String versionId, JMinecraftVersionList.Version version) throws Throwable {
        String assetVersion;
        try {
            if (version.inheritsFrom != null) { // We are almost definitely modded if this runs
                File vanillaJsonFile = new File(Tools.DIR_HOME_VERSION + "/" + version.inheritsFrom + "/" + version.inheritsFrom + ".json");
                JMinecraftVersionList.Version vanillaJson;
                try { // Get the vanilla json from modded instance
                    vanillaJson = Tools.GLOBAL_GSON.fromJson(Tools.read(vanillaJsonFile.getAbsolutePath()), JMinecraftVersionList.Version.class);
                } catch (IOException ignored) { // Should never happen, we check for this in MinecraftDownloader().start()
                    throw new RuntimeException(getString(R.string.error_vanilla_json_corrupt));
                }
                // Something went wrong if this is somehow not the case anymore
                if (!Objects.equals(vanillaJson.assets, vanillaJson.assetIndex.id))
                    Tools.showErrorRemote(new RuntimeException(getString(R.string.error_vanilla_json_corrupt)));
                assetVersion = vanillaJson.assets;
            } else {
                // Else assume we are vanilla
                if (!Objects.equals(version.assets, version.assetIndex.id))
                    Tools.showErrorRemote(new RuntimeException(getString(R.string.error_vanilla_json_corrupt)));
                assetVersion = version.assets;
            }
       } catch (RuntimeException ignored){
            assetVersion = "legacy";
       } // If this fails.. oh well.

        // FIXME: Automatic detection should be based on provided hint GLFW_CONTEXT_VERSION_MAJOR and GLFW_CONTEXT_VERSION_MINOR
        // Autoselect renderer
        if (Tools.LOCAL_RENDERER == null) {
            // Preferably we could detect when it is modded and swap to zink however that would also
            // cover optifine and vanilla+ configurations which are relatively common, degrading their
            // experience for no reason. We will compromise with just having users do it themselves.
            Tools.LOCAL_RENDERER = "opengles2";
            // MobileGlues becomes available post 1.17. It has superior compatibility with mods
            // while having fairly similar performance compared to GL4ES-based forks.
            if(assetVersion.matches("\\d+") || // Should match all digits, which is the modern assetVersioning
               "1.17".equals(assetVersion) ||
               "1.18".equals(assetVersion) ||
               "1.19".equals(assetVersion) ||
                // Angelica gives us GL3.3core on 1.7.10, it's a unique case.
                hasMods("angelica")) Tools.LOCAL_RENDERER = "opengles_mobileglues";
        }
        if(!Tools.checkRendererCompatible(this, Tools.LOCAL_RENDERER)) {
            Tools.RenderersList renderersList = Tools.getCompatibleRenderers(this);
            String firstCompatibleRenderer = renderersList.rendererIds.get(0);
            Log.w("runCraft","Incompatible renderer "+Tools.LOCAL_RENDERER+ " will be replaced with "+firstCompatibleRenderer);
            Tools.LOCAL_RENDERER = firstCompatibleRenderer;
            runOnUiThread(() -> Toast.makeText(this, R.string.autorendererselectfailed, Toast.LENGTH_LONG).show());
            Tools.releaseRenderersCache();
        }

        // MCL-3732 Mitigation
        // I don't trust the bug tracker. 'server-resource-pack" was removed in 1.20.3-pre3
        // so we use 12 to detect that. We still generate till 1.20.5 else we don't cover
        // 1.20.3-pre2 and such. Better to over than to under.
        File folder = new File(Tools.getGameDirPath(minecraftProfile), "server-resource-pack");
        try {
            if (Integer.parseInt(assetVersion) <= 12) folder.mkdir();
        } catch (NumberFormatException e) { folder.mkdir(); }

        MinecraftAccount minecraftAccount = PojavProfile.getCurrentProfileContent(this, null);
        if (hasMods("sodium"))
            Logger.appendToLog("WARNING: Sodium is being used, Amethyst-Android does NOT support this mod, you are on your own");
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
        Tools.printLauncherInfo(versionId, Tools.isValidString(minecraftProfile.javaArgs) ? minecraftProfile.javaArgs : LauncherPreferences.PREF_CUSTOM_JAVA_ARGS, Tools.getTotalDeviceMemory(this));
        if(Tools.LOCAL_RENDERER.equals("opengles_mobileglues")) {
            LauncherPreferences.writeMGRendererSettings();
        }
        JREUtils.redirectAndPrintJRELog();
        LauncherProfiles.load();
        int requiredJavaVersion = 8;
        if(version.javaVersion != null) requiredJavaVersion = version.javaVersion.majorVersion;
        Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, versionId, requiredJavaVersion);
        //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    public void setmLastIndex(int a){
        mHotbarView.setmLastIndex(a);
    }

    boolean isInEditor;
    private void openCustomControls() {
        mControlLayout.setModifiable(true);
        mControlCenter.setEditorMode(true);
        // The pull tab is the only way back into the menu while editing, so it always shows.
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    /**
     * Starts or stops recording the gameplay straight out of the renderer.
     * <p>
     * Starting takes a detour through the microphone permission and the screen capture consent,
     * both of which are needed to capture the game's own audio. Either one being refused only
     * costs the sound, the video is recorded regardless.
     */
    private void toggleRecording() {
        if(getRecorder().isRecording()) {
            // The service is released from the listener, once the tail of the audio has been
            // flushed and the file is closed.
            getRecorder().stopIfRecording();
            return;
        }
        publishPointerToRecorder();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !getRecorder().wantsAudio()) {
            // Playback capture needs Android 10, and there is no reason to prompt for it when
            // audio recording is switched off in the settings.
            getRecorder().toggle(null);
            return;
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return; // resumed from onRequestPermissionsResult
        }
        requestProjectionAndRecord();
    }

    /**
     * Hands the recorder the pointer artwork and its current state.
     * <p>
     * The virtual mouse is an Android view drawn above the game's surface, so it is absent from
     * the frames the recorder copies out of the renderer and has to be composited back in.
     */
    private void publishPointerToRecorder() {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            // Straight alpha, so the blend in the recorder matches how this is drawn on screen.
            options.inPremultiplied = false;
            GameRecorder.setPointerBitmap(BitmapFactory.decodeResource(getResources(),
                    R.drawable.ic_mouse_pointer, options));
        } catch (Throwable t) {
            Log.w(TAG, "Could not read the pointer artwork, recording without it", t);
        }
        float scale = LauncherPreferences.PREF_MOUSESCALE * LauncherPreferences.PREF_SCALE_FACTOR;
        GameRecorder.setPointerState(touchpad != null && touchpad.getDisplayState(),
                36 * scale, 54 * scale);
    }

    private void requestProjectionAndRecord() {
        // Android 14 added a choice between the whole screen and a single app to the capture
        // prompt. Picking a single app scopes the projection to that app's task, and the game's
        // audio does not come through, so explain the choice once before the system asks.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && !LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_KEY_CAPTURE_EXPLAINED, false)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.control_recording_capture_prompt_title)
                    .setMessage(R.string.control_recording_capture_prompt_message)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> getRecorder().toggle(null))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        LauncherPreferences.DEFAULT_PREF.edit()
                                .putBoolean(PREF_KEY_CAPTURE_EXPLAINED, true).apply();
                        launchCaptureConsent();
                    })
                    .show();
            return;
        }
        launchCaptureConsent();
    }

    private void launchCaptureConsent() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if(manager == null) {
            getRecorder().toggle(null);
            return;
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        } catch (Throwable t) {
            Log.w(TAG, "Could not ask for screen capture consent, recording without audio", t);
            getRecorder().toggle(null);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Nothing to resume: see REQUEST_RECORD_AUDIO_VOICE. Pressing the button again now works.
        if(requestCode == REQUEST_RECORD_AUDIO_VOICE) return;
        if(requestCode != REQUEST_RECORD_AUDIO) return;
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestProjectionAndRecord();
        } else {
            // No microphone permission means no playback capture, so record the picture only.
            getRecorder().toggle(null);
        }
    }

    private GameRecorder getRecorder() {
        if(mGameRecorder == null) {
            File recordingsDir = RecorderPreferences.recordingsDirectory(Tools.getGameDirPath(minecraftProfile));
            mGameRecorder = new GameRecorder(this, recordingsDir, minecraftProfile.lastVersionId,
                    new GameRecorder.Listener() {
                @Override
                public void onRecordingStarted() {
                    refreshRecordingState();
                    Toast.makeText(MainActivity.this, R.string.control_recording_started, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onRecordingStopped(@NonNull File output) {
                    refreshRecordingState();
                    RecorderService.release(MainActivity.this);
                    Toast.makeText(MainActivity.this, getString(R.string.control_recording_saved, output.getAbsolutePath()), Toast.LENGTH_LONG).show();
                    // On its own thread, and after the player has been told the clip is saved: a
                    // clip can be gigabytes and the copy must not be what stands between them and
                    // the game coming back.
                    GalleryExport.publishVideoLater(MainActivity.this, output);
                }

                @Override
                public void onRecordingFailed(@NonNull String reason) {
                    refreshRecordingState();
                    RecorderService.release(MainActivity.this);
                    Toast.makeText(MainActivity.this, getString(R.string.control_recording_failed, reason), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onRecordingTruncated(@NonNull String reason) {
                    Toast.makeText(MainActivity.this, getString(R.string.control_recording_truncated, reason), Toast.LENGTH_LONG).show();
                }
            });
        }
        return mGameRecorder;
    }

    /**
     * Resolution, frame rate and audio as the settings currently have them.
     * Shown on the idle recording card, so what a recording will be is answerable without leaving
     * the game to go and look.
     */
    /** Hand the control center whichever state the recorder just moved into. */
    private void refreshRecordingState() {
        if(mControlCenter == null) return;
        String summary = describeRecordingSettings();
        if(mGameRecorder != null && mGameRecorder.isRecording())
            mControlCenter.onRecordingStarted(summary, GameRecorder.getMaxOutputBytes());
        else
            mControlCenter.onRecordingStopped(summary);
    }

    private String describeRecordingSettings() {
        RecorderPreferences preferences = RecorderPreferences.load(this);
        String audio = preferences.describeAudio();
        if(audio == null) audio = getString(R.string.control_center_no_audio);
        return getString(R.string.control_center_summary,
                preferences.describeResolution(this), preferences.frameRate, audio);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    minecraftGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }

                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    public static void toggleMouse(Context ctx) {
        if (CallbackBridge.isGrabbing()) return;

        Toast.makeText(ctx, touchpad.switchState()
                        ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The control center is a view rather than a dialog, so nothing closes it on back unless
        // this does; without it the key would fall through and pause the game behind an open sheet.
        if(mControlCenter != null && mControlCenter.isOpen()
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if(event.getAction() == KeyEvent.ACTION_UP) mControlCenter.close();
            return true;
        }
        // Same reasoning for the two surfaces that can be up without it. Dictation is taken first:
        // when both are showing, back means "never mind that sentence", not "put the keyboard
        // away" — and back is the one gesture that undoes what was dictated rather than keeping it.
        if(event.getKeyCode() == KeyEvent.KEYCODE_BACK && mVoiceInput != null
                && mVoiceInput.isListening()) {
            if(event.getAction() == KeyEvent.ACTION_UP) mVoiceInput.cancel();
            return true;
        }
        if(event.getKeyCode() == KeyEvent.KEYCODE_BACK && mGameKeyboard != null
                && mGameKeyboard.isOpen()) {
            if(event.getAction() == KeyEvent.ACTION_UP) mGameKeyboard.close();
            return true;
        }
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() != KeyEvent.ACTION_DOWN) return true;
                // One layer at a time — the key picker, then the panel — before back means
                // "leave the editor", which is the order someone deep in a button expects.
                if(mControlEditor != null && mControlEditor.isOpen()) mControlEditor.dismissLayer();
                else mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = minecraftGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState() {
        if(touchCharInput != null) touchCharInput.switchKeyboardState();
    }

    @Keep
    public static void openLink(String link) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                if(link.startsWith("file:")) {
                    int truncLength = 5;
                    if(link.startsWith("file://")) truncLength = 7;
                    String path = link.substring(truncLength);
                    Tools.openPath(ctx, new File(path), false);
                }else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(link), "*/*");
                    ctx.startActivity(intent);
                }
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @SuppressWarnings("unused") //TODO: actually use it
    public static void openPath(String path) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Tools.openPath(ctx, new File(path), false);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @Keep
    public static void querySystemClipboard() {
        Tools.runOnUiThread(()->{
            ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
            if(clipData == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            ClipData.Item firstClipItem = clipData.getItemAt(0);
            //TODO: coerce to HTML if the clip item is styled
            CharSequence clipItemText = firstClipItem.getText();
            if(clipItemText == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
        });
    }

    @Keep
    public static void putClipboardData(String data, String mimeType) {
        Tools.runOnUiThread(()-> {
            ClipData clipData = null;
            switch(mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
            }
            if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
        });
    }

    @Override
    public void onClickedMenu() {
        mControlCenter.setRecordingSummary(describeRecordingSettings());
        mControlCenter.applyShutterOn(mScreenshot.isShutterVisible());
        mControlCenter.open();
    }

    @Override
    public void onClickedGameKeyboard() {
        // Closed rather than left underneath: the keyboard is a shortcut past the control center,
        // so having pressed it there is no reason for the sheet to still be there afterwards.
        mControlCenter.close();
        mGameKeyboard.open();
    }

    @Override
    public void onClickedVoice(boolean down) {
        if(down) mVoiceInput.onButtonDown();
        else mVoiceInput.onButtonUp();
    }

    @Override
    public void onVoiceShortcut(boolean down) {
        mVoiceInput.onShortcut(down);
    }

    /**
     * A screenshot from a button, with the control center left alone.
     *
     * Nothing is closed and nothing is opened: the sheet is not up when a control button is
     * pressed, and the point of binding one is that a picture costs a single tap and does not
     * take the game away from you while it is happening.
     */
    @Override
    public void onClickedScreenshot() {
        mScreenshot.take();
    }

    /* Voice typing's two questions about the rest of the game. */

    @Override
    public int voiceBlockedReason() {
        // One microphone, and the recorder took it first. Refusing out loud beats both of them
        // getting a broken stream, which is what fighting over the device actually produces.
        if(mGameRecorder != null && mGameRecorder.isCapturingMicrophone())
            return R.string.voice_error_recorder_microphone;
        if(!VoiceInput.isAvailable(this)) return R.string.voice_error_unavailable;
        return 0;
    }

    @Override
    public boolean ensureMicrophonePermission() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_VOICE);
        return false;
    }

    /* Control center actions. Every one of these already existed; only the way in has changed. */

    @Override public void onToggleRecording() { mControlCenter.close(); toggleRecording(); }

    /**
     * Take one now, before the sheet is told to close.
     *
     * The sheet is a Compose overlay above the game surface and was never in the frame the
     * renderer draws, so the picture is clean either way; going first only means the moment
     * captured is the one that was tapped rather than 300ms of exit animation later.
     */
    @Override
    public void onScreenshot() {
        mScreenshot.take();
        mControlCenter.close();
    }

    /**
     * Hand over the shutter and get out of the way.
     *
     * The sheet stays open, because this is a switch and the answer to flipping a switch is
     * seeing it flip. The shutter is behind the scrim until the sheet is dismissed, which is the
     * moment it becomes useful anyway.
     */
    @Override
    public void onToggleShutter() {
        mScreenshot.toggleShutter();
        mControlCenter.applyShutterOn(mScreenshot.isShutterVisible());
    }

    @Override public void onCustomControls() { mControlCenter.close(); openCustomControls(); }
    @Override public void onSendKeycode() { mControlCenter.close(); mGameKeyboard.open(); }
    @Override public void onQuickSettings() { mControlCenter.close(); openQuickSettings(); }
    @Override public void onLogOutput() { mControlCenter.close(); openLogOutput(); }
    @Override public void onForceClose() { mControlCenter.close(); dialogForceClose(this); }

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
        mControlLayout.openExitDialog(this);
    }

    @Override
    public long recordingElapsedMs() {
        return mGameRecorder == null ? 0L : mGameRecorder.getElapsedMs();
    }

    @Override
    public long recordingBytes() {
        return mGameRecorder == null ? 0L : mGameRecorder.getBytesWritten();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (IOException e) {
            Tools.showError(this,e);
        }

        mControlCenter.setEditorMode(false);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        minecraftGLView.start(localBinder.isActive, touchpad);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch the the captured mouse events as trackball events
     * due to a bug(?) somewhere(????)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        // On my device, the mouse sends events as a relative mouse device.
        // Not comparing with == here because apparently `eventSource` is a mask that can
        // sometimes indicate multiple sources, like in the case of InputDevice.SOURCE_TOUCHPAD
        // (which is *also* an InputDevice.SOURCE_MOUSE when controlling a cursor)
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return minecraftGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            Tools.setFullscreen(this, setFullscreen());
        }
        super.onWindowFocusChanged(hasFocus);
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, hasFocus ? 1 : 0);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void updateInputAreaRect(@Nullable RectF rect) {
        inputAreaRect = rect;
        refreshImeTranslation();
    }

    private void refreshImeTranslation() {
        int translation = imeTranslation();
        contentFrame.setTranslationY(translation);
        if (mTypingPreview != null) {
            // The typing strip is a child of the frame that just moved, and it is the one thing in
            // it that must not move: it exists because the pan carries the text field off the top
            // of the screen, so a strip that panned with it would leave at the moment it was
            // wanted. Cancelling the frame's own translation pins it to the top of the window.
            mTypingPreview.applyPan(translation);
            mTypingPreview.applyImeOpen(imeHeight != 0);
        }
    }

    /** How far up the game has to move so the keyboard is not sitting on the text field. */
    private int imeTranslation() {
        if (imeHeight == 0) return 0;

        int inputAreaBottom;
        if (inputAreaRect != null) {
            inputAreaBottom = (int) inputAreaRect.bottom;
        } else if (LauncherPreferences.PREF_KEYBOARD_PANNING) {
            inputAreaBottom = contentFrame.getHeight();
        } else {
            return 0;
        }

        int bottomDistance = contentFrame.getHeight() - inputAreaBottom;
        return -Math.max(imeHeight - bottomDistance, 0);
    }

    /**
     * Kept as a field rather than implemented by the activity, which already stands for three
     * other interfaces, and so it can be handed back to {@link CallbackBridge} by reference.
     */
    private final GrabListener mTypingGrabListener = new GrabListener() {
        @Override
        public void onGrabState(boolean isGrabbing) {
            if (mTypingPreview != null) mTypingPreview.applyGrab(isGrabbing);
        }
    };
}
