package im.hikaru.ruoyi.module.infra.framework.file.core.client.local

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_PATH_INVALID
import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LocalFileClient(id: Long, config: LocalFileClientConfig) : AbstractFileClient<LocalFileClientConfig>(id, config) {
    override fun doInit() = Unit

    override fun upload(content: ByteArray, path: String, type: String): String {
        val filePath = resolvePath(path)
        filePath.parent?.let(Files::createDirectories)
        Files.write(filePath, content)
        return formatFileUrl(config.domain, path)
    }

    override fun delete(path: String) {
        Files.deleteIfExists(resolvePath(path))
    }

    override fun getContent(path: String): ByteArray? {
        val filePath = resolvePath(path)
        return if (Files.isRegularFile(filePath)) Files.readAllBytes(filePath) else null
    }

    private fun resolvePath(path: String): Path {
        FilePathUtils.validatePath(path)
        val basePath = Paths.get(config.basePath).toAbsolutePath().normalize()
        val filePath = basePath.resolve(path).normalize()
        if (!filePath.startsWith(basePath)) throw exception(FILE_PATH_INVALID)
        return filePath
    }
}
