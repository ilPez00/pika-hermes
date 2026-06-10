# pika-hermes

**Uncensored Android AI phone agent.** Runs any GGUF model locally via llama.cpp, or falls back to a 40-provider cloud cascade (DeepSeek → Groq → Gemini × 21 → OpenRouter → Mistral → keyless proxies → public Ollama). No topic is off-limits. No action is refused.

---

## What it does

- **Local inference** — drop any GGUF model onto the device and it runs entirely on-device via llama.cpp b4867 (arm64 NEON + dotprod, flash attention)
- **Cloud cascade** — if no GGUF is loaded, it cascades through every configured provider automatically; dead keys are back-off (401→1 h, 429→5 min) so it never stalls on a burnt key
- **Phone control** — reads the screen, taps, types, opens apps, replies to messages, runs tasks in the background
- **Uncensored** — no content policy enforced at the app layer; the model is the only limit

## Quick start

### 1. Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/*.apk
```

Requires Android 9+ (API 28), arm64-v8a or x86_64.

### 2. Push API keys (optional — needed for cloud cascade)

Create `pika-hermes.env` in the app's external dir or on the SD card:

```bash
adb push pika-hermes.env /sdcard/Android/data/io.agents.pokeclaw/files/pika-hermes.env
```

Supported keys:

```env
AI_DEEPSEEK_KEY=sk-...
AI_GROQ_KEY=gsk_...
AI_GEMINI_KEY=AIza...,AIza...,AIza...    # comma-separated for 21-key rotation
AI_OPENROUTER_KEY=sk-or-...
AI_MISTRAL_KEY=...
```

Then open **Settings → Import .env** in the app, or it auto-imports on launch if the file is present.

### 3. Download a model (optional — needed for local/offline inference)

Open **Settings → Local Model → Download** and pick one:

| Model | Size | RAM | Notes |
|---|---|---|---|
| ★ Gemma 2 2B Abliterated | 1.7 GB | 4 GB+ | Fastest, best for 4-6 GB RAM devices |
| Phi-3.5 Mini 3.8B | 2.4 GB | 4 GB+ | Lightweight, good reasoning |
| Llama 3.2 3B Uncensored | 2.0 GB | 6 GB+ | Uncensored |
| Gemma 4 E4B Uncensored | 5.3 GB | 8 GB+ | Larger context, more capable |
| Dolphin 2.9 Llama3 8B | 4.9 GB | 8 GB+ | Strong general use |

Or push a GGUF directly via ADB:

```bash
adb push my-model.gguf /sdcard/Android/data/io.agents.pokeclaw/files/models/gguf/my-model.gguf
```

The app auto-detects any `.gguf` file in that directory.

## Architecture

```
pika-hermes
├── agent/
│   ├── llm/
│   │   ├── llama/          ← llama.cpp JNI backend (LlamaBackend + LlamaEngine)
│   │   ├── CascadingLlmClient.kt   ← 40-provider cloud cascade with dead-key backoff
│   │   ├── GgufModelManager.kt     ← model catalog + download + local scan
│   │   └── EnvImporter.kt          ← .env parser → KVUtils store
│   └── DefaultAgentService.kt      ← main agent loop + tool execution
├── cpp/
│   ├── llama_jni.cpp       ← JNI bridge (nativeLoadModel / nativeComplete / nativeRelease)
│   └── CMakeLists.txt      ← downloads llama.cpp b4867 at build time
└── ui/
    ├── chat/               ← chat session controller (LLAMA/LOCAL/CLOUD routing)
    └── settings/           ← model download UI, key import, provider config
```

## Provider cascade order

1. **DeepSeek** — cheapest, fast
2. **Groq** — free tier, fast
3. **Gemini** — 21-key rotation (free tier × 21)
4. **OpenRouter** — multi-model gateway
5. **Mistral** — EU-hosted
6. **Keyless proxies** — community endpoints
7. **Public Ollama** — local network / desktop bridge

## Permissions required

- `RECORD_AUDIO` — voice input
- `BIND_ACCESSIBILITY_SERVICE` — screen reading and tap automation
- `BIND_NOTIFICATION_LISTENER_SERVICE` — notification monitoring and auto-reply
- `FOREGROUND_SERVICE` — background task execution
- `INTERNET` — cloud API calls

## Changelog

### v0.1.0 — 2026-06-10

First public release of pika-hermes.

- Local GGUF inference via llama.cpp b4867 (arm64 NEON + dotprod, flash attention)
- 40-provider cloud cascade (DeepSeek → Groq → Gemini × 21 → OpenRouter → Mistral → keyless proxies → public Ollama)
- Dead-key backoff (401→1 h, 429→5 min, 503→1 min)
- .env file import for API keys
- In-app model download and management
- Phone control via accessibility service
- Uncensored — no content policy at the app layer

## License

Apache 2.0 — see [LICENSE](LICENSE)
