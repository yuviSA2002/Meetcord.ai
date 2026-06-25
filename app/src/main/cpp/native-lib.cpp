#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>
#include <android/log.h>
#include <fstream>
#include "whisper.h"

#define TAG "MeetcordJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct WhisperContextWrapper {
    struct whisper_context * ctx = nullptr;
    std::vector<float> pcmf32; // Audio buffer ring
    std::mutex mutex;
    std::atomic<bool> is_running{false};
    std::thread worker_thread;
    
    // JNI Callbacks
    JavaVM* jvm = nullptr;
    jclass whisperEngineClass = nullptr;
    jmethodID onNewWordMethodId = nullptr;
};

void whisper_worker(WhisperContextWrapper* wrapper) {
    JNIEnv* env = nullptr;
    if (wrapper->jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        LOGE("Failed to attach worker thread to JVM");
        return;
    }

    while (wrapper->is_running) {
        std::vector<float> pcm_copy;
        {
            std::lock_guard<std::mutex> lock(wrapper->mutex);
            // Wait for at least 2 seconds of audio (16000 * 2 samples) before processing
            if (wrapper->pcmf32.size() > 16000 * 2) {
                pcm_copy = wrapper->pcmf32;
                wrapper->pcmf32.clear(); 
            }
        }
        
        if (!pcm_copy.empty()) {
            whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
            wparams.print_progress   = false;
            wparams.print_special    = false;
            wparams.print_realtime   = false;
            wparams.print_timestamps = false;
            wparams.token_timestamps = true; 
            wparams.language         = "en";
            wparams.entropy_thold    = 2.8f; // Prevents hallucination loops on silence
            wparams.no_speech_thold  = 0.6f; // Ignore silence properly
            
            if (whisper_full(wrapper->ctx, wparams, pcm_copy.data(), pcm_copy.size()) == 0) {
                const int n_segments = whisper_full_n_segments(wrapper->ctx);
                for (int i = 0; i < n_segments; ++i) {
                    const int n_tokens = whisper_full_n_tokens(wrapper->ctx, i);
                    for (int j = 0; j < n_tokens; ++j) {
                        auto token_data = whisper_full_get_token_data(wrapper->ctx, i, j);
                        const char* text = whisper_full_get_token_text(wrapper->ctx, i, j);
                        
                        // Filter out special tokens like [_BEG_], [_TT_112]
                        std::string str_text(text);
                        if (str_text.find("[_") != std::string::npos || str_text.find("]") != std::string::npos) {
                            continue;
                        }
                        
                        // Callback to Kotlin
                        jstring jtext = env->NewStringUTF(text);
                        // t0 and t1 are in 10ms units
                        env->CallStaticVoidMethod(wrapper->whisperEngineClass, wrapper->onNewWordMethodId, jtext, (jlong)(token_data.t0 * 10), (jlong)(token_data.t1 * 10), true);
                        env->DeleteLocalRef(jtext);
                    }
                }
            }
        } else {
            // Sleep briefly to prevent CPU spinning
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }
    }
    
    wrapper->jvm->DetachCurrentThread();
}

extern "C" JNIEXPORT jlong JNICALL
Java_ai_meetcord_asr_WhisperEngine_initModelNative(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!ctx) {
        LOGE("Failed to initialize whisper context. Ensure model path is correct and accessible.");
        return 0;
    }

    auto *wrapper = new WhisperContextWrapper();
    wrapper->ctx = ctx;
    env->GetJavaVM(&wrapper->jvm);
    
    jclass localClass = env->FindClass("ai/meetcord/asr/WhisperEngine");
    wrapper->whisperEngineClass = (jclass) env->NewGlobalRef(localClass);
    wrapper->onNewWordMethodId = env->GetStaticMethodID(wrapper->whisperEngineClass, "onNewWordExtracted", "(Ljava/lang/String;JJZ)V");

    wrapper->is_running = true;
    wrapper->worker_thread = std::thread(whisper_worker, wrapper);
    
    return reinterpret_cast<jlong>(wrapper);
}

