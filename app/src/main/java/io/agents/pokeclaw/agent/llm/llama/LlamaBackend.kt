package io.agents.pokeclaw.agent.llm.llama

import io.agents.pokeclaw.agent.llm.LlmClient
import io.agents.pokeclaw.agent.llm.LlmResponse
import io.agents.pokeclaw.agent.llm.StreamingListener
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.runBlocking

class LlamaBackend(
    private val modelPath: String,
    private val nCtx: Int = 4096,
    private val maxTokens: Int = 2048,
) : LlmClient {

    override fun chat(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
    ): LlmResponse {
        val prompt = buildChatPrompt(messages)
        val text = runBlocking { LlamaEngine.complete(prompt, maxTokens) }
        return LlmResponse(
            text = text.ifBlank { null },
            toolExecutionRequests = emptyList(),
        )
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener,
    ): LlmResponse {
        val prompt = buildChatPrompt(messages)
        val text = runBlocking { LlamaEngine.complete(prompt, maxTokens) }
        if (text.isNotBlank()) {
            listener.onPartialText(text)
        }
        val response = LlmResponse(
            text = text.ifBlank { null },
            toolExecutionRequests = emptyList(),
        )
        listener.onComplete(response)
        return response
    }

    override fun close() {
        LlamaEngine.release()
    }

    private fun buildChatPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        for (msg in messages) {
            when (msg) {
                is SystemMessage -> {
                    sb.append("<|start_header_id|>system<|end_header_id|>\n${msg.text()}<|eot_id|>")
                }
                is UserMessage -> {
                    sb.append("<|start_header_id|>user<|end_header_id|>\n${msg.singleText()}<|eot_id|>")
                }
                is AiMessage -> {
                    sb.append("<|start_header_id|>assistant<|end_header_id|>\n${msg.text() ?: ""}<|eot_id|>")
                }
                is ToolExecutionResultMessage -> {
                    sb.append("<|start_header_id|>tool<|end_header_id|>\n${msg.text()}<|eot_id|>")
                }
            }
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n")
        return sb.toString()
    }
}
