package net.kdt.pojavlaunch.prefs.screens;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.recorder.RecorderPreferences;
import net.kdt.pojavlaunch.recorder.RecordingsActivity;

/**
 * Settings for the in-game recorder, plus the way into the clips it has produced.
 */
public class LauncherPreferenceRecorderFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_recorder);

        requirePreference("viewRecordings").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(requireContext(), RecordingsActivity.class));
            return true;
        });

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
            return true;
        });
    }
}
