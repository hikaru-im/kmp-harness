package im.hikaru.harness.llm.koog.openai.client

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionStreamResponse
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.koog.openai.semantics.OpenAiToolDescriptorSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

/** Koog 1.1.1 drops Chat reasoning_content during decoding. Retain it at the raw SSE boundary. */
internal class ReasoningOpenAiClient(apiKey: String, settings: OpenAIClientSettings, factory: KoogHttpClient.Factory) :
    OpenAILLMClient(apiKey, settings, ResponsesCompatibilityFactory(factory), toolsConverter = OpenAiToolDescriptorSchemaGenerator()) {
    // Decode/process can run concurrently for separate calls. Weak keys also release cancelled decodes.
    private val reasoning = WeakIdentityReasoning()

    override fun decodeStreamingResponse(data: String): OpenAIChatCompletionStreamResponse {
        val response = super.decodeStreamingResponse(data)
        val deltas = Json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray.orEmpty().mapNotNull { element ->
            val choice = element.jsonObject
            val text = choice["delta"]?.jsonObject?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
            text?.takeIf { it.isNotEmpty() }?.let { StreamFrame.ReasoningDelta(text = it, index = choice["index"]?.jsonPrimitive?.intOrNull) }
        }
        if (deltas.isNotEmpty()) reasoning.put(response, deltas)
        return response
    }

    override fun processStreamingResponse(response: Flow<OpenAIChatCompletionStreamResponse>): Flow<StreamFrame> = channelFlow {
        val withReasoning = response.onEach { chunk ->
            val deltas = reasoning.remove(chunk).orEmpty()
            deltas.forEach { send(it) }
        }
        super.processStreamingResponse(withReasoning).collect { send(it) }
    }
}

private class WeakIdentityReasoning {
    private val queue = ReferenceQueue<OpenAIChatCompletionStreamResponse>()
    private val values = mutableMapOf<IdentityReference, List<StreamFrame.ReasoningDelta>>()
    @Synchronized fun put(response: OpenAIChatCompletionStreamResponse, frames: List<StreamFrame.ReasoningDelta>) {
        drain()
        values[IdentityReference(response, queue)] = frames
    }
    @Synchronized fun remove(response: OpenAIChatCompletionStreamResponse): List<StreamFrame.ReasoningDelta>? {
        drain()
        return values.remove(IdentityReference(response, null))
    }
    private fun drain() {
        while (true) values.remove((queue.poll() ?: return) as IdentityReference)
    }
    private class IdentityReference(value: OpenAIChatCompletionStreamResponse, queue: ReferenceQueue<OpenAIChatCompletionStreamResponse>?) :
        WeakReference<OpenAIChatCompletionStreamResponse>(value, queue) {
        private val identityHash = System.identityHashCode(value)
        override fun hashCode(): Int = identityHash
        override fun equals(other: Any?): Boolean = this === other ||
            other is IdentityReference && get()?.let { it === other.get() } == true
    }
}

/** The official response uses instructions:string|null; this SDK version expects message[]. */
private class ResponsesCompatibilityFactory(private val delegate: KoogHttpClient.Factory) : KoogHttpClient.Factory {
    override fun create(clientName: String, baseUrl: String, headers: Map<String, String>, queryParameters: Map<String, String>,
        requestTimeoutMillis: Long, connectTimeoutMillis: Long, socketTimeoutMillis: Long, json: Json): KoogHttpClient {
        val client = delegate.create(clientName, baseUrl, headers, queryParameters, requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json)
        return object : KoogHttpClient by client {
            override fun <T : Any, R : Any, O : Any> sse(path: String, requestBody: T, requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean, decodeStreamingResponse: (String) -> R, processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>, headers: Map<String, String>): Flow<O> = client.sse(
                    path, requestBody, requestBodyType, dataFilter,
                    { data -> decodeStreamingResponse(if (path.endsWith("responses")) normalizeInstructions(data) else data) },
                    processStreamingChunk, parameters, headers)
        }
    }
}

private fun normalizeInstructions(data: String): String {
    val root = Json.parseToJsonElement(data) as? JsonObject ?: return data
    val response = root["response"] as? JsonObject ?: return data
    val instructions = response["instructions"]
    if (instructions !is JsonPrimitive) return data
    // Harness owns the original prompt; response.instructions is not used to build history.
    val compatible = JsonObject(response + ("instructions" to JsonArray(emptyList())))
    return JsonObject(root + ("response" to compatible)).toString()
}
