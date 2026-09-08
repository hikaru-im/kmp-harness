package im.hikaru.ruoyi.module.infra.framework.file.core.client

interface FileClient : AutoCloseable {
    val id: Long

    fun upload(content: ByteArray, path: String, type: String): String

    fun delete(path: String)

    fun getContent(path: String): ByteArray?

    fun presignPutUrl(path: String): String =
        throw UnsupportedOperationException("This storage does not support presigned uploads")

    fun presignGetUrl(url: String, expirationSeconds: Int?): String =
        throw UnsupportedOperationException("This storage does not support presigned downloads")

    override fun close() = Unit
}
