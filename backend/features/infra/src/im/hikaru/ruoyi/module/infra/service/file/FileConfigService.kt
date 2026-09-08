package im.hikaru.ruoyi.module.infra.service.file

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileConfigDO
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClient

interface FileConfigService {
    fun createFileConfig(createReqVO: FileConfigSaveReqVO): Long
    fun updateFileConfig(updateReqVO: FileConfigSaveReqVO)
    fun updateFileConfigMaster(id: Long)
    fun deleteFileConfig(id: Long)
    fun deleteFileConfigList(ids: List<Long>)
    fun getFileConfig(id: Long): FileConfigDO?
    fun getFileConfigPage(pageVO: FileConfigPageReqVO): PageResult<FileConfigDO>
    fun testFileConfig(id: Long): String
    fun getFileClient(id: Long): FileClient?
    fun getMasterFileClient(): FileClient?
}
