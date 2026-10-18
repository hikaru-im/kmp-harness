package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.ui.foundation.*

/** Shared geometry derived from the native Settings section and nested rows. */
@Composable
public fun SettingsFormFrame(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The shell owns centering; pages only add an inset when the content column is narrow.
        val narrowContent = maxWidth < HarnessTokens.Control.SettingsContentMaxWidth
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = HarnessTokens.Control.SettingsPageTopPadding, bottom = HarnessTokens.Spacing.Seven),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = HarnessTokens.Control.SettingsContentMaxWidth).fillMaxWidth()
                    // A stable geometry anchor for the shared page frame: the contract test measures this
                    // node against the Shell's own main content column.
                    .then(if (narrowContent) Modifier.padding(horizontal = 16.dp) else Modifier)
                    .semantics { this.testTag = testTag + "-column" },
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                HarnessText(
                    title,
                    // Settings > General measures its page title at 28px in the 2026-10-06 capture; the form
                    // pages are the same Settings section, so they share the size and the fixed 34px line box.
                    Modifier.height(34.dp).semantics { this.testTag = testTag },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp),
                )
                content()
            }
        }
    }
}

@Composable
public fun SettingsFormSection(title: String, titleTag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HarnessText(title, Modifier.semantics { titleTag?.let { testTag = it } }, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
public fun SettingsFormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(HarnessTokens.Control.SettingsCardRadius)
    Column(modifier.fillMaxWidth().background(HarnessTokens.Colors.SurfaceElevated, shape)
        .border(1.dp, HarnessTokens.Colors.BorderSubtle, shape)
        .padding(horizontal = HarnessTokens.Control.SettingsRowInsetX, vertical = 8.dp),
        content = content)
}

/** Secondary technical information stays available without competing with everyday settings. */
@Composable
public fun SettingsFormDetails(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HarnessButton(if (expanded) "收起$title" else title, { expanded = !expanded },
            variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact, testTag = testTag)
        if (expanded) content()
    }
}

@Composable
public fun SettingsFormRow(
    label: String, description: String? = null, modifier: Modifier = Modifier,
    status: String? = null, statusTag: String? = null, control: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 12.dp)) {
        val labelContent: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HarnessText(label, style = MaterialTheme.typography.bodyMedium)
                    status?.let {
                        HarnessText(it, Modifier.semantics { statusTag?.let { tag -> testTag = tag } },
                            color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                description?.takeIf { it.isNotBlank() }?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { labelContent(); control() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { labelContent() }
                Box(Modifier.widthIn(max = 272.dp)) { control() }
            }
        }
    }
}

@Composable
public fun <T> SettingsFormSelect(
    value: T, options: List<T>, label: (T) -> String, enabled: Boolean, testTag: String, onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    HarnessMenu(
        expanded = expanded, entries = options.mapIndexed { index, option ->
            HarnessMenuEntry.Item(index.toString(), label(option), selected = option == value, enabled = enabled)
        },
        onExpandedChange = { expanded = it && enabled }, onSelect = { key -> options.getOrNull(key.toInt())?.let(onSelect) },
        anchor = { toggle -> HarnessSelectTrigger(label(value), toggle, enabled = enabled, testTag = testTag) },
    )
}

/** Drafts stay local; only validated Enter/blur commits reach the settings owner. */
@Composable
public fun SettingsFormInput(
    value: String, enabled: Boolean, testTag: String, placeholder: String = "",
    validate: (String) -> String?, onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    var wasFocused by remember { mutableStateOf(false) }
    var attempted by remember(value) { mutableStateOf(false) }
    val currentCommit by rememberUpdatedState(onCommit)
    val currentValidate by rememberUpdatedState(validate)
    val currentEnabled by rememberUpdatedState(enabled)
    val errorMessage = if (attempted) validate(draft) else null
    fun commit() {
        if (!currentEnabled) return
        attempted = true
        if (currentValidate(draft) == null && draft != value) currentCommit(draft)
    }
    Column(Modifier.widthIn(min = 112.dp, max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = draft, onValueChange = { draft = it }, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = HarnessTokens.Colors.Text),
            cursorBrush = SolidColor(HarnessTokens.Colors.Text),
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                .onFocusChanged { state -> if (wasFocused && !state.isFocused) commit(); wasFocused = state.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) { commit(); true } else false
                }
                .semantics { this.testTag = testTag; if (errorMessage != null) error(errorMessage) }
                .border(1.dp, if (errorMessage == null) HarnessTokens.Colors.BorderStrong else HarnessTokens.Colors.Danger, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { field -> Box {
                if (draft.isEmpty()) HarnessText(placeholder, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                field()
            } },
        )
        errorMessage?.let { HarnessText(it, color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall) }
    }
}
