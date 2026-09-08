package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

class AuthMenuRespVO {
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
    var children: List<AuthMenuRespVO>? = null
}
