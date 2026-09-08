package im.hikaru.ruoyi.module.infra.framework.file.core.client.sftp

import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SftpFileClient(id: Long, config: SftpFileClientConfig) : AbstractFileClient<SftpFileClientConfig>(id, config) {
    override fun doInit() = Unit

    override fun upload(content: ByteArray, path: String, type: String): String {
        val filePath = remotePath(path)
        withChannel { channel ->
            createDirectories(channel, filePath.substringBeforeLast('/', ""))
            ByteArrayInputStream(content).use { channel.put(it, filePath) }
        }
        return formatFileUrl(config.domain, path)
    }

    override fun delete(path: String) {
        val filePath = remotePath(path)
        withChannel { channel ->
            try {
                channel.rm(filePath)
            } catch (ex: SftpException) {
                if (ex.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw ex
            }
        }
    }

    override fun getContent(path: String): ByteArray? {
        val filePath = remotePath(path)
        return withChannel { channel ->
            try {
                ByteArrayOutputStream().use { output ->
                    channel.get(filePath, output)
                    output.toByteArray()
                }
            } catch (ex: SftpException) {
                if (ex.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw ex
            }
        }
    }

    private fun remotePath(path: String): String {
        FilePathUtils.validatePath(path)
        return "${config.basePath.trimEnd('/')}/$path"
    }

    private fun <T> withChannel(block: (ChannelSftp) -> T): T {
        val session = JSch().getSession(config.username, config.host, requireNotNull(config.port)).apply {
            setPassword(config.password)
            setConfig("StrictHostKeyChecking", "no")
            connect(CONNECTION_TIMEOUT)
        }
        val channel = (session.openChannel("sftp") as ChannelSftp).apply { connect(SOCKET_TIMEOUT) }
        return try {
            block(channel)
        } finally {
            channel.disconnect()
            session.disconnect()
        }
    }

    private fun createDirectories(channel: ChannelSftp, directory: String) {
        if (directory.isEmpty() || directory == "/") return
        if (directory.startsWith('/')) channel.cd("/")
        directory.split('/').filter(String::isNotEmpty).forEach { segment ->
            try {
                channel.cd(segment)
            } catch (ex: SftpException) {
                if (ex.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw ex
                channel.mkdir(segment)
                channel.cd(segment)
            }
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 3_000
        private const val SOCKET_TIMEOUT = 10_000
    }
}
