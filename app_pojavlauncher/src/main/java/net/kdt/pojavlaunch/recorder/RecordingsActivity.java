package net.kdt.pojavlaunch.recorder;

import android.app.AlertDialog;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
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
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The gallery of clips taken by the in-game recorder: preview, play, share and delete, with
 * ordering and per-version filtering.
 * <p>
 * Playback and sharing go through the launcher's existing document provider, so no copy of the
 * file is ever made.
 */
public class RecordingsActivity extends BaseActivity {
    /** Matches the timestamp GameRecorder names its files with. */
    private static final SimpleDateFormat FILENAME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    private static final SimpleDateFormat DISPLAY_FORMAT =
            new SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault());

    private static final int SORT_NEWEST = 0;
    private static final int SORT_OLDEST = 1;
    private static final int SORT_LARGEST = 2;
    private static final int SORT_LONGEST = 3;

    /** Bounded so a large gallery cannot grow the heap without limit. */
    private static final int MAX_CACHED_THUMBNAILS = 60;

    private ListView mListView;
    private View mEmptyView;
    private TextView mSummaryView;
    private RecordingAdapter mAdapter;

    /** Everything on disk, and the subset currently shown. */
    private final List<File> mAllRecordings = new ArrayList<>();
    private final List<File> mRecordings = new ArrayList<>();
    private int mSortOrder = SORT_NEWEST;
    @Nullable private String mVersionFilter;

    /**
     * Details that have to be read off the files themselves. Concurrent because the adapter reads
     * them on the UI thread while the background thread fills them in.
     */
    private final Map<String, Long> mDurationCache = new ConcurrentHashMap<>();
    private final Map<String, Bitmap> mThumbnailCache = new ConcurrentHashMap<>();
    private final Map<String, RecordingInfo> mInfoCache = new ConcurrentHashMap<>();
    // newSetFromMap rather than ConcurrentHashMap.newKeySet(), which needs API 24.
    private final Set<String> mPendingMetadata =
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
        findViewById(R.id.recordings_sort_button).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.recordings_filter_button).setOnClickListener(v -> showFilterDialog());

        mListView = findViewById(R.id.recordings_list);
        mEmptyView = findViewById(R.id.recordings_empty);
        mSummaryView = findViewById(R.id.recordings_summary);

        mAdapter = new RecordingAdapter();
        mListView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        mMetadataExecutor.shutdownNow();
        mThumbnailCache.clear();
        super.onDestroy();
    }

    /** Rereads the folder, then reapplies the current ordering and filter. */
    private void reload() {
        mAllRecordings.clear();
        File directory = recordingsDirectory();
        File[] files = directory == null ? null : directory.listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (files != null) mAllRecordings.addAll(Arrays.asList(files));
        applySortAndFilter();
    }

    private void applySortAndFilter() {
        mRecordings.clear();
        for (File recording : mAllRecordings) {
            if (mVersionFilter == null || mVersionFilter.equals(versionOf(recording)))
                mRecordings.add(recording);
        }
        // Collections.sort rather than List.sort, which needs API 24.
        Collections.sort(mRecordings, comparatorFor(mSortOrder));
        mAdapter.notifyDataSetChanged();

        long totalBytes = 0;
        for (File recording : mRecordings) totalBytes += recording.length();
        mSummaryView.setText(getString(R.string.recordings_count, mRecordings.size(),
                Formatter.formatShortFileSize(this, totalBytes)));

        boolean empty = mRecordings.isEmpty();
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        mListView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private Comparator<File> comparatorFor(int order) {
        switch (order) {
            case SORT_OLDEST:
                return (left, right) -> Long.compare(left.lastModified(), right.lastModified());
            case SORT_LARGEST:
                return (left, right) -> Long.compare(right.length(), left.length());
            case SORT_LONGEST:
                return (left, right) -> {
                    // Clips whose duration is not known yet fall back to size, which tracks it
                    // closely enough at a fixed bitrate to keep the order stable.
                    Long leftDuration = mDurationCache.get(cacheKey(left));
                    Long rightDuration = mDurationCache.get(cacheKey(right));
                    if (leftDuration != null && rightDuration != null)
                        return Long.compare(rightDuration, leftDuration);
                    return Long.compare(right.length(), left.length());
                };
            case SORT_NEWEST:
            default:
                return (left, right) -> Long.compare(right.lastModified(), left.lastModified());
        }
    }

    private void showSortDialog() {
        CharSequence[] options = {
                getString(R.string.recordings_sort_newest),
                getString(R.string.recordings_sort_oldest),
                getString(R.string.recordings_sort_largest),
                getString(R.string.recordings_sort_longest)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.recordings_sort_title)
                .setSingleChoiceItems(options, mSortOrder, (dialog, which) -> {
                    mSortOrder = which;
                    applySortAndFilter();
                    dialog.dismiss();
                })
                .show();
    }

    private void showFilterDialog() {
        // Only versions that are actually present, so the list never offers an empty result.
        List<String> versions = new ArrayList<>(new LinkedHashSet<>(versionsPresent()));
        Collections.sort(versions);

        CharSequence[] options = new CharSequence[versions.size() + 1];
        options[0] = getString(R.string.recordings_filter_all);
        for (int i = 0; i < versions.size(); i++) options[i + 1] = versions.get(i);

        int checked = mVersionFilter == null ? 0 : versions.indexOf(mVersionFilter) + 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.recordings_filter_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    mVersionFilter = which == 0 ? null : versions.get(which - 1);
                    applySortAndFilter();
                    dialog.dismiss();
                })
                .show();
    }

    private List<String> versionsPresent() {
        List<String> versions = new ArrayList<>();
        for (File recording : mAllRecordings) {
            String version = versionOf(recording);
            if (version != null && !versions.contains(version)) versions.add(version);
        }
        return versions;
    }

    @Nullable
    private String versionOf(File recording) {
        return infoOf(recording).minecraftVersion;
    }

    /** Sidecars are small, so they are read on demand and kept for the life of the screen. */
    @NonNull
    private RecordingInfo infoOf(File recording) {
        String key = cacheKey(recording);
        RecordingInfo info = mInfoCache.get(key);
        if (info == null) {
            info = RecordingInfo.read(recording);
            mInfoCache.put(key, info);
        }
        return info;
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
        return Formatter.formatShortFileSize(this, recording.length()) + " · "
                + DateUtils.getRelativeTimeSpanString(recording.lastModified(),
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    /** The chip line: what the clip was recorded from and at what quality. */
    @Nullable
    private String buildDetails(File recording) {
        RecordingInfo info = infoOf(recording);
        List<String> parts = new ArrayList<>(4);
        if (info.minecraftVersion != null) parts.add(info.minecraftVersion);
        if (info.width > 0 && info.height > 0) parts.add(info.width + "×" + info.height);
        if (info.frameRate > 0) parts.add(info.frameRate + " FPS");
        if (info.audio != null) parts.add(info.audio);
        return parts.isEmpty() ? null : TextUtils.join(" · ", parts);
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

    /** Reads the duration and a preview frame off a clip, once per file, on a background thread. */
    private void loadMetadataAsync(File recording) {
        String key = cacheKey(recording);
        if (mDurationCache.containsKey(key) || !mPendingMetadata.add(key)) return;

        mMetadataExecutor.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Long duration = null;
            Bitmap thumbnail = null;
            try {
                retriever.setDataSource(recording.getAbsolutePath());
                String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) duration = Long.parseLong(value);
                // A frame a moment in, rather than the very first, which is often still a fade in.
                long at = duration != null ? Math.min(1_000_000L, duration * 500L) : 0L;
                thumbnail = retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Throwable t) {
                // A clip we cannot read still plays; it just shows without a preview.
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {}
            }

            if (duration != null) mDurationCache.put(key, duration);
            if (thumbnail != null) {
                // Oldest entries are not tracked individually; clearing wholesale is enough to
                // keep a very large folder from growing the heap without bound.
                if (mThumbnailCache.size() >= MAX_CACHED_THUMBNAILS) mThumbnailCache.clear();
                mThumbnailCache.put(key, thumbnail);
            }
            mPendingMetadata.remove(key);
            if (duration != null || thumbnail != null) {
                runOnUiThread(() -> {
                    if (!isFinishing()) mAdapter.notifyDataSetChanged();
                });
            }
        });
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
                    // Otherwise the details would outlive the clip they describe.
                    RecordingInfo.delete(recording);
                    mInfoCache.remove(cacheKey(recording));
                    reload();
                })
                .show();
    }

    private static class ViewHolder {
        final View root;
        final ImageView preview;
        final ImageView badge;
        final TextView duration;
        final TextView title;
        final TextView subtitle;
        final TextView version;
        final ImageButton overflow;

        ViewHolder(View root) {
            this.root = root;
            preview = root.findViewById(R.id.recording_preview);
            badge = root.findViewById(R.id.recording_badge);
            duration = root.findViewById(R.id.recording_duration);
            title = root.findViewById(R.id.recording_title);
            subtitle = root.findViewById(R.id.recording_subtitle);
            version = root.findViewById(R.id.recording_version);
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
            String key = cacheKey(recording);

            holder.title.setText(formatTitle(recording));
            holder.subtitle.setText(buildSubtitle(recording));

            String details = buildDetails(recording);
            holder.version.setText(details);
            holder.version.setVisibility(details == null ? View.GONE : View.VISIBLE);

            Bitmap thumbnail = mThumbnailCache.get(key);
            holder.preview.setImageBitmap(thumbnail);
            // The badge carries the row on its own until a preview is there to sit on.
            holder.badge.setAlpha(thumbnail == null ? 0.5f : 0.85f);

            Long durationMs = mDurationCache.get(key);
            holder.duration.setText(durationMs == null ? "" : formatDuration(durationMs));
            holder.duration.setVisibility(durationMs == null ? View.GONE : View.VISIBLE);

            holder.root.setOnClickListener(v -> play(recording));
            holder.overflow.setOnClickListener(v -> showActions(recording));

            loadMetadataAsync(recording);
            return convertView;
        }
    }
}
