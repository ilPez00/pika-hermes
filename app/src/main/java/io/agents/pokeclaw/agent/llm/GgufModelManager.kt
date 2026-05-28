package io.agents.pokeclaw.agent.llm

import android.content.Context
import android.os.StatFs
import io.agents.pokeclaw.utils.XLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object GgufModelManager {

    private const val TAG = "GgufModelManager"
    private const val SIZE_TOLERANCE_BYTES = 32L * 1024L * 1024L

    data class GgufModelInfo(
        val id: String,
        val displayName: String,
        val url: String,
        val fileName: String,
        val sizeBytes: Long,
        val minRamGb: Int,
    )

    val AVAILABLE_GGUF_MODELS = listOf(
        // ── Recommended: fastest, fits 4GB+ RAM ──────────────────────────────
        GgufModelInfo(
            id = "gemma-2-2b-abliterated",
            displayName = "★ Gemma 2 2B Abliterated — 1.7GB [FASTEST]",
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-abliterated-GGUF/resolve/main/gemma-2-2b-it-abliterated-Q4_K_M.gguf",
            fileName = "gemma-2-2b-it-abliterated-Q4_K_M.gguf",
            sizeBytes = 1_708_582_784L,
            minRamGb = 4,
        ),
        GgufModelInfo(
            id = "phi-3.5-mini",
            displayName = "Phi-3.5 Mini 3.8B — 2.2GB [LIGHTWEIGHT]",
            url = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_200_000_000L,
            minRamGb = 4,
        ),
        GgufModelInfo(
            id = "gemma-4-e4b-uncensored",
            displayName = "Gemma 4 E4B Uncensored — 5.3GB",
            url = "https://huggingface.co/llmfan46/gemma-4-E4B-it-uncensored-GGUF/resolve/main/gemma-4-E4B-it-uncensored-Q4_K_M.gguf",
            fileName = "gemma-4-E4B-it-uncensored-Q4_K_M.gguf",
            sizeBytes = 5_335_285_280L,
            minRamGb = 8,
        ),
        // ── Larger, 8GB+ RAM ─────────────────────────────────────────────────
        GgufModelInfo(
            id = "gemma-4-e4b-heretic",
            displayName = "Gemma 4 E4B Heretic — 5.3GB [HERETIC]",
            url = "https://huggingface.co/llmfan46/gemma-4-E4B-it-ultra-uncensored-heretic-GGUF/resolve/main/gemma-4-E4B-it-ultra-uncensored-heretic-Q4_K_M.gguf",
            fileName = "gemma-4-E4B-it-ultra-uncensored-heretic-Q4_K_M.gguf",
            sizeBytes = 5_340_000_000L,
            minRamGb = 8,
        ),
        GgufModelInfo(
            id = "dolphin-llama3-8b",
            displayName = "Dolphin 2.9 Llama3 8B — 4.9GB [UNCENSORED]",
            url = "https://huggingface.co/bartowski/dolphin-2.9-llama3-8b-GGUF/resolve/main/dolphin-2.9-llama3-8b-Q4_K_M.gguf",
            fileName = "dolphin-2.9-llama3-8b-Q4_K_M.gguf",
            sizeBytes = 4_900_000_000L,
            minRamGb = 8,
        ),
        GgufModelInfo(
            id = "llama-3.2-3b-uncensored",
            displayName = "Llama 3.2 3B Uncensored — 2.0GB",
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-uncensored-GGUF/resolve/main/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf",
            sizeBytes = 2_000_000_000L,
            minRamGb = 6,
        ),
        GgufModelInfo(
            id = "qwen-uncensored",
            displayName = "Qwen Uncensored v2 — 2.7GB",
            url = "https://huggingface.co/bartowski/Qwen-uncensored-v2-abliterated-GGUF/resolve/main/Qwen-uncensored-v2-abliterated-Q4_K_M.gguf",
            fileName = "Qwen-uncensored-v2-abliterated-Q4_K_M.gguf",
            sizeBytes = 2_700_000_000L,
            minRamGb = 8,
        ),
    )

    /**
     * Scan known local paths for pre-existing GGUF files (e.g. pushed via ADB).
     * Returns extra GgufModelInfo entries for any found files not already in AVAILABLE_GGUF_MODELS.
     */
    fun scanLocalModels(context: android.content.Context): List<GgufModelInfo> {
        val known = AVAILABLE_GGUF_MODELS.map { it.fileName }.toSet()
        val scanDirs = listOf(
            File("/sdcard/pika-hermes/gguf"),
            File("/sdcard/pika-hermes"),
            File("/sdcard/Download"),
            ggufDir(context),
        )
        val found = mutableListOf<GgufModelInfo>()
        for (dir in scanDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles()?.filter { it.extension == "gguf" && it.name !in known }?.forEach { f ->
                found += GgufModelInfo(
                    id          = "local_${f.nameWithoutExtension}",
                    displayName = "[Local] ${f.nameWithoutExtension} — ${f.length() / 1_000_000_000L + 1}GB",
                    url         = "",
                    fileName    = f.name,
                    sizeBytes   = f.length(),
                    minRamGb    = 4,
                )
            }
        }
        return found
    }

    /** Full model list: catalog + locally scanned pre-existing files. */
    fun allModels(context: android.content.Context): List<GgufModelInfo> =
        AVAILABLE_GGUF_MODELS + scanLocalModels(context)

    private fun ggufDir(context: Context): File {
        val base = LocalModelManager.getModelDir(context)
        val dir = File(base, "gguf")
        if (dir.isDirectory || dir.mkdirs()) return dir
        return base
    }

    fun isModelDownloaded(context: Context, model: GgufModelInfo): Boolean {
        val file = File(ggufDir(context), model.fileName)
        return file.exists() && file.length() in expectedLowerBound(model)..expectedUpperBound(model)
    }

    fun getModelPath(context: Context, model: GgufModelInfo): String? {
        val file = File(ggufDir(context), model.fileName)
        return if (file.exists() && file.length() in expectedLowerBound(model)..expectedUpperBound(model))
            file.absolutePath else null
    }

    fun deleteModel(context: Context, model: GgufModelInfo) {
        val file = File(ggufDir(context), model.fileName)
        val tempFile = File(ggufDir(context), "${model.fileName}.downloading")
        tempFile.delete()
        file.delete()
    }

    interface DownloadCallback {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long)
        fun onComplete(modelPath: String)
        fun onError(error: String)
    }

    fun downloadModel(
        context: Context,
        model: GgufModelInfo,
        callback: DownloadCallback
    ) {
        val dir = ggufDir(context)
        val targetFile = File(dir, model.fileName)
        val tempFile = File(dir, "${model.fileName}.downloading")

        if (targetFile.exists() && !isValidSize(targetFile, model)) targetFile.delete()
        if (tempFile.exists() && tempFile.length() > expectedUpperBound(model)) tempFile.delete()

        try {
            val stat = StatFs(dir.absolutePath)
            val availableBytes = stat.availableBytes
            val existingTempBytes = if (tempFile.exists()) tempFile.length() else 0L
            val bytesNeeded = model.sizeBytes - existingTempBytes
            if (bytesNeeded > 0 && availableBytes < bytesNeeded) {
                callback.onError("Not enough storage. Need ${bytesNeeded / 1_000_000}MB, have ${availableBytes / 1_000_000}MB")
                return
            }
        } catch (_: Exception) {}

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            val requestBuilder = Request.Builder().url(model.url)
            if (existingBytes > 0) requestBuilder.addHeader("Range", "bytes=$existingBytes-")

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                callback.onError("Download failed: HTTP ${response.code}")
                return
            }

            val isResumed = existingBytes > 0 && response.code == 206
            if (existingBytes > 0 && !isResumed) {
                tempFile.delete()
            }

            val totalBytes = if (isResumed) {
                response.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull() ?: model.sizeBytes
            } else {
                response.body?.contentLength() ?: model.sizeBytes
            }

            val body = response.body ?: run {
                callback.onError("Empty response body")
                return
            }

            val startingBytes = if (isResumed) existingBytes else 0L
            val outputStream = FileOutputStream(tempFile, isResumed)
            val buffer = ByteArray(8192)
            var downloadedBytes = startingBytes
            var lastReportTime = System.currentTimeMillis()
            var lastReportedBytes = startingBytes

            body.byteStream().use { input ->
                outputStream.use { output ->
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 200) {
                            val elapsed = (now - lastReportTime) / 1000.0
                            val speed = ((downloadedBytes - lastReportedBytes) / elapsed).toLong()
                            callback.onProgress(downloadedBytes, totalBytes, speed)
                            lastReportTime = now
                            lastReportedBytes = downloadedBytes
                        }
                    }
                }
            }

            if (!isValidSize(tempFile, model)) {
                tempFile.delete()
                callback.onError("Downloaded file looks incomplete. Please retry.")
                return
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                callback.onError("Could not move model file into place")
                return
            }

            XLog.i(TAG, "GGUF model downloaded: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            callback.onComplete(targetFile.absolutePath)

        } catch (e: Exception) {
            XLog.e(TAG, "GGUF download failed", e)
            callback.onError("Download failed: ${e.message}")
        }
    }

    fun isSupportedOnDevice(context: Context, model: GgufModelInfo): Boolean {
        val ramGb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem / (1024L * 1024L * 1024L)).toInt() + 1
        } catch (_: Exception) { 4 }
        return ramGb >= model.minRamGb
    }

    private fun isValidSize(file: File, model: GgufModelInfo): Boolean {
        if (!file.exists()) return false
        val len = file.length()
        return len >= expectedLowerBound(model) && len <= expectedUpperBound(model)
    }

    private fun expectedLowerBound(model: GgufModelInfo): Long =
        (model.sizeBytes - maxOf(SIZE_TOLERANCE_BYTES, model.sizeBytes / 20)).coerceAtLeast(1L)
    private fun expectedUpperBound(model: GgufModelInfo): Long =
        model.sizeBytes + maxOf(SIZE_TOLERANCE_BYTES, model.sizeBytes / 20)
}
