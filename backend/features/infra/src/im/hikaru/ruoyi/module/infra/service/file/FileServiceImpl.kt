package im.hikaru.ruoyi.module.infra.service.file

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FileCreateReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileDO
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_IS_EMPTY
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FileTypeUtils
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom

@Service
class FileServiceImpl(
    private val fileConfigService: FileConfigService,
) : FileService {

    override fun createFile(content: ByteArray, name: String?, directory: String?, type: String?): String =
        requireNotNull(createFileRecord(content, name, directory, type).url)

    override fun createFileRecord(content: ByteArray, name: String?, directory: String?, type: String?): FileDO {
        if (content.isEmpty()) throw exception(FILE_IS_EMPTY)
        var resolvedName = FilePathUtils.validateFileName(name)
        val mimeType = type?.takeIf(String::isNotEmpty) ?: FileTypeUtils.getMimeType(content, resolvedName)
        if (resolvedName.isNullOrEmpty()) resolvedName = sha256(content)
        if (!resolvedName.contains('.')) {
            val extension = FileTypeUtils.getExtension(mimeType)
            if (!extension.isNullOrEmpty()) resolvedName += extension
        }
        val fileName = resolvedName
        val path = generateUploadPath(fileName, directory)
        val client = requireNotNull(fileConfigService.getMasterFileClient()) { "Master file client does not exist" }
        val url = client.upload(content, path, mimeType)
        return FileDO().apply {
            configId = client.id
            this.name = fileName
            this.path = path
            this.url = url
            this.type = mimeType
            size = content.size.toLong()
        }.also(FileDao::insert)
    }

    internal fun generateUploadPath(name: String, directory: String?): String {
        var result = requireNotNull(FilePathUtils.validateFileName(name))
        FilePathUtils.validatePath(result)
        FilePathUtils.validateDirectory(directory)
        val prefix = if (PATH_PREFIX_DATE_ENABLE) LocalDate.now().format(DATE_FORMATTER) else null
        val suffix = if (PATH_SUFFIX_TIMESTAMP_ENABLE) {
            "${System.currentTimeMillis()}${ThreadLocalRandom.current().nextInt(10_000, 100_000)}"
        } else {
            null
        }
        if (suffix != null) {
            result = if (PATH_SUFFIX_AS_DIRECTORY) {
                "$suffix/$result"
            } else {
                val dot = result.lastIndexOf('.')
                if (dot > 0) "${result.substring(0, dot)}_$suffix${result.substring(dot)}" else "${result}_$suffix"
            }
        }
        if (prefix != null) result = "$prefix/$result"
        if (!directory.isNullOrEmpty()) result = "$directory/$result"
        return result
    }

    override fun createFile(createReqVO: FileCreateReqVO): Long {
        FilePathUtils.validatePath(createReqVO.path)
        createReqVO.name = FilePathUtils.validateFileName(createReqVO.name)
        createReqVO.url = createReqVO.url?.let(HttpUtils::removeUrlQuery)
        val file = requireNotNull(BeanUtils.toBean(createReqVO, FileDO::class.java)).apply { size = 0L }
        return FileDao.insert(file)
    }

    override fun deleteFile(id: Long) {
        val file = validateFileExists(id)
        val path = requireNotNull(file.path)
        FilePathUtils.validatePath(path)
        requireNotNull(fileConfigService.getFileClient(requireNotNull(file.configId))) {
            "File client ${file.configId} does not exist"
        }.delete(path)
        FileDao.deleteById(id)
    }

    override fun deleteFileList(ids: List<Long>) {
        FileDao.selectByIds(ids).forEach { file ->
            val path = requireNotNull(file.path)
            FilePathUtils.validatePath(path)
            requireNotNull(fileConfigService.getFileClient(requireNotNull(file.configId))) {
                "File client ${file.configId} does not exist"
            }.delete(path)
        }
        FileDao.deleteByIds(ids)
    }

    override fun getFile(id: Long): FileDO? = FileDao.selectById(id)

    override fun getFilePage(pageVO: FilePageReqVO): PageResult<FileDO> = FileDao.selectPage(pageVO)

    override fun getFileContent(configId: Long, path: String): ByteArray? {
        FilePathUtils.validatePath(path)
        return requireNotNull(fileConfigService.getFileClient(configId)) {
            "File client $configId does not exist"
        }.getContent(path)
    }

    override fun getFileByConfigIdAndPath(configId: Long, path: String): FileDO? =
        FileDao.selectLatestByConfigIdAndPath(configId, path)

    override fun presignPutUrl(name: String, directory: String?): FilePresignedUrlRespVO {
        val path = generateUploadPath(name, directory)
        val client = requireNotNull(fileConfigService.getMasterFileClient()) { "Master file client does not exist" }
        return FilePresignedUrlRespVO().apply {
            configId = client.id
            uploadUrl = client.presignPutUrl(path)
            url = client.presignGetUrl(path, null)
            this.path = path
        }
    }

    override fun presignGetUrl(url: String, expirationSeconds: Int?): String {
        val client = requireNotNull(fileConfigService.getMasterFileClient()) { "Master file client does not exist" }
        return client.presignGetUrl(url, expirationSeconds)
    }

    private fun validateFileExists(id: Long): FileDO =
        FileDao.selectById(id) ?: throw exception(FILE_NOT_EXISTS)

    private fun sha256(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

    companion object {
        internal var PATH_PREFIX_DATE_ENABLE = true
        internal var PATH_SUFFIX_TIMESTAMP_ENABLE = false
        internal var PATH_SUFFIX_AS_DIRECTORY = true
        private val DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE
    }
}
