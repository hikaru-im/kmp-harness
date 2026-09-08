package im.hikaru.ruoyi.module.member.enums

import im.hikaru.ruoyi.framework.common.exception.ErrorCode

object ErrorCodeConstants {
    val ADDRESS_VERSION_CONFLICT = ErrorCode(1_004_004_001, "Address was changed concurrently")
    val USER_NOT_EXISTS = ErrorCode(1_004_001_000, "用户不存在")
    val USER_MOBILE_NOT_EXISTS = ErrorCode(1_004_001_001, "手机号未注册用户")
    val USER_MOBILE_USED = ErrorCode(1_004_001_002, "修改手机失败，该手机号({})已经被使用")
    val USER_POINT_NOT_ENOUGH = ErrorCode(1_004_001_003, "用户积分余额不足")
    val USER_EMAIL_USED = ErrorCode(1_004_001_004, "修改邮箱失败，该邮箱({})已经被使用")
    val AUTH_LOGIN_BAD_CREDENTIALS = ErrorCode(1_004_003_000, "登录失败，账号密码不正确")
    val AUTH_LOGIN_USER_DISABLED = ErrorCode(1_004_003_001, "登录失败，账号被禁用")
    val AUTH_SOCIAL_USER_NOT_FOUND = ErrorCode(1_004_003_005, "登录失败，解析不到三方登录信息")
    val AUTH_MOBILE_USED = ErrorCode(1_004_003_007, "手机号已经被使用")
    val ADDRESS_NOT_EXISTS = ErrorCode(1_004_004_000, "用户收件地址不存在")
    val TAG_NOT_EXISTS = ErrorCode(1_004_006_000, "用户标签不存在")
    val TAG_NAME_EXISTS = ErrorCode(1_004_006_001, "用户标签已经存在")
    val TAG_HAS_USER = ErrorCode(1_004_006_002, "用户标签下存在用户，无法删除")
    val POINT_RECORD_BIZ_NOT_SUPPORT = ErrorCode(1_004_008_000, "用户积分记录业务类型不支持")
    val SIGN_IN_CONFIG_NOT_EXISTS = ErrorCode(1_004_009_000, "签到天数规则不存在")
    val SIGN_IN_CONFIG_EXISTS = ErrorCode(1_004_009_001, "签到天数规则已存在")
    val SIGN_IN_RECORD_TODAY_EXISTS = ErrorCode(1_004_010_000, "今日已签到，请勿重复签到")
    val LEVEL_NOT_EXISTS = ErrorCode(1_004_011_000, "用户等级不存在")
    val LEVEL_NAME_EXISTS = ErrorCode(1_004_011_001, "用户等级名称[{}]已被使用")
    val LEVEL_VALUE_EXISTS = ErrorCode(1_004_011_002, "用户等级值[{}]已被[{}]使用")
    val LEVEL_EXPERIENCE_MIN = ErrorCode(1_004_011_003, "升级经验必须大于上一个等级[{}]设置的升级经验[{}]")
    val LEVEL_EXPERIENCE_MAX = ErrorCode(1_004_011_004, "升级经验必须小于下一个等级[{}]设置的升级经验[{}]")
    val LEVEL_HAS_USER = ErrorCode(1_004_011_005, "用户等级下存在用户，无法删除")
    val EXPERIENCE_BIZ_NOT_SUPPORT = ErrorCode(1_004_011_201, "用户经验业务类型不支持")
    val GROUP_NOT_EXISTS = ErrorCode(1_004_012_000, "用户分组不存在")
    val GROUP_HAS_USER = ErrorCode(1_004_012_001, "用户分组下存在用户，无法删除")
}
