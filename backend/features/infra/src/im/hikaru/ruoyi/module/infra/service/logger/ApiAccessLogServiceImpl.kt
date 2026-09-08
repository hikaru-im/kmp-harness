package im.hikaru.ruoyi.module.infra.service.logger

import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiAccessLogDO
import im.hikaru.ruoyi.module.infra.dal.mysql.logger.ApiAccessLogDao
import kotlinx.datetime.toKotlinLocalDateTime
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.LocalDateTime

@Service
@Validated
class ApiAccessLogServiceImpl : ApiAccessLogService {

    override fun createApiAccessLog(createDTO: ApiAccessLogCreateReqDTO) {
        val accessLog = requireNotNull(BeanUtils.toBean(createDTO, ApiAccessLogDO::class.java)).apply {
            requestParams = requestParams?.take(REQUEST_PARAMS_MAX_LENGTH)
            resultMsg = resultMsg?.take(RESULT_MSG_MAX_LENGTH)
        }
        ApiAccessLogDao.insert(accessLog)
    }

    override fun getApiAccessLog(id: Long): ApiAccessLogDO? = ApiAccessLogDao.selectById(id)

    override fun getApiAccessLogPage(pageReqVO: ApiAccessLogPageReqVO): PageResult<ApiAccessLogDO> =
        ApiAccessLogDao.selectPage(pageReqVO)

    override fun cleanAccessLog(exceedDay: Int, deleteLimit: Int): Int {
        val expireDate = LocalDateTime.now().minusDays(exceedDay.toLong()).toKotlinLocalDateTime()
        var count = 0
        repeat(Short.MAX_VALUE.toInt()) {
            val deleted = ApiAccessLogDao.deleteByCreateTimeLt(expireDate, deleteLimit)
            count += deleted
            if (deleted < deleteLimit) return count
        }
        return count
    }

    private companion object {
        const val REQUEST_PARAMS_MAX_LENGTH = 8000
        const val RESULT_MSG_MAX_LENGTH = 512
    }
}
