package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.llm.*
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** Explicit vendor discovery. It never tests another vendor, region, or guessed fallback endpoint. */
public class OfficialProviderModelDiscovery(
    private val vendor: String,
    private val limits: ProviderDiscoveryLimits = ProviderDiscoveryLimits(),
) : LlmModelDiscovery {
    override suspend fun discover(access: LlmProviderAccess): LlmCatalogSnapshot {
        if (access.connection.vendor != vendor) fail("INVALID_CONNECTION", "连接与所选厂商不匹配。")
        if (vendor == "zhipu") return bundledGlmCatalog()
        val endpoint = endpoint(access.connection)
        val key = access.apiKey?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
            ?: fail("MISSING_CREDENTIAL", "请为所选连接提供 API key。")
        val headers = buildMap {
            if (vendor == "google") put("x-goog-api-key", key) else put("Authorization", "Bearer $key")
            if (vendor == "anthropic") {
                val version = access.connection.apiVersion ?: "2023-06-01"
                if (!version.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                    fail("INVALID_CONNECTION", "API version 格式无效。")
                put("anthropic-version", version)
                access.connection.workspace?.takeIf(String::isNotBlank)?.let {
                    if (it.any { character -> character == '\r' || character == '\n' })
                        fail("INVALID_CONNECTION", "Workspace ID 格式无效。")
                    put("anthropic-workspace-id", it)
                }
            }
        }
        val models = linkedMapOf<String, LlmDiscoveredModel>()
        var completedPages = 0
        val result = withTimeoutOrNull(limits.timeoutMillis) {
            val transport = ProviderDiscoveryTransport()
            var cursor: String? = null
            var page = 1
            var records = 0L
            var bytes = 0L
            val seenCursors = mutableSetOf<String>()
            while (true) {
                try {
                    val uri = pageUri(endpoint, cursor, page)
                    val response = transport.get(uri, headers, limits.timeoutMillis, limits.maxResponseBytes)
                    when (response.status) {
                        200 -> Unit
                        401, 403 -> fail("AUTHENTICATION", "模型目录请求被拒绝，请检查密钥、workspace 与访问权限。")
                        404 -> fail("NOT_FOUND", "所选服务地址未提供此模型目录；请检查入口或手动配置模型。")
                        429 -> fail("RATE_LIMITED", "模型目录请求过于频繁，请稍后重试。")
                        else -> fail("HTTP_ERROR", "获取模型目录失败（HTTP ${response.status}）。")
                    }
                    bytes += response.bytes.size
                    if (bytes > limits.maxCatalogBytes) return@withTimeoutOrNull partial(models, "CATALOG_SIZE_LIMIT")
                    val parsed = ProviderCatalogParser.parse(vendor, response.bytes.toString(Charsets.UTF_8))
                    if (vendor == "alibaba" && (parsed.total == null || parsed.pageNumber != page.toLong()))
                        fail("INVALID_RESPONSE", "服务返回的目录分页信息无效。")
                    val oldSize = models.size
                    for (model in parsed.models) {
                        if (model.id !in models && models.size >= limits.maxModels)
                            return@withTimeoutOrNull partial(models, "MODEL_COUNT_LIMIT")
                        models.putIfAbsent(model.id, model)
                    }
                    records += parsed.models.size
                    completedPages++
                    val hasMore = when (vendor) {
                        "alibaba" -> records < requireNotNull(parsed.total)
                        "anthropic", "google" -> parsed.cursor != null
                        else -> false
                    }
                    if (parsed.unsupportedPagination) return@withTimeoutOrNull partial(models, "UNSUPPORTED_PAGINATION")
                    if (!hasMore) return@withTimeoutOrNull snapshot(models, LlmCatalogCompleteness.COMPLETE)
                    if (completedPages >= limits.maxPages) return@withTimeoutOrNull partial(models, "PAGE_COUNT_LIMIT")
                    if (parsed.models.isEmpty() || models.size == oldSize)
                        return@withTimeoutOrNull partial(models, "PAGINATION_NO_PROGRESS")
                    cursor = parsed.cursor
                    if (cursor != null && (!seenCursors.add(cursor) || cursor.length > 4096))
                        return@withTimeoutOrNull partial(models, "INVALID_PAGINATION_CURSOR")
                    page++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ProviderDiscoveryFailure) {
                    if (completedPages == 0) throw failure
                    return@withTimeoutOrNull partial(models, failure.code)
                } catch (_: Exception) {
                    if (completedPages == 0) fail("INVALID_RESPONSE", "无法读取模型目录，请检查连接配置与响应格式。")
                    return@withTimeoutOrNull partial(models, "INVALID_RESPONSE")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }
        return result ?: if (completedPages > 0) partial(models, "TIMEOUT")
            else fail("TIMEOUT", "获取模型目录超时，请检查连接后重试。")
    }

    private fun snapshot(models: Map<String, LlmDiscoveredModel>, completeness: LlmCatalogCompleteness): LlmCatalogSnapshot =
        LlmCatalogSnapshot(models.values.toList(), "remote:$vendor", completeness)

    private fun partial(models: Map<String, LlmDiscoveredModel>, reason: String): LlmCatalogSnapshot =
        snapshot(models, LlmCatalogCompleteness.PARTIAL).copy(warnings = listOf("目录不完整（$reason）；不能替换完整缓存。"))

    private fun pageUri(endpoint: URI, cursor: String?, page: Int): URI {
        val query = when (vendor) {
            "anthropic" -> "limit=1000" + (cursor?.let { "&after_id=${encode(it)}" } ?: "")
            "google" -> "pageSize=1000" + (cursor?.let { "&pageToken=${encode(it)}" } ?: "")
            "alibaba" -> "page_no=$page&page_size=100"
            else -> ""
        }
        return if (query.isEmpty()) endpoint else URI("$endpoint?$query")
    }

    /** baseUrl is explicit caller input or a selected profile; only path suffixes are normalized. */
    internal fun endpoint(connection: LlmProviderConnection): URI {
        try {
            var base = connection.baseUrl.trim().trimEnd('/')
            if ("{workspace}" in base || "{WorkspaceId}" in base) {
                val workspace = connection.workspace
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,127}")) }
                    ?: fail("MISSING_WORKSPACE", "所选地区需要有效的 Workspace ID。")
                base = base.replace("{workspace}", workspace).replace("{WorkspaceId}", workspace)
            }
            val uri = URI(base)
            require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null)
            // Plain HTTP is only accepted for explicitly configured local fixtures/development services.
            require(uri.scheme == "https" || uri.host in setOf("localhost", "127.0.0.1", "::1", "[::1]"))
            val suffix = when (vendor) {
                "openai", "moonshot" -> if (base.endsWith("/v1")) "/models" else "/v1/models"
                "anthropic" -> if (base.endsWith("/v1")) "/models" else "/v1/models"
                "google" -> if (base.endsWith("/v1beta") || base.endsWith("/v1")) "/models" else "/v1beta/models"
                "deepseek" -> "/models"
                "alibaba" -> {
                    base = base.removeSuffix("/compatible-mode/v1").removeSuffix("/api/v1")
                    // Official workspace domains and legacy regional hosts both use the native catalog path.
                    "/api/v1/models"
                }
                else -> fail("UNSUPPORTED_VENDOR", "该厂商未注册模型目录接口。")
            }
            return URI(base + suffix)
        } catch (failure: ProviderDiscoveryFailure) {
            throw failure
        } catch (_: Exception) {
            fail("INVALID_ENDPOINT", "服务地址无效；请使用 HTTPS，且不要在地址中包含凭据、查询参数或片段。")
        }
    }

    private fun bundledGlmCatalog(): LlmCatalogSnapshot = LlmCatalogSnapshot(
        models = listOf("glm-5.3", "glm-5.3-flash", "glm-5.3-flashx", "glm-5.2").map { id ->
            LlmDiscoveredModel(id = id, name = id.uppercase(), metadata = buildJsonObject {
                put("vendor", "zhipu")
                put("catalog_version", "2026-10-21")
                put("account_availability", "unverified")
                put("source_url", "https://docs.bigmodel.cn/cn/guide/start/model-overview")
            })
        },
        source = "bundled:zhipu:2026-10-21",
        completeness = LlmCatalogCompleteness.UNKNOWN,
        warnings = listOf("公开模型发现契约尚未确认。当前为官方文档预置目录，密钥、地区、套餐及模型调用权限均未验证；可手工填写模型 ID。"),
    )

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    private fun fail(code: String, message: String): Nothing = throw ProviderDiscoveryFailure(code, message)
}

public data class ProviderDiscoveryLimits(
    public val timeoutMillis: Long = 30_000,
    public val maxResponseBytes: Int = 2_000_000,
    public val maxCatalogBytes: Long = 16_000_000,
    public val maxModels: Int = 5_000,
    public val maxPages: Int = 100,
) {
    init { require(timeoutMillis > 0 && maxResponseBytes > 0 && maxCatalogBytes > 0 && maxModels > 0 && maxPages > 0) }
}
