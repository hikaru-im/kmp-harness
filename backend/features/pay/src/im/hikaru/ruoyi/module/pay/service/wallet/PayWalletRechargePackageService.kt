package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackagePageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageUpdateReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargePackageDO
import jakarta.validation.Valid

interface PayWalletRechargePackageService {
    fun getWalletRechargePackage(packageId: Long): PayWalletRechargePackageDO
    fun validWalletRechargePackage(packageId: Long): PayWalletRechargePackageDO
    fun createWalletRechargePackage(@Valid createReqVO: WalletRechargePackageCreateReqVO): Long
    fun updateWalletRechargePackage(@Valid updateReqVO: WalletRechargePackageUpdateReqVO): Unit
    fun deleteWalletRechargePackage(id: Long): Unit
    fun getWalletRechargePackagePage(pageReqVO: WalletRechargePackagePageReqVO): PageResult<PayWalletRechargePackageDO>
    fun getWalletRechargePackageList(status: Int): List<PayWalletRechargePackageDO>
}
