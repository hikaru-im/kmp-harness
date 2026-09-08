package im.hikaru.ruoyi.module.infra.service.db

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.db.DataSourceConfigDO
import im.hikaru.ruoyi.module.infra.dal.mysql.db.DataSourceConfigDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_EXISTS
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class DataSourceConfigServiceImpl : DataSourceConfigService {

    override fun createDataSourceConfig(createReqVO: DataSourceConfigSaveReqVO): Long =
        DataSourceConfigDao.insert(requireNotNull(BeanUtils.toBean(createReqVO, DataSourceConfigDO::class.java)))

    override fun updateDataSourceConfig(updateReqVO: DataSourceConfigSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        validateDataSourceConfigExists(id)
        DataSourceConfigDao.updateById(requireNotNull(BeanUtils.toBean(updateReqVO, DataSourceConfigDO::class.java)))
    }

    override fun deleteDataSourceConfig(id: Long) {
        validateDataSourceConfigExists(id)
        DataSourceConfigDao.deleteById(id)
    }

    override fun deleteDataSourceConfigList(ids: List<Long>) {
        DataSourceConfigDao.deleteByIds(ids)
    }

    override fun getDataSourceConfig(id: Long): DataSourceConfigDO? =
        if (id == DataSourceConfigDO.ID_MASTER) buildMasterDataSourceConfig() else DataSourceConfigDao.selectById(id)

    override fun getDataSourceConfigList(): List<DataSourceConfigDO> =
        buildList {
            add(buildMasterDataSourceConfig())
            addAll(DataSourceConfigDao.selectList())
        }

    private fun validateDataSourceConfigExists(id: Long) {
        if (DataSourceConfigDao.selectById(id) == null) {
            throw exception(DATA_SOURCE_CONFIG_NOT_EXISTS)
        }
    }

    private fun buildMasterDataSourceConfig(): DataSourceConfigDO = DataSourceConfigDO().apply {
        id = DataSourceConfigDO.ID_MASTER
        name = "master"
        url = ""
        username = ""
        password = ""
    }
}
