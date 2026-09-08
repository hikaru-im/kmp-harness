package im.hikaru.ruoyi.module.member.convert.address

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.api.address.dto.MemberAddressRespDTO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressRespVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO

object AddressConvert {
    fun convert(source: AppAddressCreateReqVO): MemberAddressDO = requireNotNull(BeanUtils.toBean(source, MemberAddressDO::class.java))
    fun convert(source: AppAddressUpdateReqVO): MemberAddressDO = requireNotNull(BeanUtils.toBean(source, MemberAddressDO::class.java))
    fun convert(source: MemberAddressDO): AppAddressRespVO = requireNotNull(BeanUtils.toBean(source, AppAddressRespVO::class.java))
    fun convertList(source: List<MemberAddressDO>): List<AppAddressRespVO> = BeanUtils.toBean(source, AppAddressRespVO::class.java) ?: emptyList()
    fun convert02(source: MemberAddressDO): MemberAddressRespDTO = requireNotNull(BeanUtils.toBean(source, MemberAddressRespDTO::class.java))
}
