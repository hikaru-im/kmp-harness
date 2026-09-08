package im.hikaru.ruoyi.module.infra.framework.file.core.enums

import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
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

enum class FileStorageEnum(
    val storage: Int,
    val configClass: Class<out FileClientConfig>,
    private val clientClass: Class<out AbstractFileClient<out FileClientConfig>>,
) {
    DB(1, DBFileClientConfig::class.java, DBFileClient::class.java),
    LOCAL(10, LocalFileClientConfig::class.java, LocalFileClient::class.java),
    FTP(11, FtpFileClientConfig::class.java, FtpFileClient::class.java),
    SFTP(12, SftpFileClientConfig::class.java, SftpFileClient::class.java),
    S3(20, S3FileClientConfig::class.java, S3FileClient::class.java),
    ;

    fun accepts(client: AbstractFileClient<out FileClientConfig>): Boolean = clientClass.isInstance(client)

    companion object {
        fun fromStorage(storage: Int?): FileStorageEnum? = entries.firstOrNull { it.storage == storage }
    }
}
