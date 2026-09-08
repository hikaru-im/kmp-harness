package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO
import im.hikaru.ruoyi.module.member.dal.mysql.address.MemberAddressDao
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.ADDRESS_NOT_EXISTS
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.ADDRESS_VERSION_CONFLICT
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class AddressServiceImpl(
    private val commandService: AddressCommandService,
) : AddressService {

    @Transactional(rollbackFor = [Exception::class])
    override fun createAddress(userId: Long, createReqVO: AppAddressCreateReqVO): Long {
        return requireNotNull(commandService.create(userId, createReqVO).id)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateAddress(userId: Long, updateReqVO: AppAddressUpdateReqVO) {
        when (commandService.update(userId, updateReqVO)) {
            is AddressMutationResult.Applied -> Unit
            AddressMutationResult.NotFound -> throw exception(ADDRESS_NOT_EXISTS)
            is AddressMutationResult.Conflict -> throw exception(ADDRESS_VERSION_CONFLICT)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteAddress(userId: Long, id: Long) = when (commandService.delete(userId, id)) {
        is AddressMutationResult.Applied -> Unit
        AddressMutationResult.NotFound -> throw exception(ADDRESS_NOT_EXISTS)
        is AddressMutationResult.Conflict -> throw exception(ADDRESS_VERSION_CONFLICT)
    }

    override fun getAddress(userId: Long, id: Long): MemberAddressDO? = MemberAddressDao.selectByIdAndUserId(id, userId)

    override fun getAddressList(userId: Long): List<MemberAddressDO> = MemberAddressDao.selectListByUserIdAndDefaultStatus(userId, null)

    override fun getDefaultUserAddress(userId: Long): MemberAddressDO? =
        MemberAddressDao.selectListByUserIdAndDefaultStatus(userId, true).firstOrNull()

}
