package im.hikaru.ruoyi.module.infra.framework.file.core.client.s3

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.time.Duration

class S3FileClient(id: Long, config: S3FileClientConfig) : AbstractFileClient<S3FileClientConfig>(id, config) {
    private var client: S3Client? = null
    private var presigner: S3Presigner? = null
    private lateinit var resolvedDomain: String

    override fun doInit() {
        close()
        resolvedDomain = config.domain?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: buildDomain()
        val credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.accessKey, config.accessSecret),
        )
        val region = Region.of(resolveRegion())
        val serviceConfig = S3Configuration.builder()
            .pathStyleAccessEnabled(config.enablePathStyleAccess == true)
            .chunkedEncodingEnabled(false)
            .build()
        client = S3Client.builder()
            .credentialsProvider(credentials)
            .region(region)
            .endpointOverride(URI.create(buildEndpoint()))
            .serviceConfiguration(serviceConfig)
            .build()
        presigner = S3Presigner.builder()
            .credentialsProvider(credentials)
            .region(region)
            .endpointOverride(URI.create(buildPresignerEndpoint()))
            .serviceConfiguration(serviceConfig)
            .build()
    }

    override fun upload(content: ByteArray, path: String, type: String): String {
        FilePathUtils.validatePath(path)
        val request = PutObjectRequest.builder()
            .bucket(config.bucket)
            .key(path)
            .contentType(type)
            .contentLength(content.size.toLong())
            .build()
        requireClient().putObject(request, RequestBody.fromBytes(content))
        return presignGetUrl(path, null)
    }

    override fun delete(path: String) {
        FilePathUtils.validatePath(path)
        requireClient().deleteObject(
            DeleteObjectRequest.builder().bucket(config.bucket).key(path).build(),
        )
    }

    override fun getContent(path: String): ByteArray {
        FilePathUtils.validatePath(path)
        val request = GetObjectRequest.builder().bucket(config.bucket).key(path).build()
        return requireClient().getObject(request).use { it.readBytes() }
    }

    override fun presignPutUrl(path: String): String {
        FilePathUtils.validatePath(path)
        val objectRequest = PutObjectRequest.builder().bucket(config.bucket).key(path).build()
        val request = PutObjectPresignRequest.builder()
            .signatureDuration(DEFAULT_EXPIRATION)
            .putObjectRequest(objectRequest)
            .build()
        return requirePresigner().presignPutObject(request).url().toString()
    }

    override fun presignGetUrl(url: String, expirationSeconds: Int?): String {
        val domainPrefix = "$resolvedDomain/"
        val isDomainUrl = url.startsWith(domainPrefix)
        var path = if (isDomainUrl) url.removePrefix(domainPrefix) else url
        if (isDomainUrl) {
            path = HttpUtils.decodeUrlPath(HttpUtils.removeUrlPathQueryAndFragment(path))
        }
        FilePathUtils.validatePath(path)
        if (config.enablePublicAccess != false) {
            return "$resolvedDomain/${HttpUtils.encodeUrlPath(path)}"
        }
        val objectRequest = GetObjectRequest.builder().bucket(config.bucket).key(path).build()
        val request = GetObjectPresignRequest.builder()
            .signatureDuration(expirationSeconds?.let { Duration.ofSeconds(it.toLong()) } ?: DEFAULT_EXPIRATION)
            .getObjectRequest(objectRequest)
            .build()
        return requirePresigner().presignGetObject(request).url().toString()
    }

    override fun close() {
        client?.close()
        presigner?.close()
        client = null
        presigner = null
    }

    private fun requireClient(): S3Client = checkNotNull(client) { "S3 client $id is not initialized" }

    private fun requirePresigner(): S3Presigner = checkNotNull(presigner) { "S3 presigner $id is not initialized" }

    private fun buildDomain(): String = if (isHttp(config.endpoint)) {
        "${config.endpoint.trimEnd('/')}/${config.bucket}"
    } else {
        "https://${config.bucket}.${config.endpoint}"
    }

    private fun buildEndpoint(): String = if (isHttp(config.endpoint)) config.endpoint else "https://${config.endpoint}"

    private fun buildPresignerEndpoint(): String {
        // Provider endpoints must be used for signing; custom domains are only for public access.
        if (config.endpoint.contains(S3FileClientConfig.ENDPOINT_ALIYUN) ||
            config.endpoint.contains(S3FileClientConfig.ENDPOINT_QINIU)
        ) {
            return buildEndpoint()
        }
        return if (config.enablePathStyleAccess == true) {
            resolvedDomain.removeSuffix("/${config.bucket}")
        } else {
            resolvedDomain.replace("://${config.bucket}.", "://")
        }
    }

    private fun resolveRegion(): String {
        config.region?.takeIf(String::isNotBlank)?.let { return it }
        val host = if (isHttp(config.endpoint)) runCatching { URI.create(config.endpoint).host }.getOrNull() else config.endpoint
        if (host.isNullOrBlank()) return DEFAULT_REGION
        if (host.contains("amazonaws.com")) {
            if (host.startsWith("s3.") && host.contains(".amazonaws.com")) {
                return host.substringAfter("s3.").substringBefore(".amazonaws.com").takeUnless { it == "accelerate" } ?: DEFAULT_REGION
            }
            return DEFAULT_REGION
        }
        if (host.startsWith("oss-") && host.contains(".${S3FileClientConfig.ENDPOINT_ALIYUN}")) {
            return host.substringAfter("oss-").substringBefore(".${S3FileClientConfig.ENDPOINT_ALIYUN}")
        }
        if (host.startsWith("cos.") && host.contains(".${S3FileClientConfig.ENDPOINT_TENCENT}")) {
            return host.substringAfter("cos.").substringBefore(".${S3FileClientConfig.ENDPOINT_TENCENT}")
        }
        return DEFAULT_REGION
    }

    private fun isHttp(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    companion object {
        private val DEFAULT_EXPIRATION: Duration = Duration.ofHours(24)
        private const val DEFAULT_REGION = "us-east-1"
    }
}
