package im.hikaru.ruoyi.module.infra.service.file

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FileCreateReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileDO

interface FileService {
    fun createFile(content: ByteArray, name: String?, directory: String?, type: String?): String
    fun createFileRecord(content: ByteArray, name: String?, directory: String?, type: String?): FileDO
    fun createFile(createReqVO: FileCreateReqVO): Long
    fun deleteFile(id: Long)
    fun deleteFileList(ids: List<Long>)
    fun getFile(id: Long): FileDO?
    fun getFilePage(pageVO: FilePageReqVO): PageResult<FileDO>
    fun getFileContent(configId: Long, path: String): ByteArray?
    fun getFileByConfigIdAndPath(configId: Long, path: String): FileDO?
    fun presignPutUrl(name: String, directory: String?): FilePresignedUrlRespVO
    fun presignGetUrl(url: String, expirationSeconds: Int?): String
}
