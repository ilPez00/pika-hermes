// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.agent.AgentConfig
import io.agents.pokeclaw.agent.DefaultAgentService
import io.agents.pokeclaw.agent.LlmProvider
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientBuilderAdapter
import io.agents.pokeclaw.agent.llm.llama.LlamaBackend

object LlmClientFactory {

    fun create(config: AgentConfig): LlmClient {
        val httpClientBuilder = OkHttpClientBuilderAdapter().apply {
            if (DefaultAgentService.FILE_LOGGING_ENABLED && DefaultAgentService.FILE_LOGGING_CACHE_DIR != null) {
                setFileLoggingEnabled(true, DefaultAgentService.FILE_LOGGING_CACHE_DIR)
            }
        }
        return when (config.provider) {
            // For cloud providers: wrap in cascade so failed keys auto-retry next provider
            LlmProvider.OPENAI -> {
                if (CascadingLlmClient.hasAnyKey()) {
                    CascadingLlmClient(httpClientBuilder, config)
                } else {
                    OpenAiLlmClient(config, httpClientBuilder)
                }
            }
            LlmProvider.ANTHROPIC -> AnthropicLlmClient(config, httpClientBuilder)
            LlmProvider.LOCAL     -> LocalLlmClient(config)
            LlmProvider.LLAMA     -> {
                val modelPath = config.baseUrl
                if (modelPath.isNotEmpty() && java.io.File(modelPath).exists()) {
                    LlamaBackend(modelPath = modelPath, nCtx = 4096, maxTokens = 2048)
                } else {
                    // No valid GGUF path — fall back to cascade / cloud
                    if (CascadingLlmClient.hasAnyKey()) {
                        CascadingLlmClient(httpClientBuilder, config)
                    } else {
                        OpenAiLlmClient(config, httpClientBuilder)
                    }
                }
            }
        }
    }
}
