package im.hikaru.ruoyi.module.infra.service.config

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO

/**
 * 参数配置 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface ConfigService {

    fun createConfig(createReqVO: ConfigSaveReqVO): Long

    fun updateConfig(updateReqVO: ConfigSaveReqVO)

    fun deleteConfig(id: Long)

    fun deleteConfigList(ids: List<Long>)

    fun getConfig(id: Long): ConfigDO?

    fun getConfigByKey(key: String): ConfigDO?

    fun getConfigPage(pageReqVO: ConfigPageReqVO): PageResult<ConfigDO>
}
