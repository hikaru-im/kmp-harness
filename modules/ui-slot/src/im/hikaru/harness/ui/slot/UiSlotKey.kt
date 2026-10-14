package im.hikaru.harness.ui.slot

import androidx.compose.runtime.Composable

public enum class UiSlotKind {
    Single,
    Keyed,
    List,
}

public sealed interface UiSlotKey<out P : Any> {
    public val name: String
    public val kind: UiSlotKind
}

public class SingleUiSlotKey<P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    init {
        require(name.isNotBlank()) { "UI slot name must not be blank" }
    }

    override val kind: UiSlotKind = UiSlotKind.Single
}

public class KeyedUiSlotKey<K : Any, P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    init {
        require(name.isNotBlank()) { "UI slot name must not be blank" }
    }

    override val kind: UiSlotKind = UiSlotKind.Keyed
}

public class ListUiSlotKey<P : Any>(
    override val name: String,
) : UiSlotKey<P> {
    init {
        require(name.isNotBlank()) { "UI slot name must not be blank" }
    }

    override val kind: UiSlotKind = UiSlotKind.List
}

public class UiSchema(
    slots: List<UiSlotKey<*>>,
) {
    public val slots: List<UiSlotKey<*>> = slots.toList()

    private val identities: Set<UiSlotKey<*>> =
        this.slots.toSet()

    init {
        require(this.slots.distinctBy { it.name }.size == this.slots.size) {
            val duplicate =
                this.slots
                    .groupingBy(UiSlotKey<*>::name)
                    .eachCount()
                    .entries
                    .first { it.value > 1 }
                    .key
            "UI schema contains duplicate slot name '$duplicate'"
        }
        require(identities.size == this.slots.size) {
            "UI schema contains the same slot key more than once"
        }
    }

    internal fun requireDeclared(key: UiSlotKey<*>) {
        check(key in identities) {
            "UI slot '${key.name}' is not declared by the schema"
        }
    }
}

public interface UiContributionSink {
    public fun <P : Any> contribute(
        key: SingleUiSlotKey<P>,
        order: Int = 0,
        content: @Composable (P) -> Unit,
    )

    public fun <K : Any, P : Any> contribute(
        key: KeyedUiSlotKey<K, P>,
        cell: K,
        order: Int = 0,
        content: @Composable (K, P) -> Unit,
    )

    public fun <P : Any> contribute(
        key: ListUiSlotKey<P>,
        id: String,
        order: Int = 0,
        content: @Composable (P) -> Unit,
    )
}
