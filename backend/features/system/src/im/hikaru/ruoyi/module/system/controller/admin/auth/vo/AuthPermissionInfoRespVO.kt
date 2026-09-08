package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

class AuthPermissionInfoRespVO {
    var user: UserVO? = null
    var roles: Set<String> = emptySet()
    var permissions: Set<String> = emptySet()
    var menus: List<MenuVO> = emptyList()

    class UserVO {
        var id: Long? = null
        var nickname: String? = null
        var avatar: String? = null
        var deptId: Long? = null
        var username: String? = null
        var email: String? = null
    }

    class MenuVO {
        var id: Long? = null
        var parentId: Long? = null
        var name: String? = null
        var path: String? = null
        var component: String? = null
        var componentName: String? = null
        var icon: String? = null
        var visible: Boolean? = null
        var keepAlive: Boolean? = null
        var alwaysShow: Boolean? = null
        var children: MutableList<MenuVO>? = null
    }
}
