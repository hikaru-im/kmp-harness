package im.hikaru.ruoyi.framework.encrypt.core.filter

import im.hikaru.ruoyi.framework.encrypt.config.ApiEncryptProperties
import im.hikaru.ruoyi.framework.encrypt.core.crypto.ApiEncryptor
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ApiEncryptResponseWrapper(response: HttpServletResponse) : HttpServletResponseWrapper(response) {
    private val body = ByteArrayOutputStream()
    private var outputStreamRequested = false
    private var writerRequested = false
    private var writer: PrintWriter? = null

    private val outputStream = object : ServletOutputStream() {
        override fun write(value: Int) = body.write(value)
        override fun write(bytes: ByteArray, offset: Int, length: Int) = body.write(bytes, offset, length)
        override fun isReady(): Boolean = true
        override fun setWriteListener(writeListener: WriteListener?) = Unit
    }

    override fun getOutputStream(): ServletOutputStream {
        check(!writerRequested) { "getWriter() 已被调用" }
        outputStreamRequested = true
        return outputStream
    }

    override fun getWriter(): PrintWriter {
        check(!outputStreamRequested) { "getOutputStream() 已被调用" }
        writerRequested = true
        return writer ?: PrintWriter(OutputStreamWriter(body, responseCharset())).also { writer = it }
    }

    override fun flushBuffer() {
        writer?.flush()
        outputStream.flush()
    }

    override fun resetBuffer() {
        body.reset()
    }

    override fun reset() {
        super.reset()
        body.reset()
    }

    fun encrypt(properties: ApiEncryptProperties, encryptor: ApiEncryptor) {
        flushBuffer()
        val encrypted = encryptor.encrypt(body.toByteArray()).toByteArray(StandardCharsets.UTF_8)
        val rawResponse = response as HttpServletResponse
        rawResponse.resetBuffer()
        rawResponse.setHeader(properties.header, "true")
        rawResponse.setHeader("Access-Control-Expose-Headers", properties.header)
        rawResponse.setContentLength(encrypted.size)
        rawResponse.outputStream.write(encrypted)
        rawResponse.outputStream.flush()
    }

    private fun responseCharset(): Charset = characterEncoding
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: StandardCharsets.UTF_8
}
