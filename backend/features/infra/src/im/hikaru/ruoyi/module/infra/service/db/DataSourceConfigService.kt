package im.hikaru.ruoyi.module.infra.service.db

import im.hikaru.ruoyi.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.db.DataSourceConfigDO

/**
 * 数据源配置 Service 接口 (迁移自 Java)
 * @author 芋道源码
 */
interface DataSourceConfigService {

    /**
     * 创建数据源配置
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    fun createDataSourceConfig(createReqVO: DataSourceConfigSaveReqVO): Long

    /**
     * 更新数据源配置
     *
     * @param updateReqVO 更新信息
     */
    fun updateDataSourceConfig(updateReqVO: DataSourceConfigSaveReqVO)

    /**
     * 删除数据源配置
     *
     * @param id 编号
     */
    fun deleteDataSourceConfig(id: Long)

    /**
     * 批量删除数据源配置
     *
     * @param ids 编号列表
     */
    fun deleteDataSourceConfigList(ids: List<Long>)

    /**
     * 获得数据源配置
     *
     * @param id 编号
     * @return 数据源配置
     */
    fun getDataSourceConfig(id: Long): DataSourceConfigDO?

    /**
     * 获得数据源配置列表
     *
     * @return 数据源配置列表
     */
    fun getDataSourceConfigList(): List<DataSourceConfigDO>
}