extern "C" JNIEXPORT void JNICALL
Java_ai_meetcord_asr_WhisperEngine_processAudioChunkNative(JNIEnv *env, jobject thiz, jlong contextPtr, jshortArray chunk, jint length) {
    auto *wrapper = reinterpret_cast<WhisperContextWrapper *>(contextPtr);
    if (!wrapper) return;

    jshort *body = env->GetShortArrayElements(chunk, nullptr);

    {
        std::lock_guard<std::mutex> lock(wrapper->mutex);
        // Explicitly scaling PCM data to float -1.0 to 1.0 (Memory safe, avoids extra arrays)
        for (int i = 0; i < length; ++i) {
            wrapper->pcmf32.push_back((float)body[i] / 32768.0f);
        }
    }

    // Abort flag tells JNI we don't need to copy changes back to the original Kotlin array.
    env->ReleaseShortArrayElements(chunk, body, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_ai_meetcord_asr_WhisperEngine_freeModelNative(JNIEnv *env, jobject thiz, jlong contextPtr) {
    auto *wrapper = reinterpret_cast<WhisperContextWrapper *>(contextPtr);
    if (!wrapper) return;

    wrapper->is_running = false;
    if (wrapper->worker_thread.joinable()) {
        wrapper->worker_thread.join();
    }

    if (wrapper->ctx) {
        whisper_free(wrapper->ctx);
    }
    
    if (wrapper->whisperEngineClass) {
        env->DeleteGlobalRef(wrapper->whisperEngineClass);
    }

    delete wrapper;
}

extern "C" JNIEXPORT void JNICALL
Java_ai_meetcord_asr_WhisperEngine_transcribeFileNative(JNIEnv *env, jobject thiz, jlong contextPtr, jstring filePath) {
    auto *wrapper = reinterpret_cast<WhisperContextWrapper *>(contextPtr);
    if (!wrapper) return;

    const char *path = env->GetStringUTFChars(filePath, nullptr);
    std::string path_str(path);
    env->ReleaseStringUTFChars(filePath, path);

    std::ifstream file(path_str, std::ios::binary);
    if (!file) {
        LOGE("Failed to open audio file: %s", path_str.c_str());
        return;
    }

    file.seekg(0, std::ios::end);
    std::streamsize size = file.tellg();
    file.seekg(44, std::ios::beg); // Skip 44-byte WAV header

    std::streamsize data_size = size - 44;
    if (data_size <= 0) return;

    std::vector<int16_t> pcm16(data_size / 2);
    if (!file.read(reinterpret_cast<char*>(pcm16.data()), data_size)) {
        LOGE("Failed to read audio data");
        return;
    }

    std::vector<float> pcmf32(pcm16.size());
    for (size_t i = 0; i < pcm16.size(); ++i) {
        pcmf32[i] = (float)pcm16[i] / 32768.0f;
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress   = false;
    wparams.print_special    = false;
    wparams.print_realtime   = false;
    wparams.print_timestamps = false;
    wparams.token_timestamps = true; 
    wparams.language         = "en";
    wparams.entropy_thold    = 2.8f; 
    wparams.no_speech_thold  = 0.6f;

    if (whisper_full(wrapper->ctx, wparams, pcmf32.data(), pcmf32.size()) == 0) {
        const int n_segments = whisper_full_n_segments(wrapper->ctx);
        for (int i = 0; i < n_segments; ++i) {
            const int n_tokens = whisper_full_n_tokens(wrapper->ctx, i);
            for (int j = 0; j < n_tokens; ++j) {
                auto token_data = whisper_full_get_token_data(wrapper->ctx, i, j);
                const char* text = whisper_full_get_token_text(wrapper->ctx, i, j);
                
                std::string str_text(text);
                if (str_text.find("[_") != std::string::npos || str_text.find("]") != std::string::npos) {
                    continue;
                }
                
                jstring jtext = env->NewStringUTF(text);
                env->CallStaticVoidMethod(wrapper->whisperEngineClass, wrapper->onNewWordMethodId, jtext, (jlong)(token_data.t0 * 10), (jlong)(token_data.t1 * 10), true);
                env->DeleteLocalRef(jtext);
            }
        }
    }
}
