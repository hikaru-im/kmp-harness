package im.hikaru.ruoyi.module.member.api.address

import im.hikaru.ruoyi.module.member.api.address.dto.MemberAddressRespDTO

interface MemberAddressApi {
    fun getAddress(id: Long, userId: Long): MemberAddressRespDTO
    fun getDefaultAddress(userId: Long): MemberAddressRespDTO
}
