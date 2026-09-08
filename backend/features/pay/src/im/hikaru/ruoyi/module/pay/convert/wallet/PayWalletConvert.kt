package im.hikaru.ruoyi.module.pay.convert.wallet

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.api.wallet.dto.PayWalletRespDTO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.wallet.PayWalletRespVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.wallet.AppPayWalletRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletDO
import kotlinx.datetime.toJavaLocalDateTime

object PayWalletConvert {
    fun app(bean: PayWalletDO) = AppPayWalletRespVO().apply { balance = bean.balance; totalExpense = bean.totalExpense; totalRecharge = bean.totalRecharge }
    fun admin(bean: PayWalletDO) = PayWalletRespVO().apply { id = bean.id; userId = bean.userId; userType = bean.userType; balance = bean.balance; freezePrice = bean.freezePrice; totalExpense = bean.totalExpense; totalRecharge = bean.totalRecharge; createTime = bean.createTime?.toJavaLocalDateTime() }
    fun page(page: PageResult<PayWalletDO>) = PageResult(page.total, page.list.map(::admin))
    fun api(bean: PayWalletDO) = PayWalletRespDTO().apply { id = bean.id; userId = bean.userId; userType = bean.userType; balance = bean.balance; freezePrice = bean.freezePrice; totalExpense = bean.totalExpense; totalRecharge = bean.totalRecharge }
}
