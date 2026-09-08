package im.hikaru.ruoyi.module.member.controller.admin.address

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.member.controller.admin.address.vo.AddressRespVO
import im.hikaru.ruoyi.module.member.service.address.AddressService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "管理后台 - 用户收件地址")
@RestController
@RequestMapping("/member/address")
@Validated
class AddressController(
    private val addressService: AddressService,
) {
    @GetMapping("/list")
    @Operation(summary = "获得用户收件地址列表")
    @Parameter(name = "userId", description = "用户编号", required = true)
    @PreAuthorize("@ss.hasPermission('member:user:query')")
    fun getAddressList(@RequestParam("userId") userId: Long): CommonResult<List<AddressRespVO>> = CommonResult.success(BeanUtils.toBean(addressService.getAddressList(userId), AddressRespVO::class.java) ?: emptyList())
}
