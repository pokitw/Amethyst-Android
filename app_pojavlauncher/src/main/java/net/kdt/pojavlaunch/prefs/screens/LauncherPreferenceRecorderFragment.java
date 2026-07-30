package net.kdt.pojavlaunch.prefs.screens;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.recorder.RecorderPreferences;
import net.kdt.pojavlaunch.recorder.RecordingsActivity;

import java.io.File;
import java.util.Locale;

/**
 * Settings for the in-game recorder, plus the way into the clips it has produced.
 */
public class LauncherPreferenceRecorderFragment extends LauncherPreferenceFragment {
    private Preference mEstimatePreference;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_recorder);

        requirePreference("viewRecordings").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), RecordingsActivity.class));
            return true;
        });

        mEstimatePreference = requirePreference("recorderEstimate");

        CustomSeekBarPreference videoBitrate = requirePreference(
                RecorderPreferences.KEY_VIDEO_BITRATE, CustomSeekBarPreference.class);
        videoBitrate.setSuffix(" Mbps");

        // The source and bitrate only mean anything while audio is actually being captured.
        SwitchPreference captureAudio = requirePreference(
                RecorderPreferences.KEY_CAPTURE_AUDIO, SwitchPreference.class);
        Preference audioBitrate = requirePreference(RecorderPreferences.KEY_AUDIO_BITRATE);
        Preference audioSource = requirePreference(RecorderPreferences.KEY_AUDIO_SOURCE);
        audioBitrate.setVisible(captureAudio.isChecked());
        audioSource.setVisible(captureAudio.isChecked());
        captureAudio.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = Boolean.TRUE.equals(newValue);
            audioBitrate.setVisible(enabled);
            audioSource.setVisible(enabled);
            // The stored value has not been updated yet, so tell the estimate what is coming.
            refreshEstimate(enabled);
            return true;
        });

        // Every one of these moves the estimate, so recompute whenever any of them is touched.
        for (String key : new String[]{RecorderPreferences.KEY_VIDEO_BITRATE,
                RecorderPreferences.KEY_AUDIO_BITRATE, RecorderPreferences.KEY_FRAME_RATE,
                RecorderPreferences.KEY_RESOLUTION}) {
            requirePreference(key).setOnPreferenceChangeListener((preference, newValue) -> {
                refreshEstimateAfterWrite();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshEstimate(null);
    }

    /** Preference values are written after the listener returns, so read them on the next pass. */
    private void refreshEstimateAfterWrite() {
        if (getView() != null) getView().post(() -> refreshEstimate(null));
    }

    /**
     * Describes what these settings cost in storage: how fast a recording grows, how long one
     * clip can run before it has to be stopped, and what is free on the device.
     *
     * @param audioOverride the audio setting about to be stored, or null to use what is stored
     */
    private void refreshEstimate(Boolean audioOverride) {
        Context context = getContext();
        if (context == null || mEstimatePreference == null) return;

        RecorderPreferences preferences = RecorderPreferences.load(context);
        long bytesPerSecond = preferences.bytesPerSecond();
        if (audioOverride != null && audioOverride != preferences.captureAudio) {
            // Same arithmetic as bytesPerSecond(), with the pending audio choice applied.
            long bits = preferences.videoBitRate + (audioOverride ? preferences.audioBitRate : 0);
            bytesPerSecond = Math.max(1, bits / 8);
        }

        String perHour = Formatter.formatShortFileSize(context, bytesPerSecond * 3600);
        File directory = RecorderPreferences.currentRecordingsDirectory();
        long usableSpace = RecorderPreferences.usableSpaceFor(directory);
        if (usableSpace <= 0) {
            // Without a readable location only the growth rate can be stated honestly.
            mEstimatePreference.setSummary(
                    getString(R.string.preference_recorder_estimate_unavailable, perHour));
            return;
        }

        long spaceBudget = Math.max(0, usableSpace - RecorderPreferences.MIN_FREE_BYTES);
        boolean fileLimited = RecorderPreferences.MAX_OUTPUT_BYTES <= spaceBudget;
        long seconds = Math.min(RecorderPreferences.MAX_OUTPUT_BYTES, spaceBudget) / bytesPerSecond;

        mEstimatePreference.setSummary(getString(R.string.preference_recorder_estimate_summary,
                perHour,
                formatDuration(seconds),
                getString(fileLimited
                        ? R.string.preference_recorder_estimate_limit_file
                        : R.string.preference_recorder_estimate_limit_space),
                Formatter.formatShortFileSize(context, usableSpace)));
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
        if (minutes > 0) return String.format(Locale.getDefault(), "%dm", minutes);
        return String.format(Locale.getDefault(), "%ds", seconds);
    }
}
