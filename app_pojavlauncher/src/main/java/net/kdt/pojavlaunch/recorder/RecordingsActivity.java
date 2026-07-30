package net.kdt.pojavlaunch.recorder;

import android.app.AlertDialog;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists the clips taken by the in-game recorder, newest first, and lets them be played, shared
 * or deleted. Playback and sharing go through the launcher's existing document provider, so no
 * copy of the file is ever made.
 */
public class RecordingsActivity extends BaseActivity {
    /** Matches the timestamp GameRecorder names its files with. */
    private static final SimpleDateFormat FILENAME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault());

    private ListView mListView;
    private View mEmptyView;
    private RecordingAdapter mAdapter;
    private final List<File> mRecordings = new ArrayList<>();

    /**
     * Video duration is metadata that has to be read off the file, so it is cached once found.
     * Concurrent, because the adapter reads these on the UI thread while the metadata thread
     * writes them.
     */
    private final Map<String, Long> mDurationCache = new ConcurrentHashMap<>();
    // newSetFromMap rather than ConcurrentHashMap.newKeySet(), which needs API 24.
    private final Set<String> mPendingDurations =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ExecutorService mMetadataExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RecordingsActivity-metadata");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        findViewById(R.id.recordings_back_button).setOnClickListener(v -> finish());

        mListView = findViewById(R.id.recordings_list);
        mEmptyView = findViewById(R.id.recordings_empty);

        mAdapter = new RecordingAdapter();
        mListView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        mMetadataExecutor.shutdownNow();
        super.onDestroy();
    }

    private void refresh() {
        mRecordings.clear();
        File directory = recordingsDirectory();
        File[] files = directory == null ? null : directory.listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (files != null) {
            List<File> found = new ArrayList<>(Arrays.asList(files));
            // Newest first, which is almost always the one being looked for.
            // Collections.sort rather than List.sort, which needs API 24.
            Collections.sort(found, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
            mRecordings.addAll(found);
        }
        mAdapter.notifyDataSetChanged();

        boolean empty = mRecordings.isEmpty();
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * Recordings live next to the world data of the profile that produced them, so the folder is
     * resolved the same way the recorder resolves it.
     */
    @Nullable
    private File recordingsDirectory() {
        try {
            LauncherProfiles.load();
            MinecraftProfile profile = LauncherProfiles.getCurrentProfile();
            return RecorderPreferences.recordingsDirectory(Tools.getGameDirPath(profile));
        } catch (Throwable t) {
            return null;
        }
    }

    /** The recorder names files after the moment recording started; show that in a friendly form. */
    private static String formatTitle(File recording) {
        String stem = recording.getName().replace(".mp4", "");
        try {
            Date recordedAt;
            synchronized (FILENAME_FORMAT) {
                recordedAt = FILENAME_FORMAT.parse(stem);
            }
            if (recordedAt != null) {
                synchronized (DISPLAY_FORMAT) {
                    return DISPLAY_FORMAT.format(recordedAt);
                }
            }
        } catch (ParseException ignored) {
            // Fall through to the raw name below, e.g. for a file dropped in by hand.
        }
        return stem;
    }

    private String buildSubtitle(File recording) {
        List<String> parts = new ArrayList<>(3);
        Long durationMs = mDurationCache.get(cacheKey(recording));
        if (durationMs != null) parts.add(formatDuration(durationMs));
        parts.add(Formatter.formatShortFileSize(this, recording.length()));
        parts.add(DateUtils.getRelativeTimeSpanString(recording.lastModified(),
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString());
        return TextUtils.join(" · ", parts);
    }

    private static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private static String cacheKey(File recording) {
        return recording.getAbsolutePath() + "@" + recording.lastModified();
    }

    /** Kicks off a background read of the clip's duration, once per file, and refreshes on success. */
    private void loadDurationAsync(File recording) {
        String key = cacheKey(recording);
        if (mDurationCache.containsKey(key) || !mPendingDurations.add(key)) return;

        mMetadataExecutor.execute(() -> {
            Long duration = extractDuration(recording);
            mPendingDurations.remove(key);
            if (duration != null) {
                mDurationCache.put(key, duration);
                runOnUiThread(() -> {
                    if (!isFinishing()) mAdapter.notifyDataSetChanged();
                });
            }
        });
    }

    @Nullable
    private static Long extractDuration(File recording) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(recording.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value != null ? Long.parseLong(value) : null;
        } catch (Throwable t) {
            return null; // a clip we cannot read metadata for still plays fine, just without a duration
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }
    }

    private void play(File recording) {
        try {
            Tools.openPath(this, recording, false);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.recordings_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showActions(File recording) {
        new AlertDialog.Builder(this)
                .setTitle(recording.getName())
                .setItems(new CharSequence[]{
                        getString(R.string.recordings_action_play),
                        getString(R.string.recordings_action_share),
                        getString(R.string.recordings_action_delete)
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: play(recording); break;
                        case 1: share(recording); break;
                        case 2: confirmDelete(recording); break;
                    }
                })
                .show();
    }

    private void share(File recording) {
        try {
            Tools.openPath(this, recording, true);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.recordings_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(File recording) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.recordings_action_delete)
                .setMessage(getString(R.string.recordings_delete_confirm, recording.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!recording.delete())
                        Toast.makeText(this, R.string.recordings_delete_failed, Toast.LENGTH_LONG).show();
                    refresh();
                })
                .show();
    }

    private static class ViewHolder {
        final View root;
        final TextView title;
        final TextView subtitle;
        final ImageButton overflow;

        ViewHolder(View root) {
            this.root = root;
            title = root.findViewById(R.id.recording_title);
            subtitle = root.findViewById(R.id.recording_subtitle);
            overflow = root.findViewById(R.id.recording_overflow);
        }
    }

    private class RecordingAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mRecordings.size();
        }

        @Override
        public File getItem(int position) {
            return mRecordings.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_recording, parent, false);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            File recording = getItem(position);
            holder.title.setText(formatTitle(recording));
            holder.subtitle.setText(buildSubtitle(recording));
            holder.root.setOnClickListener(v -> play(recording));
            holder.overflow.setOnClickListener(v -> showActions(recording));

            loadDurationAsync(recording);
            return convertView;
        }
    }
}
