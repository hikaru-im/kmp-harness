package im.hikaru.ruoyi.module.system.convert.user

import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileRespVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserRespVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserSimpleRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO

object UserConvert {
    fun convertList(list: List<AdminUserDO>, deptMap: Map<Long, DeptDO>): List<UserRespVO> =
        list.map { convert(it, it.deptId?.let(deptMap::get)) }

    fun convert(user: AdminUserDO, dept: DeptDO?): UserRespVO =
        requireNotNull(BeanUtils.toBean(user, UserRespVO::class.java)).apply {
            deptName = dept?.name
        }

    fun convertSimpleList(list: List<AdminUserDO>, deptMap: Map<Long, DeptDO>): List<UserSimpleRespVO> =
        list.map { user ->
            requireNotNull(BeanUtils.toBean(user, UserSimpleRespVO::class.java)).apply {
                deptName = user.deptId?.let(deptMap::get)?.name
            }
        }

    @Suppress("UNUSED_PARAMETER")
    fun convert(
        user: AdminUserDO,
        userRoles: List<RoleDO>,
        dept: DeptDO?,
        posts: List<PostDO>,
    ): UserProfileRespVO = requireNotNull(BeanUtils.toBean(user, UserProfileRespVO::class.java))
}
