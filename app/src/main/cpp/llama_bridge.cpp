// JNI bridge between Java (LlamaEngine.java) and llama.cpp's C API.
//
// HONESTY NOTE, read before building: llama.cpp's C API renames functions
// fairly often (e.g. llama_load_model_from_file -> llama_model_load_from_file
// in late 2024). This file is written against a recent-as-of-training API
// shape. If your `git submodule` pull is newer/older, the compiler error will
// point at the exact renamed function -- it's usually a 1:1 rename, not a
// logic change. Fix points are marked "// API-SENSITIVE" below.

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "AssistantTwinLLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    llama_model* g_model = nullptr;
    llama_context* g_ctx = nullptr;
    int g_n_ctx = 2048;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dhanaram_assistanttwin_llm_LlamaEngine_nativeLoadModel(
        JNIEnv* env, jobject /* this */, jstring modelPath, jint nCtx, jint nThreads) {

    const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(modelPath, pathChars);

    g_n_ctx = nCtx;
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-only: safest default across the huge range of Android GPUs

    // API-SENSITIVE: some llama.cpp versions name this llama_model_load_from_file instead.
    g_model = llama_load_model_from_file(path.c_str(), model_params);
    if (g_model == nullptr) {
        LOGE("Failed to load model from %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("Failed to create llama context");
        return JNI_FALSE;
    }

    LOGI("Model loaded: %s (n_ctx=%d, n_threads=%d)", path.c_str(), nCtx, nThreads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dhanaram_assistanttwin_llm_LlamaEngine_nativeUnload(JNIEnv* env, jobject /* this */) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    llama_backend_free();
}

// Streams generated text back to Java by invoking onToken(String) on the passed
// callback object once per token, so the chat UI can render tokens live -- the
// on-device equivalent of the SSE streaming the earlier server-based version used.
extern "C" JNIEXPORT jstring JNICALL
Java_com_dhanaram_assistanttwin_llm_LlamaEngine_nativeGenerate(
        JNIEnv* env, jobject /* this */, jstring prompt, jint maxTokens, jobject callback) {

    if (g_ctx == nullptr || g_model == nullptr) {
        return env->NewStringUTF("");
    }

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    const llama_vocab* vocab = llama_model_get_vocab(g_model);

    std::vector<llama_token> tokens(promptStr.size() + 32);
    int nTokens = llama_tokenize(
        vocab, promptStr.c_str(), (int32_t) promptStr.size(),
        tokens.data(), (int32_t) tokens.size(), true, true);
    if (nTokens < 0) { tokens.resize(-nTokens); nTokens = -nTokens; }
    tokens.resize(nTokens);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Initial decode failed");
        return env->NewStringUTF("");
    }

    // API-SENSITIVE: sampler-chain API replaced older llama_sample_* calls around
    // mid-2024. This builds a simple greedy sampler -- swap in top-k/top-p/temp
    // samplers here for less repetitive output once the basic loop compiles.
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string fullText;
    int nCur = (int) tokens.size();

    for (int i = 0; i < maxTokens; i++) {
        llama_token newToken = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (len > 0) {
            std::string piece(buf, len);
            fullText += piece;
            jstring jPiece = env->NewStringUTF(piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jPiece);
            env->DeleteLocalRef(jPiece);
        }

        llama_token nextTokenArr[1] = { newToken };
        llama_batch nextBatch = llama_batch_get_one(nextTokenArr, 1);
        if (llama_decode(g_ctx, nextBatch) != 0) {
            LOGE("Decode failed at step %d", i);
            break;
        }
        nCur++;
        if (nCur >= g_n_ctx - 4) break; // leave headroom instead of hard-failing at the context limit
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(fullText.c_str());
}
