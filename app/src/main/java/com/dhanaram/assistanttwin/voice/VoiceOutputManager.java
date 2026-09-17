package com.dhanaram.assistanttwin.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class VoiceOutputManager {

    private boolean ready = false;
    private final TextToSpeech tts;

    public VoiceOutputManager(Context context) {
        tts = new TextToSpeech(context, status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) tts.setLanguage(Locale.getDefault());
        });
    }

    public void speak(String text) {
        if (!ready || text == null || text.trim().isEmpty()) return;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_twin_utterance");
    }

    public void stop() { tts.stop(); }
    public void shutdown() { tts.shutdown(); }
}
