package im.hikaru.ruoyi.module.system.enums

object LogRecordConstants {
    const val SYSTEM_USER_TYPE = "SYSTEM user"
    const val SYSTEM_USER_CREATE_SUB_TYPE = "Create user"
    const val SYSTEM_USER_CREATE_SUCCESS = "Created user [{{#user.nickname}}]"
    const val SYSTEM_USER_UPDATE_SUB_TYPE = "Update user"
    const val SYSTEM_USER_UPDATE_SUCCESS = "Updated user [{{#user.nickname}}]: {_DIFF{#updateReqVO}}"
    const val SYSTEM_USER_DELETE_SUB_TYPE = "Delete user"
    const val SYSTEM_USER_DELETE_SUCCESS = "Deleted user [{{#user.nickname}}]"
    const val SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE = "Reset user password"
    const val SYSTEM_USER_UPDATE_PASSWORD_SUCCESS = "Reset password for [{{#user.nickname}}]"

    const val SYSTEM_ROLE_TYPE = "SYSTEM role"
    const val SYSTEM_ROLE_CREATE_SUB_TYPE = "Create role"
    const val SYSTEM_ROLE_CREATE_SUCCESS = "Created role [{{#role.name}}]"
    const val SYSTEM_ROLE_UPDATE_SUB_TYPE = "Update role"
    const val SYSTEM_ROLE_UPDATE_SUCCESS = "Updated role [{{#role.name}}]: {_DIFF{#updateReqVO}}"
    const val SYSTEM_ROLE_DELETE_SUB_TYPE = "Delete role"
    const val SYSTEM_ROLE_DELETE_SUCCESS = "Deleted role [{{#role.name}}]"
}
