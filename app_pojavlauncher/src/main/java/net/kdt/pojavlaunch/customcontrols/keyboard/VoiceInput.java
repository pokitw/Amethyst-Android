package net.kdt.pojavlaunch.customcontrols.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Dictation, for typing into the game without a keyboard.
 *
 * Chatting on a phone in landscape means covering the game with a system keyboard and hunting for
 * letters with one thumb. This says the sentence instead.
 *
 * It uses the bound-service {@link SpeechRecognizer} rather than
 * {@code startActivityForResult(ACTION_RECOGNIZE_SPEECH)}, and that is not a style preference:
 * the intent version starts the recogniser's own activity, which pauses the game activity, and
 * {@code MainActivity.onPause} sends ESCAPE while the cursor is grabbed. Every dictation would
 * open the pause menu and drop the game out of immersive fullscreen.
 *
 * <p><b>Threading.</b> The recogniser is created, started and destroyed on the main thread, and
 * every callback is handed on from there. That is a hard requirement further down: the native
 * input queue is not safe for several writers, so the characters this produces must be typed from
 * one thread only.
 *
 * <p><b>What it does not do.</b> It never opens chat. A character sent to the game goes to
 * whatever text field is already open, and the key that opens chat belongs to the player — it is
 * rebindable, and nothing on the launcher side can read their keybinds. So the button that starts
 * dictation is either pressed with chat already open, or it is a control button that sends the
 * player's own chat key first and then starts listening.
 */
public class VoiceInput {

    /** What the overlay needs to know, called on the main thread. */
    public interface Listener {
        /** The microphone is live. */
        void onVoiceStarted();

        /** A best guess so far, or the final text. Never null. */
        void onVoiceText(String text, boolean isFinal);

        /**
         * Listening stopped.
         *
         * @param errorRes a string resource explaining why, or 0 when it simply finished
         */
        void onVoiceStopped(int errorRes);

        /** How loud the speaker is, 0..1, for the level ring. */
        void onVoiceLevel(float level);
    }

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    @Nullable private SpeechRecognizer mRecognizer;
    @Nullable private Listener mListener;
    private boolean mListening;

    /**
     * Some recognisers on some devices never call back at all. Without this the button could stick
     * in "listening" with the microphone apparently live, which is the worst state to leave a
     * player in.
     *
     * It is a silence timer, not a session limit: every callback pushes it back, so it only ever
     * fires when nothing at all has been heard for this long. A fixed limit would have cut off
     * anyone still talking, which is exactly the case dictation exists for.
     */
    private static final long SILENCE_MS = 12000L;

    /**
     * The shorter wait after asking for a result, which is a question the recogniser has already
     * heard enough to answer.
     */
    private static final long RESULT_MS = 6000L;

    private final Runnable mWatchdog = new Runnable() {
        @Override
        public void run() {
            if (mListening) stop(net.kdt.pojavlaunch.R.string.voice_error_timeout);
        }
    };

    private void armWatchdog(long delay) {
        mHandler.removeCallbacks(mWatchdog);
        mHandler.postDelayed(mWatchdog, delay);
    }

