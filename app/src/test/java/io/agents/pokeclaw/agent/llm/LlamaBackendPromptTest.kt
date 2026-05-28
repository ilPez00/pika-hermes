package io.agents.pokeclaw.agent.llm

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.agents.pokeclaw.agent.llm.llama.LlamaBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests prompt-building logic in LlamaBackend without loading any GGUF model.
 *
 * Uses reflection to call private buildPrompt() so the JNI layer is never touched.
 */
class LlamaBackendPromptTest {

    private fun prompt(modelPath: String, msgs: List<dev.langchain4j.data.message.ChatMessage>): String {
        val backend = LlamaBackend(modelPath = modelPath)
        val method = LlamaBackend::class.java.getDeclaredMethod(
            "buildPrompt",
            List::class.java,
            List::class.java,
        )
        method.isAccessible = true
        return method.invoke(backend, msgs, emptyList<Any>()) as String
    }

    // ── Gemma 3+ (system role supported) ─────────────────────────────────────

    @Test
    fun `gemma3 prompt starts with bos and system turn`() {
        val msgs = listOf(
            SystemMessage.from("be helpful"),
            UserMessage.from("hello"),
        )
        val p = prompt("/sdcard/gemma-3-4b.gguf", msgs)
        assertTrue(p.startsWith("<bos><start_of_turn>system\n"))
        assertTrue(p.contains("be helpful"))
        assertTrue(p.contains("<start_of_turn>user\nhello"))
        assertTrue(p.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `gemma3 prompt model turn uses model role`() {
        val msgs = listOf(
            UserMessage.from("hi"),
            AiMessage.from("hey"),
            UserMessage.from("how are you"),
        )
        val p = prompt("/models/gemma-4-e4b.gguf", msgs)
        assertTrue(p.contains("<start_of_turn>model\nhey<end_of_turn>"))
    }

    @Test
    fun `gemma3 uses default system prompt when no system message`() {
        val msgs = listOf(UserMessage.from("do something"))
        val p = prompt("/models/gemma-4-e4b.gguf", msgs)
        assertTrue(p.contains(LlamaBackend.PIKA_SYSTEM_PROMPT.take(40)))
    }

    // ── Gemma 2 (no system role — inject into first user turn) ────────────────

    @Test
    fun `gemma2 prompt has no system turn`() {
        val msgs = listOf(
            SystemMessage.from("be aggressive"),
            UserMessage.from("hello"),
        )
        val p = prompt("/sdcard/gemma-2-2b-it-abliterated-Q4_K_M.gguf", msgs)
        assertFalse(p.contains("<start_of_turn>system"))
    }

    @Test
    fun `gemma2 system text prepended into first user turn`() {
        val msgs = listOf(
            SystemMessage.from("SYS_PROMPT"),
            UserMessage.from("USER_MSG"),
        )
        val p = prompt("/sdcard/gemma-2-2b.gguf", msgs)
        assertTrue(p.contains("SYS_PROMPT\n\nUSER_MSG"))
    }

    @Test
    fun `gemma2 second user turn not prefixed with system text`() {
        val msgs = listOf(
            UserMessage.from("first"),
            AiMessage.from("reply"),
            UserMessage.from("second"),
        )
        val p = prompt("/sdcard/gemma-2-9b.gguf", msgs)
        val secondUserIdx = p.lastIndexOf("<start_of_turn>user\nsecond")
        assertTrue(secondUserIdx >= 0)
        // system text (default prompt) should not appear again before "second"
        val systemText = LlamaBackend.PIKA_SYSTEM_PROMPT.take(30)
        val lastSystemIdx = p.lastIndexOf(systemText)
        assertTrue(lastSystemIdx < secondUserIdx)
    }

    @Test
    fun `gemma2 still ends with model turn prompt`() {
        val msgs = listOf(UserMessage.from("go"))
        val p = prompt("/sdcard/gemma-2-2b.gguf", msgs)
        assertTrue(p.endsWith("<start_of_turn>model\n"))
    }

    // ── Llama 3 format ────────────────────────────────────────────────────────

    @Test
    fun `llama3 prompt starts with begin_of_text`() {
        val msgs = listOf(
            SystemMessage.from("sys"),
            UserMessage.from("hi"),
        )
        val p = prompt("/models/llama-3.2-3b-uncensored.gguf", msgs)
        assertTrue(p.startsWith("<|begin_of_text|>"))
        assertTrue(p.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(p.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(p.endsWith("<|start_header_id|>assistant<|end_header_id|>\n"))
    }

    @Test
    fun `llama3 no gemma tokens in output`() {
        val msgs = listOf(UserMessage.from("hi"))
        val p = prompt("/models/llama-3.2-3b.gguf", msgs)
        assertFalse(p.contains("<start_of_turn>"))
        assertFalse(p.contains("<end_of_turn>"))
    }

    // ── Model detection ───────────────────────────────────────────────────────

    @Test
    fun `qwen path detected as llama3-family (explicitly excluded from gemma)`() {
        // isGemma = p.contains("gemma") || (!llama && !mistral && !qwen)
        // qwen → false, so Llama3 format is used
        val msgs = listOf(UserMessage.from("hi"))
        val p = prompt("/sdcard/qwen-uncensored.gguf", msgs)
        assertTrue(p.startsWith("<|begin_of_text|>"))
    }

    @Test
    fun `mistral path detected as llama3 family`() {
        val msgs = listOf(UserMessage.from("hi"))
        val p = prompt("/sdcard/mistral-7b.gguf", msgs)
        assertTrue(p.startsWith("<|begin_of_text|>"))
    }

    @Test
    fun `dolphin-llama3 path detected as llama3`() {
        val msgs = listOf(UserMessage.from("hi"))
        val p = prompt("/sdcard/dolphin-2.9-llama3-8b-Q4_K_M.gguf", msgs)
        assertTrue(p.startsWith("<|begin_of_text|>"))
    }
}
