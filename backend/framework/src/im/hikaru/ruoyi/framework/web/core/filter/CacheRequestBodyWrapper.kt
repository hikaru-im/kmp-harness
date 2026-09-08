package im.hikaru.ruoyi.framework.web.core.filter

import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class CacheRequestBodyWrapper(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    private val body: ByteArray = ServletUtils.getBodyBytes(request) ?: ByteArray(0)
    private val charset: Charset = request.characterEncoding
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: StandardCharsets.UTF_8

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, charset))

    override fun getContentLength(): Int = body.size

    override fun getContentLengthLong(): Long = body.size.toLong()

    override fun getInputStream(): ServletInputStream {
        val input = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun read(): Int = input.read()

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = input.read(bytes, offset, length)

            override fun available(): Int = input.available()

            override fun isFinished(): Boolean = input.available() == 0

            override fun isReady(): Boolean = true

            override fun setReadListener(readListener: ReadListener?) = Unit
        }
    }
}
