package io.agents.pokeclaw.agent.llm

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.ChatMessage
import io.agents.pokeclaw.agent.AgentConfig
import io.agents.pokeclaw.agent.LlmProvider
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientBuilderAdapter
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.ConcurrentHashMap

/**
 * CascadingLlmClient — tries providers in priority order, falls back on failure.
 *
 * Cascade order:
 *   1. DeepSeek (cheap, reliable)
 *   2. Groq (fast, generous free tier)
 *   3. Gemini (21 keys, massive quota — rotated round-robin)
 *   4. OpenRouter (single key, 200+ models)
 *   5. Mistral
 *   6. Keyless proxies (llm7, airforce, unfiltered)
 *   7. Public Ollama endpoints
 *   8. Local GGUF (last resort)
 */
class CascadingLlmClient(
    private val httpClientBuilder: OkHttpClientBuilderAdapter,
    private val baseConfig: AgentConfig,
) : LlmClient {

    data class ProviderSlot(
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val provider: LlmProvider = LlmProvider.OPENAI,
        val isKeyless: Boolean = false,
    )

    private val deadUntil = ConcurrentHashMap<String, Long>()
    private var geminiKeyIndex = 0
    private val slots: List<ProviderSlot> by lazy { buildSlots() }

    private fun buildSlots(): List<ProviderSlot> {
        val list = mutableListOf<ProviderSlot>()

        // 1. DeepSeek
        val dsKey = KVUtils.getString(KEY_DEEPSEEK, "")
        if (dsKey.isNotEmpty()) list += ProviderSlot("deepseek", EP_DEEPSEEK, dsKey, "deepseek-chat")

        // 2. Groq
        val groqKey = KVUtils.getString(KEY_GROQ, "")
        if (groqKey.isNotEmpty()) list += ProviderSlot("groq", EP_GROQ, groqKey, "llama-3.3-70b-versatile")

        // 3. Gemini — rotate through comma-separated keys
        val geminiRaw = KVUtils.getString(KEY_GEMINI, "")
        val geminiKeys = geminiRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        geminiKeys.forEachIndexed { i, key ->
            list += ProviderSlot("gemini_$i", EP_GEMINI, key, "gemini-2.5-flash")
        }

        // 4. OpenRouter
        val orKey = KVUtils.getString(KEY_OPENROUTER, "")
        if (orKey.isNotEmpty()) list += ProviderSlot("openrouter", EP_OPENROUTER, orKey, "meta-llama/llama-3.3-70b-instruct")

        // 5. Mistral
        val miKey = KVUtils.getString(KEY_MISTRAL, "")
        if (miKey.isNotEmpty()) list += ProviderSlot("mistral", EP_MISTRAL, miKey, "mistral-small-latest")

        // 6. Keyless proxies
        list += ProviderSlot("llm7",       EP_LLM7,       "none", "gpt-4o-mini",            isKeyless = true)
        list += ProviderSlot("airforce",   EP_AIRFORCE,   "none", "gpt-4o-mini",             isKeyless = true)
        list += ProviderSlot("unfiltered", EP_UNFILTERED, "none", "gpt-4o-mini",             isKeyless = true)
        list += ProviderSlot("pollinations", EP_POLLINATIONS, "none", "openai-large",         isKeyless = true)

        // 7. Public Ollama endpoints (assume llama3 is running)
        PUBLIC_OLLAMA.forEach { ep ->
            list += ProviderSlot("ollama_${ep.substringAfterLast('/')}", ep, "ollama", "llama3.2:3b")
        }

        XLog.i(TAG, "cascade slots: ${list.size} (${list.count { !it.isKeyless }} keyed, ${list.count { it.isKeyless }} keyless)")
        return list
    }

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        val now = System.currentTimeMillis()
        val tried = mutableListOf<String>()
        for (slot in slots) {
            val deadAt = deadUntil[slot.name] ?: 0L
            if (deadAt > now) { XLog.d(TAG, "skip dead: ${slot.name} (dead for ${(deadAt - now)/1000}s)"); continue }
            tried += slot.name
            try {
                val client = slotToClient(slot)
                val resp = client.chat(messages, toolSpecs)
                XLog.i(TAG, "cascade hit: ${slot.name}")
                return resp
            } catch (e: Exception) {
                val backoffMs = backoffFor(slot.name, e)
                if (backoffMs > 0) deadUntil[slot.name] = now + backoffMs
                XLog.w(TAG, "cascade miss: ${slot.name} → ${e.message?.take(80)} (backoff ${backoffMs/1000}s)")
            }
        }
        throw RuntimeException("All cascade providers exhausted. Tried: $tried")
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener,
    ): LlmResponse {
        val response = chat(messages, toolSpecs)
        if (!response.text.isNullOrEmpty()) listener.onPartialText(response.text)
        listener.onComplete(response)
        return response
    }

    override fun close() {}

    private fun slotToClient(slot: ProviderSlot): LlmClient {
        val cfg = AgentConfig(
            apiKey    = slot.apiKey,
            baseUrl   = slot.baseUrl,
            modelName = slot.model,
            systemPrompt = baseConfig.systemPrompt,
            maxIterations = baseConfig.maxIterations,
            temperature   = baseConfig.temperature,
            provider  = LlmProvider.OPENAI,
            streaming = false,
        )
        return OpenAiLlmClient(cfg, httpClientBuilder)
    }

    private fun backoffFor(name: String, e: Exception): Long {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("401") || msg.contains("unauthorized") || msg.contains("invalid api") -> 3_600_000L  // 1h — bad key
            msg.contains("429") || msg.contains("rate limit") || msg.contains("quota")         ->   300_000L  // 5m
            msg.contains("503") || msg.contains("unavailable")                                 ->    60_000L  // 1m
            msg.contains("timeout") || msg.contains("connect")                                 ->    30_000L  // 30s
            else                                                                                ->    15_000L  // 15s
        }
    }

    companion object {
        private const val TAG = "ProviderCascade"

        // KVUtils keys for cascade provider credentials
        const val KEY_DEEPSEEK   = "CASCADE_KEY_DEEPSEEK"
        const val KEY_GROQ       = "CASCADE_KEY_GROQ"
        const val KEY_GEMINI     = "CASCADE_KEY_GEMINI"
        const val KEY_OPENROUTER = "CASCADE_KEY_OPENROUTER"
        const val KEY_MISTRAL    = "CASCADE_KEY_MISTRAL"

        // Endpoints
        private const val EP_DEEPSEEK    = "https://api.deepseek.com/v1"
        private const val EP_GROQ        = "https://api.groq.com/openai/v1"
        private const val EP_GEMINI      = "https://generativelanguage.googleapis.com/v1beta/openai/"
        private const val EP_OPENROUTER  = "https://openrouter.ai/api/v1"
        private const val EP_MISTRAL     = "https://api.mistral.ai/v1"
        private const val EP_LLM7        = "https://llm7.io/v1"
        private const val EP_AIRFORCE    = "https://api.airforce/v1"
        private const val EP_UNFILTERED  = "https://unfilteredapi.com/v1"
        private const val EP_POLLINATIONS = "https://gen.pollinations.ai/v1"

        private val PUBLIC_OLLAMA = listOf(
            "http://108.181.196.208:11434/v1",
            "http://5.149.249.212:11434/v1",
            "http://89.111.170.212:11434/v1",
        )

        /** Populate cascade keys from a parsed env map (call from EnvImporter). */
        fun importFromEnv(env: Map<String, String>) {
            env["AI_DEEPSEEK_KEY"]?.takeIf { it.isNotEmpty() }?.let { KVUtils.putString(KEY_DEEPSEEK, it) }
            env["AI_GROQ_KEY"]?.takeIf { it.isNotEmpty() }?.let { KVUtils.putString(KEY_GROQ, it) }
            env["AI_GEMINI_KEY"]?.takeIf { it.isNotEmpty() }?.let { KVUtils.putString(KEY_GEMINI, it) }
            env["AI_OPENROUTER_KEY"]?.takeIf { it.isNotEmpty() }?.let { KVUtils.putString(KEY_OPENROUTER, it) }
            env["AI_MISTRAL_KEY"]?.takeIf { it.isNotEmpty() }?.let { KVUtils.putString(KEY_MISTRAL, it) }
            XLog.i(TAG, "importFromEnv: keys loaded")
        }

        /** True if at least one keyed provider is configured. */
        fun hasAnyKey(): Boolean = listOf(KEY_DEEPSEEK, KEY_GROQ, KEY_GEMINI, KEY_OPENROUTER, KEY_MISTRAL)
            .any { KVUtils.getString(it, "").isNotEmpty() }
    }
}
