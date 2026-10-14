package im.hikaru.harness.ui.slot

import androidx.compose.runtime.Composable

public class UiContributionBuilder(
    private val schema: UiSchema,
) : UiContributionSink {
    private val singles =
        linkedMapOf<SingleUiSlotKey<*>, MutableList<UiSingleContribution<*>>>()
    private val keyed =
        linkedMapOf<KeyedUiSlotKey<*, *>, MutableMap<Any, MutableList<UiKeyedContribution<*, *>>>>()
    private val lists =
        linkedMapOf<ListUiSlotKey<*>, MutableList<UiListContribution<*>>>()

    private var sequence = 0L

    public var isFrozen: Boolean = false
        private set

    override fun <P : Any> contribute(
        key: SingleUiSlotKey<P>,
        order: Int,
        content: @Composable (P) -> Unit,
    ) {
        checkWritable()
        schema.requireDeclared(key)
        singles.getOrPut(key) { mutableListOf() } +=
            UiSingleContribution(key, order, sequence++, content)
    }

    override fun <K : Any, P : Any> contribute(
        key: KeyedUiSlotKey<K, P>,
        cell: K,
        order: Int,
        content: @Composable (K, P) -> Unit,
    ) {
        checkWritable()
        schema.requireDeclared(key)
        val cells = keyed.getOrPut(key) { linkedMapOf() }
        cells.getOrPut(cell) { mutableListOf() } +=
            UiKeyedContribution(key, cell, order, sequence++, content)
    }

    override fun <P : Any> contribute(
        key: ListUiSlotKey<P>,
        id: String,
        order: Int,
        content: @Composable (P) -> Unit,
    ) {
        checkWritable()
        require(id.isNotBlank()) { "UI list contribution id must not be blank" }
        schema.requireDeclared(key)
        lists.getOrPut(key) { mutableListOf() } +=
            UiListContribution(key, id, order, sequence++, content)
    }

    public fun freeze(): UiSnapshot {
        check(!isFrozen) { "UI contribution builder is already frozen" }
        isFrozen = true

        val frozenSingles =
            singles.mapValues { (key, values) ->
                val sorted = values.sortedWith(orderComparator())
                sorted.zipWithNext().forEach { (first, second) ->
                    check(first.order != second.order) {
                        "UI single slot '${key.name}' has conflicting order ${first.order}"
                    }
                }
                sorted
            }

        val frozenKeyed =
            keyed.mapValues { (key, cells) ->
                cells.mapValues { (cell, values) ->
                    val sorted = values.sortedWith(orderComparator())
                    sorted.zipWithNext().forEach { (first, second) ->
                        check(first.order != second.order) {
                            "UI keyed slot '${key.name}' cell '$cell' has conflicting order ${first.order}"
                        }
                    }
                    sorted
                }
            }

        val frozenLists =
            lists.mapValues { (key, values) ->
                val duplicates = values.groupingBy { it.id }.eachCount().filterValues { it > 1 }
                check(duplicates.isEmpty()) {
                    "UI list slot '${key.name}' contains duplicate id(s): ${duplicates.keys.joinToString()}"
                }
                values.sortedWith(orderComparator())
            }

        return UiSnapshot(
            singles = frozenSingles,
            keyed = frozenKeyed,
            lists = frozenLists,
        )
    }

    private fun checkWritable() {
        check(!isFrozen) { "UI contributions are frozen" }
    }

    private fun <T : UiOrderedContribution> orderComparator(): Comparator<T> =
        compareBy<T> { it.order }.thenBy { it.sequence }
}

internal interface UiOrderedContribution {
    val order: Int
    val sequence: Long
}

public class UiSingleContribution<P : Any> internal constructor(
    public val key: SingleUiSlotKey<P>,
    override val order: Int,
    override val sequence: Long,
    public val content: @Composable (P) -> Unit,
) : UiOrderedContribution

public class UiKeyedContribution<K : Any, P : Any> internal constructor(
    public val key: KeyedUiSlotKey<K, P>,
    public val cell: K,
    override val order: Int,
    override val sequence: Long,
    public val content: @Composable (K, P) -> Unit,
) : UiOrderedContribution

public class UiListContribution<P : Any> internal constructor(
    public val key: ListUiSlotKey<P>,
    public val id: String,
    override val order: Int,
    override val sequence: Long,
    public val content: @Composable (P) -> Unit,
) : UiOrderedContribution
