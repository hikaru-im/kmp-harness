package im.hikaru.ruoyi.module.pay.convert.wallet

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.*
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.recharge.AppPayWalletPackageRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargePackageDO
import kotlinx.datetime.toJavaLocalDateTime

object PayWalletRechargePackageConvert {
    fun create(req: WalletRechargePackageCreateReqVO) = PayWalletRechargePackageDO().apply { name = req.name; payPrice = req.payPrice; bonusPrice = req.bonusPrice; status = req.status?.toInt() }
    fun update(req: WalletRechargePackageUpdateReqVO) = PayWalletRechargePackageDO().apply { id = req.id; name = req.name; payPrice = req.payPrice; bonusPrice = req.bonusPrice; status = req.status?.toInt() }
    fun admin(bean: PayWalletRechargePackageDO) = WalletRechargePackageRespVO().apply { id = bean.id; name = bean.name; payPrice = bean.payPrice; bonusPrice = bean.bonusPrice; status = bean.status?.toByte(); createTime = bean.createTime?.toJavaLocalDateTime() }
    fun page(page: PageResult<PayWalletRechargePackageDO>) = PageResult(page.total, page.list.map(::admin))
    fun app(list: List<PayWalletRechargePackageDO>) = list.map { AppPayWalletPackageRespVO().apply { id = it.id; name = it.name; payPrice = it.payPrice; bonusPrice = it.bonusPrice } }
}
