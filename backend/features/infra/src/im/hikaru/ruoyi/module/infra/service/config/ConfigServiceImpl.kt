package im.hikaru.ruoyi.module.infra.service.config

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO
import im.hikaru.ruoyi.module.infra.dal.mysql.config.ConfigDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CONFIG_KEY_DUPLICATE
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CONFIG_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.config.ConfigTypeEnum
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class ConfigServiceImpl : ConfigService {

    override fun createConfig(createReqVO: ConfigSaveReqVO): Long {
        validateConfigKeyUnique(null, requireNotNull(createReqVO.key))
        val config = requireNotNull(BeanUtils.toBean(createReqVO, ConfigDO::class.java)).apply {
            type = ConfigTypeEnum.CUSTOM.type
        }
        return ConfigDao.insert(config)
    }

    override fun updateConfig(updateReqVO: ConfigSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateConfigExists(id)
        validateConfigKeyUnique(id, requireNotNull(updateReqVO.key))
        ConfigDao.updateById(requireNotNull(BeanUtils.toBean(updateReqVO, ConfigDO::class.java)))
    }

    override fun deleteConfig(id: Long) {
        val config = validateConfigExists(id)
        if (config.type == ConfigTypeEnum.SYSTEM.type) {
            throw exception(CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE)
        }
        ConfigDao.deleteById(id)
    }

    override fun deleteConfigList(ids: List<Long>) {
        ConfigDao.selectByIds(ids).forEach {
            if (it.type == ConfigTypeEnum.SYSTEM.type) {
                throw exception(CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE)
            }
        }
        ConfigDao.deleteByIds(ids)
    }

    override fun getConfig(id: Long): ConfigDO? = ConfigDao.selectById(id)

    override fun getConfigByKey(key: String): ConfigDO? = ConfigDao.selectByKey(key)

    override fun getConfigPage(pageReqVO: ConfigPageReqVO): PageResult<ConfigDO> = ConfigDao.selectPage(pageReqVO)

    private fun validateConfigExists(id: Long): ConfigDO =
        ConfigDao.selectById(id) ?: throw exception(CONFIG_NOT_EXISTS)

    private fun validateConfigKeyUnique(id: Long?, key: String) {
        val config = ConfigDao.selectByKey(key) ?: return
        if (id == null || config.id != id) {
            throw exception(CONFIG_KEY_DUPLICATE)
        }
    }
}
