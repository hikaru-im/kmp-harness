package im.hikaru.ruoyi.module.pay.service.app

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.app.vo.*
import im.hikaru.ruoyi.module.pay.convert.app.PayAppConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.app.PayAppDO
import im.hikaru.ruoyi.module.pay.dal.mysql.app.PayAppDao
import im.hikaru.ruoyi.module.pay.dal.mysql.channel.PayChannelDao
import im.hikaru.ruoyi.module.pay.dal.mysql.order.PayOrderDao
import im.hikaru.ruoyi.module.pay.dal.mysql.refund.PayRefundDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.APP_EXIST_ORDER_CANT_DELETE
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.APP_EXIST_REFUND_CANT_DELETE
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.APP_IS_DISABLE
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.APP_KEY_EXISTS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.APP_NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayAppServiceImpl : PayAppService {
    override fun createApp(createReqVO: PayAppCreateReqVO): Long {
        validateAppKeyUnique(null, requireNotNull(createReqVO.appKey))
        return PayAppDao.insert(PayAppConvert.convert(createReqVO))
    }

    override fun updateApp(updateReqVO: PayAppUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id); validateAppExists(id); validateAppKeyUnique(id, requireNotNull(updateReqVO.appKey)); PayAppDao.updateById(PayAppConvert.convert(updateReqVO))
    }

    override fun updateAppStatus(id: Long, status: Int) { validateAppExists(id); PayAppDao.updateById(PayAppDO().apply { this.id = id; this.status = status }) }

    override fun deleteApp(id: Long) {
        validateAppExists(id)
        if (PayOrderDao.selectCountByAppId(id) > 0) throw exception(APP_EXIST_ORDER_CANT_DELETE)
        if (PayRefundDao.selectCountByAppId(id) > 0) throw exception(APP_EXIST_REFUND_CANT_DELETE)
        PayAppDao.deleteById(id)
    }

    override fun getApp(id: Long): PayAppDO = PayAppDao.selectById(id) ?: throw exception(APP_NOT_FOUND)
    override fun getAppList(ids: Collection<Long>): List<PayAppDO> = PayAppDao.selectByIds(ids)
    override fun getAppList(): List<PayAppDO> = PayAppDao.selectList()
    override fun getAppPage(pageReqVO: PayAppPageReqVO): PageResult<PayAppDO> = PayAppDao.selectPage(pageReqVO)
    override fun getAppMap(ids: Collection<Long>): Map<Long, PayAppDO> = getAppList(ids).mapNotNull { it.id?.let { id -> id to it } }.toMap()

    override fun validPayApp(id: Long): PayAppDO = validatePayApp(PayAppDao.selectById(id))
    override fun validPayApp(appKey: String): PayAppDO = validatePayApp(PayAppDao.selectByAppKey(appKey))

    private fun validateAppExists(id: Long) { if (PayAppDao.selectById(id) == null) throw exception(APP_NOT_FOUND) }
    private fun validateAppKeyUnique(id: Long?, appKey: String) { PayAppDao.selectByAppKey(appKey)?.let { if (id == null || it.id != id) throw exception(APP_KEY_EXISTS) } }
    private fun validatePayApp(app: PayAppDO?): PayAppDO { if (app == null) throw exception(APP_NOT_FOUND); if (CommonStatusEnum.isDisable(app.status)) throw exception(APP_IS_DISABLE); return app }
}
