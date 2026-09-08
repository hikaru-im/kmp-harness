package im.hikaru.ruoyi.module.pay.convert.wallet

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionRespVO
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionRespVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import kotlinx.datetime.toJavaLocalDateTime

object PayWalletTransactionConvert {
    fun admin(bean: PayWalletTransactionDO) = PayWalletTransactionRespVO().apply { id = bean.id; walletId = bean.walletId; bizType = bean.bizType; price = bean.price?.toLong(); title = bean.title; balance = bean.balance?.toLong(); createTime = bean.createTime?.toJavaLocalDateTime() }
    fun adminPage(page: PageResult<PayWalletTransactionDO>) = PageResult(page.total, page.list.map(::admin))
    fun app(bean: PayWalletTransactionDO) = AppPayWalletTransactionRespVO().apply { bizType = bean.bizType; price = bean.price?.toLong(); title = bean.title; createTime = bean.createTime?.toJavaLocalDateTime() }
    fun appPage(page: PageResult<PayWalletTransactionDO>) = PageResult(page.total, page.list.map(::app))
}
