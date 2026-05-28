package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.io.File

/**
 * Parses a .env file (KEY=value format) and populates provider credentials.
 *
 * Supports:
 * - Comma-separated values (e.g. AI_GEMINI_KEY=key1,key2,key3)
 * - Inline comments (# ...)
 * - Quoted values ("value" or 'value')
 */
object EnvImporter {

    private const val TAG = "EnvImporter"

    data class ImportResult(
        val keysImported: Int,
        val providers: List<String>,
        val errors: List<String>,
    )

    /**
     * Import from a File on device (e.g. /sdcard/pika-hermes.env or
     * /sdcard/Download/env).
     */
    fun importFromFile(file: File): ImportResult {
        return if (!file.exists()) {
            ImportResult(0, emptyList(), listOf("File not found: ${file.absolutePath}"))
        } else {
            importFromText(file.readText())
        }
    }

    /** Import from raw env text pasted by the user. */
    fun importFromText(text: String): ImportResult {
        val map = parse(text)
        return applyToStore(map)
    }

    // ── Parser ────────────────────────────────────────────────────────────────

    fun parse(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx < 1) continue
            val key = trimmed.substring(0, eqIdx).trim()
            var value = trimmed.substring(eqIdx + 1).trim()
            // Strip inline comment
            val commentIdx = value.indexOf(" #")
            if (commentIdx >= 0) value = value.substring(0, commentIdx).trim()
            // Strip quotes
            if ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\''))) {
                value = value.substring(1, value.length - 1)
            }
            if (value.isNotEmpty()) result[key] = value
        }
        return result
    }

    // ── Store ─────────────────────────────────────────────────────────────────

    private fun applyToStore(env: Map<String, String>): ImportResult {
        var count = 0
        val providers = mutableListOf<String>()
        val errors = mutableListOf<String>()

        fun store(kvKey: String, envKey: String, providerName: String) {
            val v = env[envKey]?.takeIf { it.isNotEmpty() } ?: return
            KVUtils.putString(kvKey, v)
            count++
            providers += providerName
            XLog.i(TAG, "imported: $providerName")
        }

        // Cascade providers
        store(CascadingLlmClient.KEY_DEEPSEEK,   "AI_DEEPSEEK_KEY",   "DeepSeek")
        store(CascadingLlmClient.KEY_GROQ,       "AI_GROQ_KEY",       "Groq")
        store(CascadingLlmClient.KEY_GEMINI,     "AI_GEMINI_KEY",     "Gemini")
        store(CascadingLlmClient.KEY_OPENROUTER, "AI_OPENROUTER_KEY", "OpenRouter")
        store(CascadingLlmClient.KEY_MISTRAL,    "AI_MISTRAL_KEY",    "Mistral")

        // Standard PokeClaw API key (if user sets a single preferred one)
        env["AI_OPENROUTER_KEY"]?.takeIf { it.isNotEmpty() }?.let {
            if (KVUtils.getLlmApiKey().isEmpty()) {
                KVUtils.setLlmApiKey(it)
                KVUtils.setLlmBaseUrl("https://openrouter.ai/api/v1")
                KVUtils.setLlmModelName("meta-llama/llama-3.3-70b-instruct")
            }
        }

        XLog.i(TAG, "import complete: $count keys, providers=$providers")
        return ImportResult(count, providers, errors)
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    /** Search common paths for an env file. Prioritises app-owned dirs (no permission needed). */
    fun findEnvFile(): File? {
        val appExternal = try {
            io.agents.pokeclaw.ClawApplication.instance.getExternalFilesDir(null)?.absolutePath
        } catch (_: Exception) { null }

        val candidates = buildList {
            // App-owned external dir — readable without MANAGE_EXTERNAL_STORAGE
            if (appExternal != null) {
                add("$appExternal/pika-hermes.env")
                add("$appExternal/pika_hermes.env")
                add("$appExternal/.env")
            }
            // Legacy / rooted / ADB-pushed paths (require READ_EXTERNAL_STORAGE or root)
            add("/sdcard/pika-hermes.env")
            add("/sdcard/pika_hermes.env")
            add("/sdcard/Download/pika-hermes.env")
            add("/sdcard/Download/.env")
            add("/sdcard/.env")
        }
        return candidates.map { File(it) }.firstOrNull { it.exists() }
    }
}
