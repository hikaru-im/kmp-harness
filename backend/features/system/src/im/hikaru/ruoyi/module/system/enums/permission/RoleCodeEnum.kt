package im.hikaru.ruoyi.module.system.enums.permission

enum class RoleCodeEnum(val code: String, val roleName: String) {
    SUPER_ADMIN("super_admin", "Super administrator"),
    TENANT_ADMIN("tenant_admin", "Tenant administrator"),
    CRM_ADMIN("crm_admin", "CRM administrator");

    companion object {
        fun isSuperAdmin(code: String?): Boolean = code == SUPER_ADMIN.code
    }
}
