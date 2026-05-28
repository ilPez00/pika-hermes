package io.agents.pokeclaw.agent.llm.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LlamaEngine {

    interface TokenCallback {
        fun onToken(token: String)
    }

    private var loaded = false

    init {
        System.loadLibrary("llama_jni")
    }

    private external fun nativeLoadModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeComplete(prompt: String, maxTokens: Int): String
    private external fun nativeCompleteStreaming(prompt: String, maxTokens: Int, callback: TokenCallback)
    private external fun nativeRelease()

    suspend fun loadModel(modelPath: String, nCtx: Int = 4096): Boolean = withContext(Dispatchers.IO) {
        if (loaded) return@withContext true
        val ok = nativeLoadModel(modelPath, nCtx)
        if (ok) loaded = true
        ok
    }

    suspend fun complete(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        if (!loaded) return@withContext ""
        nativeComplete(prompt, maxTokens)
    }

    suspend fun completeStreaming(
        prompt: String,
        maxTokens: Int = 512,
        onToken: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!loaded) return@withContext
        nativeCompleteStreaming(prompt, maxTokens, object : TokenCallback {
            override fun onToken(token: String) = onToken(token)
        })
    }

    fun release() {
        if (!loaded) return
        nativeRelease()
        loaded = false
    }
}
