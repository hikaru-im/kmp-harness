package im.hikaru.ruoyi.module.member.controller.app.address

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressRespVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import im.hikaru.ruoyi.module.member.convert.address.AddressConvert
import im.hikaru.ruoyi.module.member.enums.ErrorCodeConstants.ADDRESS_NOT_EXISTS
import im.hikaru.ruoyi.module.member.service.address.AddressService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "用户 APP - 用户收件地址")
@RestController
@RequestMapping("/member/address")
@Validated
class AppAddressController(
    private val addressService: AddressService,
) {
    @PostMapping("/create")
    @Operation(summary = "创建用户收件地址")
    fun createAddress(@Valid @RequestBody createReqVO: AppAddressCreateReqVO): CommonResult<Long> = CommonResult.success(addressService.createAddress(userId(), createReqVO))

    @PutMapping("/update")
    @Operation(summary = "更新用户收件地址")
    fun updateAddress(@Valid @RequestBody updateReqVO: AppAddressUpdateReqVO): CommonResult<Boolean> { addressService.updateAddress(userId(), updateReqVO); return CommonResult.success(true) }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户收件地址")
    @Parameter(name = "id", description = "编号", required = true)
    fun deleteAddress(@RequestParam("id") id: Long): CommonResult<Boolean> { addressService.deleteAddress(userId(), id); return CommonResult.success(true) }

    @GetMapping("/get")
    @Operation(summary = "获得用户收件地址")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    fun getAddress(@RequestParam("id") id: Long): CommonResult<AppAddressRespVO> = CommonResult.success(AddressConvert.convert(addressService.getAddress(userId(), id) ?: throw exception(ADDRESS_NOT_EXISTS)))

    @GetMapping("/get-default")
    @Operation(summary = "获得默认的用户收件地址")
    fun getDefaultUserAddress(): CommonResult<AppAddressRespVO> = CommonResult.success(AddressConvert.convert(addressService.getDefaultUserAddress(userId()) ?: throw exception(ADDRESS_NOT_EXISTS)))

    @GetMapping("/list")
    @Operation(summary = "获得用户收件地址列表")
    fun getAddressList(): CommonResult<List<AppAddressRespVO>> = CommonResult.success(AddressConvert.convertList(addressService.getAddressList(userId())))

    private fun userId(): Long = requireNotNull(WebFrameworkUtils.getLoginUserId())
}
