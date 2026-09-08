package im.hikaru.ruoyi.framework.common.biz.system.permission.dto

import java.io.Serializable

/**
 * 登陆用户的部门数据权限 Response DTO (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class DeptDataPermissionRespDTO : Serializable {
    /** 部门的编号数组 */
    var deptIds: Set<Long>? = null

    /** 是否可以查看全部数据 */
    var all: Boolean? = null

    /** 是否可查看自己的数据 */
    var self: Boolean? = null

    companion object {
        private const val serialVersionUID = 1L
    }
}
