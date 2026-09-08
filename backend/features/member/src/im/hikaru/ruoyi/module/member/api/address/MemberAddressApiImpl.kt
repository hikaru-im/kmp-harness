package im.hikaru.ruoyi.module.member.api.address

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.member.api.address.dto.MemberAddressRespDTO
import im.hikaru.ruoyi.module.member.convert.address.AddressConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.ADDRESS_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.address.AddressService
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MemberAddressApiImpl(
    private val addressService: AddressService,
) : MemberAddressApi {
    override fun getAddress(id: Long, userId: Long): MemberAddressRespDTO =
        AddressConvert.convert02(addressService.getAddress(userId, id) ?: throw exception(ADDRESS_NOT_EXISTS))

    override fun getDefaultAddress(userId: Long): MemberAddressRespDTO =
        AddressConvert.convert02(addressService.getDefaultUserAddress(userId) ?: throw exception(ADDRESS_NOT_EXISTS))
}
