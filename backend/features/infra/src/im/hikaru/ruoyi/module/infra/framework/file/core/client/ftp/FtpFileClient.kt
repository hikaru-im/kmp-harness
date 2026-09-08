package im.hikaru.ruoyi.module.infra.framework.file.core.client.ftp

import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class FtpFileClient(id: Long, config: FtpFileClientConfig) : AbstractFileClient<FtpFileClientConfig>(id, config) {
    override fun doInit() = Unit

    override fun upload(content: ByteArray, path: String, type: String): String {
        val filePath = remotePath(path)
        withClient { client ->
            createDirectories(client, filePath.substringBeforeLast('/', ""))
            val uploaded = ByteArrayInputStream(content).use { client.storeFile(filePath, it) }
            check(uploaded) { "Failed to upload file to FTP path $filePath: ${client.replyString}" }
        }
        return formatFileUrl(config.domain, path)
    }

    override fun delete(path: String) {
        val filePath = remotePath(path)
        withClient { it.deleteFile(filePath) }
    }

    override fun getContent(path: String): ByteArray? {
        val filePath = remotePath(path)
        return withClient { client ->
            ByteArrayOutputStream().use { output ->
                if (client.retrieveFile(filePath, output)) output.toByteArray() else null
            }
        }
    }

    private fun remotePath(path: String): String {
        FilePathUtils.validatePath(path)
        return "${config.basePath.trimEnd('/')}/$path"
    }

    private fun <T> withClient(block: (FTPClient) -> T): T {
        val client = FTPClient().apply {
            controlEncoding = StandardCharsets.UTF_8.name()
            connectTimeout = CONNECTION_TIMEOUT
            defaultTimeout = CONNECTION_TIMEOUT
        }
        try {
            client.connect(config.host, requireNotNull(config.port))
            check(FTPReply.isPositiveCompletion(client.replyCode)) { "FTP server rejected the connection: ${client.replyString}" }
            check(client.login(config.username, config.password)) { "FTP login failed: ${client.replyString}" }
            client.soTimeout = SOCKET_TIMEOUT
            client.setFileType(FTP.BINARY_FILE_TYPE)
            if (config.mode.equals("ACTIVE", ignoreCase = true)) {
                client.enterLocalActiveMode()
            } else {
                client.enterLocalPassiveMode()
            }
            return block(client)
        } finally {
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
        }
    }

    private fun createDirectories(client: FTPClient, directory: String) {
        if (directory.isEmpty() || directory == "/") return
        var current = if (directory.startsWith('/')) "/" else ""
        directory.split('/').filter(String::isNotEmpty).forEach { segment ->
            current = when (current) {
                "" -> segment
                "/" -> "/$segment"
                else -> "$current/$segment"
            }
            if (!client.changeWorkingDirectory(current)) {
                check(client.makeDirectory(current)) { "Failed to create FTP directory $current: ${client.replyString}" }
            }
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 3_000
        private const val SOCKET_TIMEOUT = 10_000
    }
}
