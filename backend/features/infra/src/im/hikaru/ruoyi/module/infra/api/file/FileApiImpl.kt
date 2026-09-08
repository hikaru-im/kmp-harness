package im.hikaru.ruoyi.module.infra.api.file

import im.hikaru.ruoyi.module.infra.service.file.FileService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

/**
 * 文件 API 实现类 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Service
@Validated
class FileApiImpl(
    private val fileService: FileService,
) : FileApi {

    override fun createFile(content: ByteArray, name: String?, directory: String?, type: String?): String =
        fileService.createFile(content, name, directory, type)

    override fun presignGetUrl(url: String, expirationSeconds: Int?): String =
        fileService.presignGetUrl(url, expirationSeconds)
}
