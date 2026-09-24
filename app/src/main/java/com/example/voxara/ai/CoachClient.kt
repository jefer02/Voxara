package com.example.voxara.ai

import com.example.voxara.core.ai.CoachPrompt
import com.example.voxara.core.ai.CoachReply
import com.example.voxara.core.ai.CoachRequest
import com.example.voxara.core.ai.MiniJson
import com.example.voxara.core.ai.Validation
import com.example.voxara.core.ai.ReplyValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The seam every coach sits behind. Today: [OpenAiCompatibleCoachClient] (DeepSeek, debug only)
 * and the offline templates. Later: a server-side proxy that holds the key, with the same
 * interface — the watch never needs to change.
 */
interface CoachClient {
    suspend fun reply(request: CoachRequest): CoachResult
}

sealed interface CoachResult {
    data class Success(val reply: CoachReply) : CoachResult
    data class Failure(val reason: String) : CoachResult
}

/** HTTP in one small interface so tests use a fake and never reach a real endpoint. */
interface HttpTransport {
    /** POSTs [body] (JSON) with [headers]; returns status code and body, or throws [IOException]. */
    fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
) : HttpTransport {
    override fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = connectTimeoutMs
            c.readTimeout = readTimeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }
}

/**
 * OpenAI-compatible chat completions (DeepSeek by default: base URL and model come from
 * BuildConfig, i.e. local.properties). JSON output mode, strict token cap, every reply validated
 * against the snapshot before it is shown. The key is never logged.
 */
class OpenAiCompatibleCoachClient(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val http: HttpTransport = UrlConnectionTransport(),
) : CoachClient {

    fun requestBody(request: CoachRequest): String = MiniJson.write(
        linkedMapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to CoachPrompt.system(request.snapshot.locale)),
                mapOf("role" to "user", "content" to CoachPrompt.user(request)),
            ),
            "response_format" to mapOf("type" to "json_object"),
            "max_tokens" to CoachPrompt.MAX_TOKENS,
            "temperature" to 0.3,
            "stream" to false,
        )
    )

    override suspend fun reply(request: CoachRequest): CoachResult {
        if (apiKey.isBlank()) return CoachResult.Failure("no key")
        // Main-safe: callers launch from the UI thread, and a blocking socket there is a crash.
        val (code, body) = try {
            withContext(Dispatchers.IO) {
                http.postJson(
                    url = baseUrl.trimEnd('/') + "/chat/completions",
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                    body = requestBody(request),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return CoachResult.Failure("network")
        } catch (e: RuntimeException) {
            // e.g. SecurityException without INTERNET, a malformed base URL: the offline coach answers.
            return CoachResult.Failure("transport")
        }
        if (code !in 200..299) return CoachResult.Failure("http $code")
        val content = runCatching {
            val root = MiniJson.parse(body) as Map<*, *>
            val choice = (root["choices"] as List<*>).first() as Map<*, *>
            (choice["message"] as Map<*, *>)["content"] as String
        }.getOrNull() ?: return CoachResult.Failure("malformed response")
        return when (val v = ReplyValidator.validate(content, request.snapshot)) {
            is Validation.Valid -> CoachResult.Success(v.reply)
            is Validation.Invalid -> CoachResult.Failure("invalid reply: ${v.reason}")
        }
    }
}
