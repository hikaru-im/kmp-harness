package im.hikaru.ruoyi.module.infra.framework.file.core.client

import im.hikaru.ruoyi.module.infra.framework.file.core.client.db.DBFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.db.DBFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.ftp.FtpFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.ftp.FtpFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.local.LocalFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.local.LocalFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.s3.S3FileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.s3.S3FileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.sftp.SftpFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.sftp.SftpFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.enums.FileStorageEnum
import java.util.concurrent.ConcurrentHashMap

class FileClientFactoryImpl : FileClientFactory {
    private val clients = ConcurrentHashMap<Long, AbstractFileClient<out FileClientConfig>>()

    override fun getFileClient(configId: Long): FileClient? = clients[configId]

    override fun createOrUpdateFileClient(configId: Long, storage: Int, config: FileClientConfig) {
        val storageType = requireNotNull(FileStorageEnum.fromStorage(storage)) { "Unknown file storage type: $storage" }
        clients.compute(configId) { _, current ->
            if (current != null && storageType.accepts(current) && storageType.configClass.isInstance(config)) {
                current.refresh(config)
                current
            } else {
                current?.close()
                createClient(configId, storageType, config).also(AbstractFileClient<*>::init)
            }
        }
    }

    private fun createClient(
        id: Long,
        storage: FileStorageEnum,
        config: FileClientConfig,
    ): AbstractFileClient<out FileClientConfig> = when (storage) {
        FileStorageEnum.DB -> DBFileClient(id, config.requireType())
        FileStorageEnum.LOCAL -> LocalFileClient(id, config.requireType())
        FileStorageEnum.FTP -> FtpFileClient(id, config.requireType())
        FileStorageEnum.SFTP -> SftpFileClient(id, config.requireType())
        FileStorageEnum.S3 -> S3FileClient(id, config.requireType())
    }

    private inline fun <reified T : FileClientConfig> FileClientConfig.requireType(): T =
        this as? T ?: throw IllegalArgumentException("Expected ${T::class.qualifiedName}, got ${this::class.qualifiedName}")
}
