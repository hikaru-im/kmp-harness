package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.MenuListReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object MenuDao {
    fun selectById(id: Long): MenuDO? = transaction { MenuTable.selectAll().where { conditions(MenuTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MenuDO> = if (ids.isEmpty()) emptyList() else transaction { MenuTable.selectAll().where { conditions(MenuTable.id inList ids) }.map(::toEntity) }
    fun selectList(req: MenuListReqVO = MenuListReqVO()): List<MenuDO> = transaction { val ops = mutableListOf<Op<Boolean>>(); req.name?.takeIf { it.isNotBlank() }?.let { ops += MenuTable.name like "%$it%" }; req.status?.let { ops += MenuTable.status eq it }; MenuTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(MenuTable.sort, SortOrder.ASC).map(::toEntity) }
    fun selectByParentIdAndName(parentId: Long, name: String): MenuDO? = transaction { MenuTable.selectAll().where { conditions(MenuTable.parentId eq parentId, MenuTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectCountByParentId(parentId: Long): Long = transaction { MenuTable.selectAll().where { conditions(MenuTable.parentId eq parentId) }.count() }
    fun selectListByPermission(permission: String): List<MenuDO> = transaction { MenuTable.selectAll().where { conditions(MenuTable.permission eq permission) }.map(::toEntity) }
    fun selectByComponentName(name: String): MenuDO? = transaction { MenuTable.selectAll().where { conditions(MenuTable.componentName eq name) }.singleOrNull()?.let(::toEntity) }
    fun insert(entity: MenuDO): Long { DefaultDBFieldHandler.fillOnInsert(entity); val id = transaction { MenuTable.insert {
        it[MenuTable.name] = requireNotNull(entity.name); it[MenuTable.permission] = entity.permission.orEmpty(); it[MenuTable.type] = requireNotNull(entity.type); it[MenuTable.sort] = requireNotNull(entity.sort); it[MenuTable.parentId] = entity.parentId ?: MenuDO.ID_ROOT
        it[MenuTable.path] = entity.path; it[MenuTable.icon] = entity.icon; it[MenuTable.component] = entity.component; it[MenuTable.componentName] = entity.componentName; it[MenuTable.status] = requireNotNull(entity.status)
        it[MenuTable.visible] = entity.visible ?: true; it[MenuTable.keepAlive] = entity.keepAlive ?: true; it[MenuTable.alwaysShow] = entity.alwaysShow ?: true
        it[MenuTable.creator] = entity.creator.orEmpty(); it[MenuTable.updater] = entity.updater.orEmpty(); it[MenuTable.createTime] = requireNotNull(entity.createTime); it[MenuTable.updateTime] = requireNotNull(entity.updateTime)
    }.get(MenuTable.id) }; entity.id = id; return id }
    fun updateById(entity: MenuDO) { DefaultDBFieldHandler.fillOnUpdate(entity); val id = requireNotNull(entity.id); transaction { MenuTable.update(where = { conditions(MenuTable.id eq id) }) {
        entity.name?.let { v -> it[MenuTable.name] = v }; entity.permission?.let { v -> it[MenuTable.permission] = v }; entity.type?.let { v -> it[MenuTable.type] = v }; entity.sort?.let { v -> it[MenuTable.sort] = v }; entity.parentId?.let { v -> it[MenuTable.parentId] = v }
        entity.path?.let { v -> it[MenuTable.path] = v }; entity.icon?.let { v -> it[MenuTable.icon] = v }; entity.component?.let { v -> it[MenuTable.component] = v }; entity.componentName?.let { v -> it[MenuTable.componentName] = v }; entity.status?.let { v -> it[MenuTable.status] = v }
        entity.visible?.let { v -> it[MenuTable.visible] = v }; entity.keepAlive?.let { v -> it[MenuTable.keepAlive] = v }; entity.alwaysShow?.let { v -> it[MenuTable.alwaysShow] = v }; entity.updater?.let { v -> it[MenuTable.updater] = v }; it[MenuTable.updateTime] = requireNotNull(entity.updateTime)
    } } }
    fun deleteById(id: Long): Int = transaction { MenuTable.update(where = { conditions(MenuTable.id eq id) }) { it[MenuTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>) = listOf(MenuTable.deleted eq false, *extra).compoundAnd()
    private fun toEntity(row: ResultRow) = MenuDO().apply { id = row[MenuTable.id]; name = row[MenuTable.name]; permission = row[MenuTable.permission]; type = row[MenuTable.type]; sort = row[MenuTable.sort]; parentId = row[MenuTable.parentId]; path = row[MenuTable.path]; icon = row[MenuTable.icon]; component = row[MenuTable.component]; componentName = row[MenuTable.componentName]; status = row[MenuTable.status]; visible = row[MenuTable.visible]; keepAlive = row[MenuTable.keepAlive]; alwaysShow = row[MenuTable.alwaysShow]; creator = row[MenuTable.creator]; createTime = row[MenuTable.createTime]; updater = row[MenuTable.updater]; updateTime = row[MenuTable.updateTime]; deleted = row[MenuTable.deleted] }
}
