package im.hikaru.ruoyi.framework.mybatis.core.mapper

import org.jetbrains.exposed.v1.core.FieldSet
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.deleteWhere as exposedDeleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll as exposedSelectAll
import org.jetbrains.exposed.v1.jdbc.update as exposedUpdate
import java.util.concurrent.CopyOnWriteArrayList

/** Database operation that can be constrained by an [ExposedDataAccessPolicy]. */
enum class ExposedDataOperation {
    SELECT,
    UPDATE,
    DELETE,
}

/** Supplies an additional predicate for a table before an Exposed statement is built. */
fun interface ExposedDataAccessPolicy {
    fun getCondition(table: Table, operation: ExposedDataOperation): Op<Boolean>?
}

/** Process-wide registry used by the framework policies configured by Spring Boot starters. */
object ExposedDataAccessPolicyRegistry {
    private val policies = CopyOnWriteArrayList<ExposedDataAccessPolicy>()

    fun register(policy: ExposedDataAccessPolicy): AutoCloseable {
        policies.addIfAbsent(policy)
        return AutoCloseable { policies.remove(policy) }
    }

    internal fun getCondition(tables: Iterable<Table>, operation: ExposedDataOperation): Op<Boolean>? {
        val conditions = tables
            .distinct()
            .flatMap { table -> policies.mapNotNull { it.getCondition(table, operation) } }
        return conditions.takeIf { it.isNotEmpty() }?.compoundAnd()
    }
}

/**
 * Policy-aware replacement for Exposed's [exposedSelectAll].
 *
 * The predicate is added when Exposed first renders the query, before its argument list is captured. This keeps
 * policy values parameterized and also lets callers add their regular `where` clause after `selectAll()`.
 */
fun FieldSet.selectAll(): Query = PolicyQuery(this, null)

/** Policy-aware replacement for Exposed's update with an explicit predicate. */
fun <T : Table> T.update(
    where: () -> Op<Boolean>,
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit,
): Int = exposedUpdate(
    where = { combineWithPolicy(this@update, ExposedDataOperation.UPDATE, where()) },
    limit = limit,
    body = body,
)

/** Policy-aware replacement for Exposed's update of every permitted row. */
fun <T : Table> T.update(
    limit: Int? = null,
    body: T.(UpdateStatement) -> Unit,
): Int {
    val policyCondition = ExposedDataAccessPolicyRegistry.getCondition(listOf(this), ExposedDataOperation.UPDATE)
    return if (policyCondition == null) {
        exposedUpdate(limit = limit, body = body)
    } else {
        exposedUpdate(where = { policyCondition }, limit = limit, body = body)
    }
}

/** Policy-aware replacement for Exposed's delete. */
fun <T : Table> T.deleteWhere(
    limit: Int? = null,
    op: T.() -> Op<Boolean>,
): Int = exposedDeleteWhere(limit = limit) {
    combineWithPolicy(this@deleteWhere, ExposedDataOperation.DELETE, op())
}

private fun combineWithPolicy(
    table: Table,
    operation: ExposedDataOperation,
    original: Op<Boolean>,
): Op<Boolean> {
    val policyCondition = ExposedDataAccessPolicyRegistry.getCondition(listOf(table), operation)
    return if (policyCondition == null) original else listOf(original, policyCondition).compoundAnd()
}

private class PolicyQuery(
    set: FieldSet,
    where: Op<Boolean>?,
    private var policyApplied: Boolean = false,
) : Query(set, where) {

    override fun copy(): Query = PolicyQuery(set, where, policyApplied).also(::copyTo)

    override fun arguments() = applyPolicy().let { super.arguments() }

    override fun prepareSQL(builder: QueryBuilder): String {
        applyPolicy()
        return super.prepareSQL(builder)
    }

    private fun applyPolicy(): PolicyQuery = apply {
        if (policyApplied) return@apply
        policyApplied = true
        val condition = ExposedDataAccessPolicyRegistry.getCondition(
            set.source.policyTables(),
            ExposedDataOperation.SELECT,
        ) ?: return@apply
        adjustWhere {
            if (this == null) condition else listOf(this, condition).compoundAnd()
        }
    }
}

private fun ColumnSet.policyTables(): List<Table> = columns.map { it.table }.distinct()
