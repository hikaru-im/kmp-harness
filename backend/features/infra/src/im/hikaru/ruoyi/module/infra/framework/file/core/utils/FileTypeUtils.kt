package im.hikaru.ruoyi.module.infra.framework.file.core.utils

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import jakarta.servlet.http.HttpServletResponse
import org.apache.tika.Tika
import org.apache.tika.mime.MimeTypeException
import org.apache.tika.mime.MimeTypes
import org.slf4j.LoggerFactory

object FileTypeUtils {
    private val tika = Tika()
    private val log = LoggerFactory.getLogger(FileTypeUtils::class.java)

    @JvmStatic
    fun getMimeType(data: ByteArray): String = tika.detect(data)

    @JvmStatic
    fun getMimeType(name: String): String = tika.detect(name)

    @JvmStatic
    fun getMimeType(data: ByteArray, name: String?): String = tika.detect(data, name)

    @JvmStatic
    fun getMineType(data: ByteArray, name: String?): String = getMimeType(data, name)

    @JvmStatic
    fun getExtension(mimeType: String): String? = try {
        MimeTypes.getDefaultMimeTypes().forName(mimeType).extension
    } catch (ex: MimeTypeException) {
        log.warn("[getExtension][cannot resolve extension for {}]", mimeType, ex)
        null
    }

    @JvmStatic
    fun writeAttachment(response: HttpServletResponse, filename: String?, content: ByteArray) {
        val safeName = filename?.takeIf(String::isNotEmpty) ?: "download"
        val mimeType = getMimeType(content, safeName)
        response.contentType = mimeType
        val disposition = if (isImage(mimeType)) "inline" else "attachment"
        response.setHeader(
            "Content-Disposition",
            "$disposition;filename=\"${fallbackFilename(safeName)}\";filename*=UTF-8''${HttpUtils.encodeUrlPathSegment(safeName)}",
        )
        if (mimeType.contains("video", ignoreCase = true)) {
            response.setHeader("Accept-Ranges", "bytes")
            response.setHeader("Content-Length", content.size.toString())
        }
        response.outputStream.write(content)
        response.outputStream.flush()
    }

    @JvmStatic
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun fallbackFilename(filename: String): String = buildString(filename.length) {
        filename.forEach { ch ->
            when {
                ch == '"' || ch == '\\' -> append('\\').append(ch)
                ch.code in 0x20..0x7e -> append(ch)
                else -> append('_')
            }
        }
    }
}
