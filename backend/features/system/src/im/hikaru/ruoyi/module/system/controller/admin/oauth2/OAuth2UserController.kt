package im.hikaru.ruoyi.module.system.controller.admin.oauth2

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.user.OAuth2UserInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.user.OAuth2UserUpdateReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - OAuth2 user")
@RestController
@RequestMapping("/system/oauth2/user")
@Validated
class OAuth2UserController(
    private val userService: AdminUserService,
    private val deptService: DeptService,
    private val postService: PostService,
) {
    @GetMapping("/get")
    @Operation(summary = "Get user information")
    @PreAuthorize("@ss.hasScope('user.read')")
    fun getUserInfo(): CommonResult<OAuth2UserInfoRespVO> {
        val user = requireNotNull(userService.getUser(requireLoginId())) { "Authenticated user does not exist" }
        val response = OAuth2UserInfoRespVO().apply {
            id = user.id
            username = user.username
            nickname = user.nickname
            email = user.email
            mobile = user.mobile
            sex = user.sex
            avatar = user.avatar
        }
        user.deptId?.let { deptId ->
            deptService.getDept(deptId)?.let { dept ->
                response.dept = OAuth2UserInfoRespVO.Dept().apply {
                    id = dept.id
                    name = dept.name
                }
            }
        }
        response.posts = postService.getPostList(user.postIds).map { post ->
            OAuth2UserInfoRespVO.Post().apply {
                id = post.id
                name = post.name
            }
        }
        return CommonResult.success(response)
    }

    @PutMapping("/update")
    @Operation(summary = "Update user information")
    @PreAuthorize("@ss.hasScope('user.write')")
    fun updateUserInfo(@Valid @RequestBody req: OAuth2UserUpdateReqVO): CommonResult<Boolean> {
        userService.updateUserProfile(requireLoginId(), UserProfileUpdateReqVO().apply {
            nickname = req.nickname
            email = req.email
            mobile = req.mobile
            sex = req.sex
        })
        return CommonResult.success(true)
    }

    private fun requireLoginId(): Long =
        requireNotNull(SecurityFrameworkUtils.getLoginUserId()) { "No authenticated user" }
}
