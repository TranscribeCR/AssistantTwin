package com.dhanaram.assistanttwin.llm;

/**
 * Thin Java wrapper around the native llama.cpp bridge (llama_bridge.cpp).
 * Everything runs on-device: no server, no network call per message. Only the
 * one-time model file needs to reach the phone (download it yourself into
 * modelPath, or ship it under assets/ if you don't mind a larger APK).
 *
 * Pair with a NON-GATED GGUF model -- Qwen2.5-0.5B-Instruct-GGUF is a good fit:
 * public on Hugging Face, no login, and small enough to run at usable speed on
 * mid-range phone CPUs. Search "Qwen2.5-0.5B-Instruct-GGUF" on huggingface.co.
 */
public class LlamaEngine {

    static {
        System.loadLibrary("assistanttwin_llm");
    }

    /** Callback invoked once per generated token, for live-streaming UIs. */
    public interface TokenCallback {
        void onToken(String token);
    }

    private boolean loaded = false;

    /** Loads the model from an absolute file path. Call once, off the main thread. */
    public synchronized boolean loadModel(String modelPath, int contextSize, int threads) {
        loaded = nativeLoadModel(modelPath, contextSize, threads);
        return loaded;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Blocking generation call -- always invoke from a background thread. */
    public synchronized String generate(String prompt, int maxTokens, TokenCallback callback) {
        if (!loaded) {
            throw new IllegalStateException("LlamaEngine.loadModel() has not succeeded yet");
        }
        return nativeGenerate(prompt, maxTokens, callback);
    }

    public synchronized void unload() {
        if (loaded) {
            nativeUnload();
            loaded = false;
        }
    }

    private native boolean nativeLoadModel(String modelPath, int contextSize, int threads);
    private native String nativeGenerate(String prompt, int maxTokens, TokenCallback callback);
    private native void nativeUnload();
}
