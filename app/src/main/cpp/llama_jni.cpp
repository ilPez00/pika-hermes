#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>

#define LTAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LTAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LTAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model   *g_model   = nullptr;
static llama_context *g_ctx     = nullptr;
static llama_sampler *g_sampler = nullptr;

static void free_resources() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_ctx)     { llama_free(g_ctx);              g_ctx     = nullptr; }
    if (g_model)   { llama_free_model(g_model);      g_model   = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeLoadModel(
    JNIEnv *env, jobject /*thiz*/, jstring model_path, jint n_ctx) {

    std::lock_guard<std::mutex> lock(g_mutex);
    free_resources();

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return JNI_FALSE;

    llama_log_set([](ggml_log_level level, const char *text, void *) {
        int prio = (level == GGML_LOG_LEVEL_ERROR) ? ANDROID_LOG_ERROR :
                   (level == GGML_LOG_LEVEL_WARN)  ? ANDROID_LOG_WARN  : ANDROID_LOG_DEBUG;
        __android_log_print(prio, "llama.cpp", "%s", text);
    }, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    LOGI("loading model: %s  n_ctx=%d", path, n_ctx);
    g_model = llama_load_model_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!g_model) {
        LOGE("llama_load_model_from_file returned null");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 2048);
    cparams.n_threads       = 4;
    cparams.n_threads_batch = 4;
    LOGI("creating context n_ctx=%u", cparams.n_ctx);
    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("llama_new_context_with_model returned null (OOM?)");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // sampler chain: top_k → top_p → temp → dist (replaces deprecated llama_sampling_context)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeComplete(
    JNIEnv *env, jobject /*thiz*/, jstring prompt_jstr, jint max_tokens) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx || !g_sampler) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt_jstr, nullptr);
    if (!prompt_str) return env->NewStringUTF("");

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    int n_ctx    = (int)llama_n_ctx(g_ctx);
    int n_prompt = -llama_tokenize(vocab, prompt_str, -1, nullptr, 0, true, false);
    if (n_prompt <= 0 || n_prompt > n_ctx / 2) {
        env->ReleaseStringUTFChars(prompt_jstr, prompt_str);
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(n_prompt);
    n_prompt = llama_tokenize(vocab, prompt_str, -1, tokens.data(), n_prompt, true, false);
    env->ReleaseStringUTFChars(prompt_jstr, prompt_str);
    if (n_prompt <= 0) return env->NewStringUTF("");

    // clear KV cache so each call is stateless
    llama_kv_cache_clear(g_ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("");

    std::string result;
    char piece_buf[32];
    for (int i = 0; i < max_tokens; i++) {
        llama_token id = llama_sampler_sample(g_sampler, g_ctx, -1);
        llama_sampler_accept(g_sampler, id);

        if (llama_vocab_is_eog(vocab, id)) break;

        int n = llama_token_to_piece(vocab, id, piece_buf, sizeof(piece_buf), 0, true);
        if (n > 0) result.append(piece_buf, n);

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeRelease(
    JNIEnv *env, jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    free_resources();
    llama_backend_free();
}