    public VoiceInput(Context context) {
        mContext = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public boolean isListening() {
        return mListening;
    }

    /** Whether this device has anything that can transcribe speech at all. */
    public static boolean isAvailable(Context context) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(context);
        } catch (Throwable ignored) {
            // Never worth crashing the game over a capability query.
            return false;
        }
    }

    /**
     * Begin listening.
     *
     * @param language a BCP-47 tag, or null for the device's own language
     */
    public void start(@Nullable String language) {
        start(language, false);
    }

    /**
     * Begin listening.
     *
     * @param language  a BCP-47 tag, or null for the device's own language
     * @param sustained true when a finger is holding a button down for the length of the
     *                  dictation. The recogniser is then asked to wait far longer before deciding
     *                  a pause was the end of the sentence, because the player has already said
     *                  when it ends — they are still holding the button.
     */
    public void start(@Nullable String language, boolean sustained) {
        if (mListening) return;
        if (!isAvailable(mContext)) {
            fireStopped(net.kdt.pojavlaunch.R.string.voice_error_unavailable);
            return;
        }
        try {
            if (mRecognizer == null) {
                mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
                mRecognizer.setRecognitionListener(mRecognitionListener);
            }
            mListening = true;
            fireStarted();
            mRecognizer.startListening(buildIntent(language, sustained));
            armWatchdog(SILENCE_MS);
        } catch (Throwable t) {
            mListening = false;
            fireStopped(net.kdt.pojavlaunch.R.string.voice_error_unavailable);
        }
    }

    /** Stop listening and keep whatever was heard. */
    public void stop() {
        if (!mListening || mRecognizer == null) return;
        try {
            // Not cancel(): this asks for a final result from what has already been said, which is
            // what releasing a push-to-talk button should mean.
            mRecognizer.stopListening();
            armWatchdog(RESULT_MS);
        } catch (Throwable ignored) {
        }
    }

    /** Stop listening and throw away what was heard. */
    public void cancel() {
        mHandler.removeCallbacks(mWatchdog);
        if (mRecognizer != null) {
            try {
                mRecognizer.cancel();
            } catch (Throwable ignored) {
            }
        }
        if (mListening) {
            mListening = false;
            fireStopped(0);
        }
    }

    /** Drop the service binding. A session per press would otherwise accumulate one each. */
    public void release() {
        mHandler.removeCallbacks(mWatchdog);
        mListening = false;
        if (mRecognizer != null) {
            try {
                mRecognizer.destroy();
            } catch (Throwable ignored) {
            }
            mRecognizer = null;
        }
    }

    /**
     * How long a silence has to last before a held dictation is treated as finished.
     *
     * A hint, not a guarantee: recognisers are free to ignore it and Google's caps it well below
     * whatever is asked for. That is exactly why the caller must still handle a session ending
     * early rather than trusting this to hold the line.
     */
    private static final long SUSTAINED_SILENCE_MS = 10000L;

    private Intent buildIntent(@Nullable String language, boolean sustained) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mContext.getPackageName());
        // Partial results are what make the overlay feel alive rather than frozen while speaking.
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        String tag = language == null || language.isEmpty()
                ? Locale.getDefault().toLanguageTag() : language;
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        // A phone in a game is often a phone with no signal to spare. Asking for on-device
        // recognition is a preference, not a requirement, and it is ignored where unsupported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        if (sustained) {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SUSTAINED_SILENCE_MS);
            intent.putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SUSTAINED_SILENCE_MS);
        }
        return intent;
    }

    private void stop(int errorRes) {
        mListening = false;
        mHandler.removeCallbacks(mWatchdog);
        try {
            if (mRecognizer != null) mRecognizer.cancel();
        } catch (Throwable ignored) {
        }
        fireStopped(errorRes);
    }

    private void fireStarted() {
        if (mListener != null) mListener.onVoiceStarted();
    }

    private void fireStopped(int errorRes) {
        if (mListener != null) mListener.onVoiceStopped(errorRes);
    }

    private final RecognitionListener mRecognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            armWatchdog(SILENCE_MS);
        }

        @Override
        public void onBeginningOfSpeech() {
            armWatchdog(SILENCE_MS);
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Roughly ten times a second while the microphone is open, which is what makes this
            // the callback that proves the recogniser is still alive.
            armWatchdog(SILENCE_MS);
            // The API reports roughly -2..10 dB. Normalised here so the overlay never has to know.
            if (mListener != null) {
                mListener.onVoiceLevel(Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f)));
            }
        }

        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            // A cancelled session usually reports ERROR_CLIENT a moment later. Nothing is still
            // listening by then, and passing it on would flash a failure over a deliberate stop.
            if (!mListening) return;
            mListening = false;
            mHandler.removeCallbacks(mWatchdog);
            fireStopped(messageFor(error));
        }

        @Override
        public void onResults(Bundle results) {
            if (!mListening) return;
            mListening = false;
            mHandler.removeCallbacks(mWatchdog);
            String text = firstOf(results);
            if (mListener != null) {
                if (text != null) mListener.onVoiceText(text, true);
                mListener.onVoiceStopped(0);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (!mListening) return;
            armWatchdog(SILENCE_MS);
            String text = firstOf(partialResults);
            if (text != null && mListener != null) mListener.onVoiceText(text, false);
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    };

    @Nullable
    private static String firstOf(@Nullable Bundle results) {
        if (results == null) return null;
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        String first = list.get(0);
        return first == null || first.isEmpty() ? null : first;
    }

    /** Every error the player could plausibly act on gets its own words. */
    private static int messageFor(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return net.kdt.pojavlaunch.R.string.voice_error_permission;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return net.kdt.pojavlaunch.R.string.voice_error_network;
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return net.kdt.pojavlaunch.R.string.voice_error_no_match;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return net.kdt.pojavlaunch.R.string.voice_error_busy;
            default:
                return net.kdt.pojavlaunch.R.string.voice_error_generic;
        }
    }
}
