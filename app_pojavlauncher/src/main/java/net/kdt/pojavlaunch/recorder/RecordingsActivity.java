package net.kdt.pojavlaunch.recorder;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lists the clips taken by the in-game recorder, newest first, and lets them be played, shared
 * or deleted. Playback and sharing go through the launcher's existing document provider, so no
 * copy of the file is ever made.
 */
public class RecordingsActivity extends BaseActivity {
    private ListView mListView;
    private TextView mEmptyView;
    private final List<File> mRecordings = new ArrayList<>();
    private ArrayAdapter<String> mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);
        setTitle(R.string.preference_recorder_title);

        mListView = findViewById(R.id.recordings_list);
        mEmptyView = findViewById(R.id.recordings_empty);

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, new ArrayList<>()) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                File recording = mRecordings.get(position);
                ((TextView) view.findViewById(android.R.id.text2)).setText(describe(recording));
                return view;
            }
        };
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) ->
                play(mRecordings.get(position)));
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            showActions(mRecordings.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        mRecordings.clear();
        File directory = recordingsDirectory();
        File[] files = directory == null ? null : directory.listFiles(
                (dir, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files != null) {
            List<File> found = new ArrayList<>(Arrays.asList(files));
            // Newest first, which is almost always the one being looked for.
            // Collections.sort rather than List.sort, which needs API 24.
            Collections.sort(found, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
            mRecordings.addAll(found);
        }

        mAdapter.clear();
        for (File recording : mRecordings) mAdapter.add(recording.getName());
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

    private String describe(File recording) {
        String size = Formatter.formatShortFileSize(this, recording.length());
        CharSequence when = DateUtils.getRelativeTimeSpanString(recording.lastModified(),
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return size + " • " + when;
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
}
