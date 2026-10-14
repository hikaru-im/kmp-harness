package im.hikaru.harness.ui.slot

@Suppress("UNCHECKED_CAST")
public class UiSnapshot internal constructor(
    private val singles: Map<SingleUiSlotKey<*>, List<UiSingleContribution<*>>>,
    private val keyed: Map<KeyedUiSlotKey<*, *>, Map<Any, List<UiKeyedContribution<*, *>>>>,
    private val lists: Map<ListUiSlotKey<*>, List<UiListContribution<*>>>,
) {
    public fun <P : Any> single(
        key: SingleUiSlotKey<P>,
    ): UiSingleContribution<P>? =
        singles[key]?.firstOrNull() as UiSingleContribution<P>?

    public fun <K : Any, P : Any> keyed(
        key: KeyedUiSlotKey<K, P>,
        cell: K,
    ): UiKeyedContribution<K, P>? =
        keyed[key]?.get(cell)?.firstOrNull() as UiKeyedContribution<K, P>?

    public fun <P : Any> list(
        key: ListUiSlotKey<P>,
    ): List<UiListContribution<P>> =
        lists[key].orEmpty() as List<UiListContribution<P>>

    public companion object {
        public val Empty: UiSnapshot =
            UiSnapshot(
                singles = emptyMap(),
                keyed = emptyMap(),
                lists = emptyMap(),
            )
    }
}
