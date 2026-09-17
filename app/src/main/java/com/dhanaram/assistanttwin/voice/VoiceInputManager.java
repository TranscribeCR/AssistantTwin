package com.dhanaram.assistanttwin.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** Wraps on-device speech recognition. Requires RECORD_AUDIO, requested at call time. */
public class VoiceInputManager {

    public interface ResultCallback {
        void onResult(String text);
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    public interface PartialCallback {
        void onPartial(String text);
    }

    private final SpeechRecognizer recognizer;

    public VoiceInputManager(Context context) {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
    }

    public void startListening(ResultCallback onResult, ErrorCallback onError, PartialCallback onPartial) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    onResult.onResult(matches.get(0));
                } else {
                    onError.onError("No speech recognized");
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty() && onPartial != null) {
                    onPartial.onPartial(matches.get(0));
                }
            }
            @Override public void onError(int error) { onError.onError("Speech recognizer error code " + error); }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        recognizer.startListening(intent);
    }

    public void stopListening() { recognizer.stopListening(); }
    public void destroy() { recognizer.destroy(); }
}
