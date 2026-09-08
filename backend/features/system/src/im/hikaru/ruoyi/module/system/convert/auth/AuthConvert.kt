package im.hikaru.ruoyi.module.system.convert.auth

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.enums.permission.MenuTypeEnum

object AuthConvert {
    fun convert(
        user: AdminUserDO,
        roleList: List<RoleDO>,
        menuList: List<MenuDO>,
    ): AuthPermissionInfoRespVO = AuthPermissionInfoRespVO().apply {
        this.user = BeanUtils.toBean(user, AuthPermissionInfoRespVO.UserVO::class.java)
        roles = roleList.mapNotNull { it.code }.toSet()
        permissions = menuList.mapNotNull { it.permission }.toSet()
        menus = buildMenuTree(menuList)
    }

    fun buildMenuTree(menuList: List<MenuDO>): List<AuthPermissionInfoRespVO.MenuVO> {
        val nodes = menuList
            .filter { it.type != MenuTypeEnum.BUTTON.type }
            .sortedBy { it.sort ?: Int.MAX_VALUE }
            .mapNotNull { menu ->
                menu.id?.let {
                    it to requireNotNull(BeanUtils.toBean(menu, AuthPermissionInfoRespVO.MenuVO::class.java))
                }
            }
            .toMap(LinkedHashMap())

        nodes.values
            .filter { it.parentId != MenuDO.ID_ROOT }
            .forEach { child ->
                nodes[child.parentId]?.let { parent ->
                    val children = parent.children ?: mutableListOf<AuthPermissionInfoRespVO.MenuVO>().also {
                        parent.children = it
                    }
                    children += child
                }
            }
        return nodes.values.filter { it.parentId == MenuDO.ID_ROOT }
    }

    fun convert(
        userId: Long,
        userType: Int,
        reqVO: AuthSocialLoginReqVO,
    ): SocialUserBindReqDTO = SocialUserBindReqDTO().apply {
        this.userId = userId
        this.userType = userType
        socialType = reqVO.type
        code = reqVO.code
        state = reqVO.state
    }

    fun convert(reqVO: AuthSmsSendReqVO): SmsCodeSendReqDTO = SmsCodeSendReqDTO().apply {
        mobile = reqVO.mobile
        scene = reqVO.scene
        createIp = reqVO.captchaVerification
    }

    fun convert(
        reqVO: AuthSmsLoginReqVO,
        scene: Int,
        usedIp: String,
    ): SmsCodeUseReqDTO = SmsCodeUseReqDTO().apply {
        mobile = reqVO.mobile
        code = reqVO.code
        this.scene = scene
        this.usedIp = usedIp
    }
}
