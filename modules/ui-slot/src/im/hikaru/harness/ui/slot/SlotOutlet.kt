package im.hikaru.harness.ui.slot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

public val LocalUiSnapshot =
    compositionLocalOf { UiSnapshot.Empty }

@Composable
public fun <P : Any> SlotOutlet(
    key: SingleUiSlotKey<P>,
    props: P,
    fallback: @Composable () -> Unit = {},
) {
    val contribution = LocalUiSnapshot.current.single(key)
    if (contribution == null) {
        fallback()
    } else {
        contribution.content(props)
    }
}

@Composable
public fun <K : Any, P : Any> SlotOutlet(
    key: KeyedUiSlotKey<K, P>,
    cell: K,
    props: P,
    fallback: @Composable () -> Unit = {},
) {
    val contribution = LocalUiSnapshot.current.keyed(key, cell)
    if (contribution == null) {
        fallback()
    } else {
        contribution.content(cell, props)
    }
}

@Composable
public fun <P : Any> ListSlotOutlet(
    key: ListUiSlotKey<P>,
    props: P,
) {
    LocalUiSnapshot.current.list(key).forEach { contribution ->
        contribution.content(props)
    }
}

@Composable
public fun UiSnapshotProvider(
    snapshot: UiSnapshot,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalUiSnapshot provides snapshot, content = content)
}
