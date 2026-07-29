package net.kdt.pojavlaunch.recorder;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.NotificationUtils;

/**
 * Holds the {@link MediaProjection} used to capture the game's audio.
 * <p>
 * Android 14 refuses to hand out a media projection unless a foreground service declaring the
 * mediaProjection type is already running, so the projection is acquired here rather than in the
 * activity. This is deliberately a service of its own: GameService runs for the whole session and
 * giving it the mediaProjection type would make the platform demand a projection every time the
 * game starts.
 */
public class RecorderService extends Service {
    private static final String TAG = "RecorderService";
    private static final String EXTRA_RESULT_CODE = "resultCode";
    private static final String EXTRA_RESULT_DATA = "resultData";

    /** Delivered once, on the main thread, with null meaning "carry on without audio". */
    public interface ProjectionListener {
        void onProjectionReady(@Nullable MediaProjection projection);
    }

    /** Same process as the service, cleared as soon as it has been called. */
    @Nullable private static ProjectionListener sListener;

    @Nullable private MediaProjection mProjection;

    /**
     * Bring the service up and hand the consent result to it. The listener is called once the
     * projection is available, or with null if it could not be obtained.
     */
    public static void requestProjection(Context context, int resultCode, Intent resultData,
                                         ProjectionListener listener) {
        sListener = listener;
        Intent intent = new Intent(context, RecorderService.class)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start the recorder service", t);
            deliver(null);
        }
    }

    public static void release(Context context) {
        sListener = null;
        try {
            context.stopService(new Intent(context, RecorderService.class));
        } catch (Throwable t) {
            Log.w(TAG, "Could not stop the recorder service", t);
        }
    }

    private static void deliver(@Nullable MediaProjection projection) {
        ProjectionListener listener = sListener;
        sListener = null;
        if (listener != null) listener.onProjectionReady(projection);
    }

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle(getString(R.string.control_recording_started))
                .setSmallIcon(R.drawable.notif_icon)
                .setNotificationSilent()
                .build();
        try {
            // The type has to be claimed here, before the projection is asked for.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationUtils.NOTIFICATION_ID_RECORDER_SERVICE, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NotificationUtils.NOTIFICATION_ID_RECORDER_SERVICE, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not go foreground, recording without audio", t);
            deliver(null);
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjection projection = null;
        try {
            Intent resultData = intent == null ? null : intent.getParcelableExtra(EXTRA_RESULT_DATA);
            int resultCode = intent == null ? 0 : intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            if (resultData != null) {
                MediaProjectionManager manager =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                projection = manager.getMediaProjection(resultCode, resultData);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not obtain the media projection, recording without audio", t);
        }

        mProjection = projection;
        deliver(projection);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mProjection != null) {
            try {
                mProjection.stop();
            } catch (Throwable t) {
                Log.w(TAG, "Could not stop the media projection", t);
            }
            mProjection = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
