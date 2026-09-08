package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageCreateReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackagePageReqVO
import im.hikaru.ruoyi.module.pay.controller.admin.wallet.vo.rechargepackage.WalletRechargePackageUpdateReqVO
import im.hikaru.ruoyi.module.pay.convert.wallet.PayWalletRechargePackageConvert
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletRechargePackageDO
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletRechargePackageDao
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_PACKAGE_IS_DISABLE
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_PACKAGE_NAME_EXISTS
import im.hikaru.ruoyi.module.pay.enums.ErrorCodeConstants.WALLET_RECHARGE_PACKAGE_NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PayWalletRechargePackageServiceImpl : PayWalletRechargePackageService {

    override fun getWalletRechargePackage(packageId: Long): PayWalletRechargePackageDO =
        PayWalletRechargePackageDao.selectById(packageId) ?: throw exception(WALLET_RECHARGE_PACKAGE_NOT_FOUND)

    override fun validWalletRechargePackage(packageId: Long): PayWalletRechargePackageDO {
        val rechargePackage = getWalletRechargePackage(packageId)
        if (CommonStatusEnum.isDisable(rechargePackage.status)) {
            throw exception(WALLET_RECHARGE_PACKAGE_IS_DISABLE)
        }
        return rechargePackage
    }

    override fun createWalletRechargePackage(createReqVO: WalletRechargePackageCreateReqVO): Long {
        validateNameUnique(null, createReqVO.name)
        return PayWalletRechargePackageDao.insert(PayWalletRechargePackageConvert.create(createReqVO))
    }

    override fun updateWalletRechargePackage(updateReqVO: WalletRechargePackageUpdateReqVO) {
        val id = requireNotNull(updateReqVO.id)
        getWalletRechargePackage(id)
        validateNameUnique(id, updateReqVO.name)
        PayWalletRechargePackageDao.updateById(PayWalletRechargePackageConvert.update(updateReqVO))
    }

    override fun deleteWalletRechargePackage(id: Long) {
        getWalletRechargePackage(id)
        PayWalletRechargePackageDao.deleteById(id)
    }

    override fun getWalletRechargePackagePage(
        pageReqVO: WalletRechargePackagePageReqVO,
    ): PageResult<PayWalletRechargePackageDO> = PayWalletRechargePackageDao.selectPage(pageReqVO)

    override fun getWalletRechargePackageList(status: Int): List<PayWalletRechargePackageDO> =
        PayWalletRechargePackageDao.selectListByStatus(status)

    private fun validateNameUnique(id: Long?, name: String?) {
        if (name.isNullOrBlank()) return
        val existing = PayWalletRechargePackageDao.selectByName(name) ?: return
        if (id == null || existing.id != id) throw exception(WALLET_RECHARGE_PACKAGE_NAME_EXISTS)
    }
}
