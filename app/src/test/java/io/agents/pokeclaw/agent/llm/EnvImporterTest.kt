package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvImporterTest {

    // ── parse() ───────────────────────────────────────────────────────────────

    @Test
    fun `parse basic key=value`() {
        val result = EnvImporter.parse("AI_DEEPSEEK_KEY=sk-abc123")
        assertEquals("sk-abc123", result["AI_DEEPSEEK_KEY"])
    }

    @Test
    fun `parse strips inline comment`() {
        val result = EnvImporter.parse("AI_GROQ_KEY=gsk_xyz #my groq key")
        assertEquals("gsk_xyz", result["AI_GROQ_KEY"])
    }

    @Test
    fun `parse strips double quotes`() {
        val result = EnvImporter.parse("""AI_GEMINI_KEY="AIza123" """)
        assertEquals("AIza123", result["AI_GEMINI_KEY"])
    }

    @Test
    fun `parse strips single quotes`() {
        val result = EnvImporter.parse("AI_MISTRAL_KEY='mistral-key'")
        assertEquals("mistral-key", result["AI_MISTRAL_KEY"])
    }

    @Test
    fun `parse skips comment lines`() {
        val result = EnvImporter.parse("# this is a comment\nAI_DEEPSEEK_KEY=val")
        assertEquals(1, result.size)
        assertEquals("val", result["AI_DEEPSEEK_KEY"])
    }

    @Test
    fun `parse skips blank lines`() {
        val result = EnvImporter.parse("\n\nAI_DEEPSEEK_KEY=val\n\n")
        assertEquals(1, result.size)
    }

    @Test
    fun `parse skips lines without equals`() {
        val result = EnvImporter.parse("INVALID_LINE\nAI_DEEPSEEK_KEY=val")
        assertEquals(1, result.size)
    }

    @Test
    fun `parse skips empty values`() {
        val result = EnvImporter.parse("AI_DEEPSEEK_KEY=")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse multi-key env block`() {
        val env = """
            AI_DEEPSEEK_KEY=ds-key
            AI_GROQ_KEY=groq-key
            AI_GEMINI_KEY=gem1,gem2,gem3
            AI_OPENROUTER_KEY=or-key
            AI_MISTRAL_KEY=ms-key
        """.trimIndent()
        val result = EnvImporter.parse(env)
        assertEquals(5, result.size)
        assertEquals("ds-key",        result["AI_DEEPSEEK_KEY"])
        assertEquals("groq-key",      result["AI_GROQ_KEY"])
        assertEquals("gem1,gem2,gem3",result["AI_GEMINI_KEY"])
        assertEquals("or-key",        result["AI_OPENROUTER_KEY"])
        assertEquals("ms-key",        result["AI_MISTRAL_KEY"])
    }

    @Test
    fun `parse value with equals sign in it`() {
        val result = EnvImporter.parse("AI_DEEPSEEK_KEY=abc=def=ghi")
        assertEquals("abc=def=ghi", result["AI_DEEPSEEK_KEY"])
    }

    @Test
    fun `findEnvFile returns null when no file exists`() {
        // All candidate paths should be absent in the test environment
        val file = EnvImporter.findEnvFile()
        assertTrue(file == null || !file.exists())
    }
}
