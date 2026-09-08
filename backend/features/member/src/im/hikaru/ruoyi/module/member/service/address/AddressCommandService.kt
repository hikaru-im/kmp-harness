package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO

interface AddressCommandService {
    fun create(userId: Long, request: AppAddressCreateReqVO): MemberAddressDO

    fun update(
        userId: Long,
        request: AppAddressUpdateReqVO,
        expectedVersion: Long? = null,
    ): AddressMutationResult

    fun delete(
        userId: Long,
        id: Long,
        expectedVersion: Long? = null,
    ): AddressMutationResult
}

sealed interface AddressMutationResult {
    data class Applied(val address: MemberAddressDO) : AddressMutationResult
    data object NotFound : AddressMutationResult
    data class Conflict(
        val serverVersion: Long,
        val serverAddress: MemberAddressDO? = null,
    ) : AddressMutationResult
}
