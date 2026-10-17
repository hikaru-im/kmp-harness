package im.hikaru.harness.llm.koog.openai.client

import im.hikaru.harness.llm.koog.KoogModelProfile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** One explicit discovery request; credentials and server error bodies never leave this boundary. */
public class OpenAiModelDiscovery {
    public suspend fun fetch(baseUrl: String?, apiKey: String, timeoutMillis: Long = 30_000): List<KoogModelProfile> {
        try {
            val base = (baseUrl ?: OpenAiPromptExecutorFactory.DEFAULT_BASE_URL).trimEnd('/')
            val uri = URI(base)
            require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null)
            val endpoints = if (base.endsWith("/v1")) listOf("$base/models")
                else listOf("$base/v1/models", "$base/models")
            return withTimeoutOrNull(timeoutMillis) { runInterruptible(Dispatchers.IO) {
                val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofMillis(timeoutMillis)).build()
                var result: List<KoogModelProfile>? = null
                for ((index, endpoint) in endpoints.withIndex()) {
                    val request = HttpRequest.newBuilder(URI(endpoint)).timeout(Duration.ofMillis(timeoutMillis))
                        .header("Authorization", "Bearer $apiKey").header("Accept", "application/json").GET().build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                    response.body().use { body ->
                        when (response.statusCode()) {
                            200 -> {
                                val bytes = body.readNBytes(2_000_001)
                                if (bytes.size > 2_000_000) throw ModelDiscoveryException("模型列表响应过大，原列表未更改。")
                                result = parse(bytes.toString(Charsets.UTF_8))
                            }
                            401, 403 -> throw ModelDiscoveryException("获取模型失败：凭据无效或没有访问权限。")
                            404 -> if (index == endpoints.lastIndex) throw ModelDiscoveryException("此服务未提供兼容的模型列表接口，请手动配置模型。")
                            429 -> throw ModelDiscoveryException("获取模型请求过于频繁，请稍后重试。")
                            else -> throw ModelDiscoveryException("获取模型失败（HTTP ${response.statusCode()}），原列表未更改。")
                        }
                    }
                    if (result != null) break
                }
                requireNotNull(result)
            } } ?: throw ModelDiscoveryException("获取模型超时，请检查连接或增加请求超时。")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ModelDiscoveryException) {
            throw failure
        } catch (_: java.net.http.HttpTimeoutException) {
            throw ModelDiscoveryException("获取模型超时，请检查连接或增加请求超时。")
        } catch (_: Exception) {
            throw ModelDiscoveryException("无法获取模型列表，请检查服务地址、网络和响应格式。")
        }
    }

    internal fun parse(body: String): List<KoogModelProfile> {
        val root = Json.parseToJsonElement(body).jsonObject
        if ((root["has_more"] as? JsonPrimitive)?.booleanOrNull == true) {
            throw ModelDiscoveryException("服务返回分页模型目录，暂不支持自动导入，请手动配置模型。")
        }
        val entries = root["data"] as? JsonArray ?: error("Invalid model list")
        val models = entries.map { entry ->
            val model = entry.jsonObject
            val id = (model["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            require(!id.isNullOrBlank() && id.none(Char::isWhitespace) && id.length <= 512)
            val name = (model["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            val context = (model["context_window"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            // Output limits are capabilities, not a default max_tokens request. Do not conflate them.
            KoogModelProfile(id = id, name = name, contextWindow = context)
        }.distinctBy { it.id }
        if (models.isEmpty()) throw ModelDiscoveryException("服务返回空模型列表，已保留原列表。")
        return models
    }
}

public class ModelDiscoveryException(message: String) : IllegalStateException(message)
