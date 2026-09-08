package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO
import jakarta.validation.Valid

interface AddressService {
    fun createAddress(userId: Long, @Valid createReqVO: AppAddressCreateReqVO): Long
    fun updateAddress(userId: Long, @Valid updateReqVO: AppAddressUpdateReqVO): Unit
    fun deleteAddress(userId: Long, id: Long): Unit
    fun getAddress(userId: Long, id: Long): MemberAddressDO?
    fun getAddressList(userId: Long): List<MemberAddressDO>
    fun getDefaultUserAddress(userId: Long): MemberAddressDO?
}
