package im.hikaru.ruoyi.module.member.dal.mysql.user

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import im.hikaru.ruoyi.framework.mybatis.core.type.longList
import org.jetbrains.exposed.v1.datetime.datetime

object MemberUserTable : BaseTable("member_user") {
    val id = long("id").autoIncrement("member_user_seq")
    val mobile = varchar("mobile", 32).nullable()
    val email = varchar("email", 255).nullable()
    val password = varchar("password", 100).nullable()
    val status = integer("status").nullable()
    val registerIp = varchar("register_ip", 255).nullable()
    val registerTerminal = integer("register_terminal").nullable()
    val loginIp = varchar("login_ip", 255).nullable()
    val loginDate = datetime("login_date").nullable()
    val nickname = varchar("nickname", 255).nullable()
    val avatar = varchar("avatar", 255).nullable()
    val profileVersion = long("profile_version").default(1)
    val name = varchar("name", 255).nullable()
    val sex = integer("sex").nullable()
    val birthday = datetime("birthday").nullable()
    val areaId = integer("area_id").nullable()
    val mark = varchar("mark", 2048).nullable()
    val point = integer("point").default(0)
    val tagIds = longList("tag_ids", 1024).nullable()
    val levelId = long("level_id").nullable()
    val experience = integer("experience").default(0)
    val groupId = long("group_id").nullable()
    val tenantId = long("tenant_id")
    override val primaryKey = PrimaryKey(id)
}
