package im.hikaru.harness.llm.koog.openai.providers

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No redirects, response bodies, request URLs, or credentials escape this boundary on failure. */
internal class ProviderDiscoveryTransport {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    suspend fun get(
        uri: URI,
        headers: Map<String, String>,
        timeoutMillis: Long,
        maxBytes: Int,
    ): DiscoveryHttpResponse {
        require(timeoutMillis > 0 && maxBytes > 0)
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .GET()
            .build()
        try {
            val response = suspendCancellableCoroutine<HttpResponse<ByteArray>> { continuation ->
                val future = client.sendAsync(request) { info ->
                    // Error responses are bounded too, but are never buffered or returned.
                    BoundedBodySubscriber(
                        maxBytes = if (info.statusCode() == 200) maxBytes else minOf(maxBytes, 65_536),
                        keepBody = info.statusCode() == 200,
                    )
                }
                continuation.invokeOnCancellation { future.cancel(true) }
                future.whenComplete { result, failure ->
                    if (failure == null) continuation.resume(result)
                    else continuation.resumeWithException(failure)
                }
            }
            return DiscoveryHttpResponse(response.statusCode(), response.body())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val exceeded = generateSequence<Throwable>(failure) { it.cause }
                .take(8).any { it is DiscoveryResponseTooLarge }
            throw ProviderDiscoveryFailure(
                if (exceeded) "RESPONSE_TOO_LARGE" else "NETWORK_ERROR",
                if (exceeded) "模型目录响应超过大小限制。" else "无法获取模型目录，请检查网络和服务地址。",
            )
        }
    }
}

internal data class DiscoveryHttpResponse(val status: Int, val bytes: ByteArray)

/** A stable, sanitized code and message; intentionally carries no original cause. */
public class ProviderDiscoveryFailure(public val code: String, message: String) : IllegalStateException(message)

private class DiscoveryResponseTooLarge : IllegalStateException()

/** Enforces the byte bound while streaming, before an untrusted body can be accumulated. */
private class BoundedBodySubscriber(private val maxBytes: Int, private val keepBody: Boolean) : HttpResponse.BodySubscriber<ByteArray> {
    private val body = CompletableFuture<ByteArray>()
    private val bytes = ByteArrayOutputStream()
    private var consumed = 0
    private var subscription: Flow.Subscription? = null

    override fun getBody(): CompletionStage<ByteArray> = body

    override fun onSubscribe(subscription: Flow.Subscription) {
        this.subscription = subscription
        subscription.request(1)
    }

    override fun onNext(items: List<ByteBuffer>) {
        if (body.isDone) return
        for (buffer in items) {
            if (buffer.remaining() > maxBytes - consumed) {
                subscription?.cancel()
                body.completeExceptionally(DiscoveryResponseTooLarge())
                return
            }
            consumed += buffer.remaining()
            if (keepBody) {
                val chunk = ByteArray(buffer.remaining())
                buffer.get(chunk)
                bytes.write(chunk)
            }
        }
        subscription?.request(1)
    }

    override fun onError(throwable: Throwable) { body.completeExceptionally(throwable) }
    override fun onComplete() { body.complete(bytes.toByteArray()) }
}
