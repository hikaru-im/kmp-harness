package im.hikaru.ruoyi.module.infra.convert.config

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO

/**
 * Config 转换器 (迁移自 Java MapStruct → BeanUtils)
 *
 * 迁移说明：原使用 MapStruct @Mapper 接口自动生成转换代码。
 * 迁移到 Kotlin 后改用 BeanUtils (基于 Jackson) 进行对象转换。
 *
 * @author 芋道源码
 */
object ConfigConvert {

    fun convert(saveReqVO: ConfigSaveReqVO): ConfigDO? =
        BeanUtils.toBean(saveReqVO, ConfigDO::class.java)

    fun convert(configDO: ConfigDO?): ConfigRespVO? =
        BeanUtils.toBean(configDO, ConfigRespVO::class.java)

    fun convertList(list: List<ConfigDO>): List<ConfigRespVO>? =
        BeanUtils.toBean(list, ConfigRespVO::class.java)

    fun convertPage(page: PageResult<ConfigDO>): PageResult<ConfigRespVO> =
        BeanUtils.toBean(page, ConfigRespVO::class.java)!!
}
