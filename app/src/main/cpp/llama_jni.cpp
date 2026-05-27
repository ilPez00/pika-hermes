#include <jni.h>
#include <llama.h>
#include <string>
#include <vector>
#include <sstream>
#include <mutex>

static std::mutex g_mutex;
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static llama_sampling_context *g_sampling = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeLoadModel(
    JNIEnv *env, jobject /*thiz*/, jstring model_path, jint n_ctx) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char *path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return JNI_FALSE;

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);
    if (!g_model) return JNI_FALSE;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx > 0 ? n_ctx : 2048);
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_sampling = llama_sampling_init(nullptr);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeComplete(
    JNIEnv *env, jobject /*thiz*/, jstring prompt, jint max_tokens) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx || !g_sampling) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) return env->NewStringUTF("");

    llama_token *tokens = nullptr;
    int n_tokens = 0;

    int n_ctx = llama_n_ctx(g_ctx);
    int n_prompt = -llama_tokenize(g_model, prompt_str, -1, nullptr, 0, true, false);
    if (n_prompt <= 0 || n_prompt > n_ctx / 2) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("");
    }

    tokens = new llama_token[n_prompt];
    n_prompt = llama_tokenize(g_model, prompt_str, -1, tokens, n_prompt, true, false);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    if (n_prompt <= 0) {
        delete[] tokens;
        return env->NewStringUTF("");
    }

    llama_batch batch = llama_batch_get_one(tokens, n_prompt);
    if (llama_decode(g_ctx, batch) != 0) {
        delete[] tokens;
        return env->NewStringUTF("");
    }

    std::string result;
    int generated = 0;
    while (generated < max_tokens) {
        llama_token id = llama_sampling_sample(g_sampling, g_ctx, nullptr);
        llama_sampling_accept(g_sampling, g_ctx, id, true);

        if (llama_token_is_eog(g_model, id)) break;

        char buf[8];
        int n = llama_token_to_piece(g_model, id, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
        generated++;
    }

    delete[] tokens;
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_io_agents_pokeclaw_agent_llm_llama_LlamaEngine_nativeRelease(
    JNIEnv *env, jobject /*thiz*/) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sampling) { llama_sampling_free(g_sampling); g_sampling = nullptr; }
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    llama_backend_free();
}
