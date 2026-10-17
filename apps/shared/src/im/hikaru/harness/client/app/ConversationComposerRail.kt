package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.page.localProjectsForBinding
import im.hikaru.harness.client.projects.LocalProject
import im.hikaru.harness.client.projects.LocalProjectBinding
import im.hikaru.harness.client.projects.LocalProjectId
import im.hikaru.harness.client.projects.LocalProjectsPhase
import im.hikaru.harness.client.projects.LocalProjectsState
import im.hikaru.harness.ui.foundation.*

/**
 * Live Codex home composer rail (`data-composer-rail`, placement `above`): a 38dp strip tucked under
 * the composer card, holding the project selector, the work-location chip and the environment gear.
 * Threads have no rail, so this is only mounted on the home page.
 *
 * The strip is inset 13dp from the card and carries a 16dp top radius. [HarnessTokens.Composer.RailOverlap]
 * of its bottom is covered by the card, which is why the caller offsets the card by `RailHeight`
 * while this composable paints `RailHeight + RailOverlap` of surface.
 */
@Composable
internal fun ComposerProjectRail(
    state: LocalProjectsState,
    binding: LocalProjectBinding?,
    enabled: Boolean,
    onSelect: (LocalProjectId?) -> Unit,
    onQueryChange: (String) -> Unit,
    onManage: () -> Unit,
    onConfigureEnvironment: () -> Unit,
) {
    val visible = localProjectsForBinding(state, binding)
    val latestState by rememberUpdatedState(visible)
    val latestBinding by rememberUpdatedState(binding)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestEnabled by rememberUpdatedState(enabled)
    val selectable = enabled && visible.mutation == null &&
        visible.phase in listOf(LocalProjectsPhase.Ready, LocalProjectsPhase.Empty)
    val project = visible.selectedProject
    var projectOpen by remember(binding) { mutableStateOf(false) }
    var workOpen by remember(binding) { mutableStateOf(false) }
    fun current(): Boolean {
        val captured = binding
        return latestEnabled && latestState.mutation == null && if (captured == null) latestState.binding == null
        else latestBinding?.sameOwner(captured) == true && latestState.binding?.sameOwner(captured) == true
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Composer.RailInset)) {
        Box(
            Modifier.fillMaxWidth()
                .height(HarnessTokens.Composer.RailHeight + HarnessTokens.Composer.RailOverlap)
                .clip(RoundedCornerShape(topStart = HarnessTokens.Composer.RailRadius, topEnd = HarnessTokens.Composer.RailRadius))
                .background(ConversationAppearance.railSurface)
                .semantics { testTag = "local-project-picker" },
        ) {
            Row(
                Modifier.fillMaxWidth().height(HarnessTokens.Composer.RailHeight)
                    .padding(start = HarnessTokens.Composer.RailChipInset, end = HarnessTokens.Composer.RailChipInset,
                        top = 5.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.RailChipSpacing),
            ) {
                HarnessPopover(
                    expanded = projectOpen,
                    onDismissRequest = { projectOpen = false },
                    onExpandedChange = { if (!it || selectable) projectOpen = it },
                    side = HarnessPopoverSide.Top,
                    align = HarnessPopoverAlign.Start,
                    sideOffset = 6.dp,
                    popupWidth = HarnessTokens.Composer.ProjectPopupWidth,
                    popupMaxHeight = HarnessTokens.Composer.ProjectPopupMaxHeight,
                    popupShape = 20.dp,
                    popupColor = ConversationAppearance.popup,
                    popupBorder = ConversationAppearance.popupRing,
                    popupBorderWidth = 0.5.dp,
                    popupShadow = 6.dp,
                    popupModifier = Modifier.padding(4.dp).semantics { testTag = "local-project-picker-menu" },
                    anchor = { toggle ->
                        ProjectChip(
                            label = project?.name ?: "选择项目",
                            selected = project != null,
                            enabled = selectable,
                            expanded = projectOpen,
                            onOpen = { if (selectable && current()) { workOpen = false; toggle() } },
                            onClear = {
                                if (selectable && current()) {
                                    latestSelect(null)
                                    projectOpen = false
                                }
                            },
                        )
                    },
                ) {
                    ProjectMenu(
                        state = visible,
                        selected = project,
                        selectable = selectable,
                        onQueryChange = onQueryChange,
                        onSelect = { id ->
                            if (selectable && current() && latestState.projects.any { it.id == id }) {
                                latestSelect(id)
                                projectOpen = false
                            }
                        },
                        onClear = {
                            if (selectable && current()) {
                                latestSelect(null)
                                projectOpen = false
                            }
                        },
                        onManage = {
                            projectOpen = false
                            onManage()
                        },
                    )
                }
                HarnessPopover(
                    expanded = workOpen,
                    onDismissRequest = { workOpen = false },
                    onExpandedChange = { workOpen = it },
                    side = HarnessPopoverSide.Top,
                    align = HarnessPopoverAlign.Start,
                    sideOffset = 6.dp,
                    popupWidth = HarnessTokens.Composer.WorkPopupWidth,
                    popupShape = 20.dp,
                    popupColor = ConversationAppearance.popup,
                    popupBorder = ConversationAppearance.popupRing,
                    popupBorderWidth = 0.5.dp,
                    popupShadow = 6.dp,
                    popupModifier = Modifier.padding(4.dp).semantics { testTag = "live-composer-work-menu" },
                    anchor = { toggle ->
                        RailChip(onClick = {
                            projectOpen = false
                            toggle()
                        }, testTag = "live-composer-work-location", description = "工作位置") {
                            ConversationIcon(ConversationGlyph.Monitor, ConversationAppearance.ink)
                            RailLabel("此计算机")
                        }
                    },
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(HarnessTokens.Composer.RailSectionHeight)
                            .padding(horizontal = HarnessTokens.Composer.RailChipInsetX),
                        contentAlignment = Alignment.CenterStart,
                    ) { RailLabel("工作位置", ConversationAppearance.tertiary) }
                    RailMenuRow(
                        selected = true,
                        testTag = "live-composer-work-location-local",
                        onClick = { workOpen = false },
                        leading = { ConversationIcon(ConversationGlyph.Monitor, ConversationAppearance.ink) },
                        trailing = { ConversationIcon(ConversationGlyph.CheckSmall, ConversationAppearance.ink) },
                    ) { RailLabel("此计算机") }
                }
                Spacer(Modifier.weight(1f))
                // Live only renders the environment gear once a project is selected.
                if (project != null) RailGear(onClick = onConfigureEnvironment)
            }
        }
    }
}

@Composable
private fun ProjectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    expanded: Boolean,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    HarnessTooltipTrigger(
        text = if (selected) "更改此聊天的项目 ⇧⌘O" else "选择项目",
        enabled = enabled,
        popupSuppressed = expanded,
        railPill = true,
        trigger = { tooltip ->
            Row(
                Modifier.height(HarnessTokens.Composer.RailChipHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (hovered) ConversationAppearance.railHover else Color.Transparent)
                    .hoverable(interaction)
                    .then(tooltip)
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onOpen)
                    .padding(start = if (selected) 2.dp else 0.dp, end = HarnessTokens.Composer.RailChipInsetX)
                    .semantics { testTag = "local-project-picker-open" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.RailChipGap),
            ) {
                if (selected) {
                    // Live swaps the folder for a clear-circle on hover; the whole 28dp slot clears.
                    Box(
                        Modifier.size(HarnessTokens.Composer.RailChipHeight)
                            .semantics { contentDescription = "不在项目中工作" }
                            .clickable(enabled = enabled, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConversationIcon(if (hovered) ConversationGlyph.CircleX else ConversationGlyph.Folder,
                            ConversationAppearance.ink)
                    }
                } else {
                    Box(Modifier.padding(start = HarnessTokens.Composer.RailChipInsetX), contentAlignment = Alignment.Center) {
                        ConversationIcon(ConversationGlyph.Folder, ConversationAppearance.ink)
                    }
                }
                RailLabel(label, maxLines = 1)
            }
        },
    )
}

@Composable
private fun ProjectMenu(
    state: LocalProjectsState,
    selected: LocalProject?,
    selectable: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (LocalProjectId) -> Unit,
    onClear: () -> Unit,
    onManage: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var query by remember(state.binding) { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(state.binding) {
        if (state.query.isNotEmpty()) onQueryChange("")
        focus.requestFocus()
    }
    SearchRow(query, { value ->
        query = value
        onQueryChange(value.text)
    }, focus)
    Divider()
    state.visibleProjects.forEach { project ->
        RailMenuRow(
            selected = selected?.id == project.id,
            testTag = "local-project-picker-item-${project.id.value}",
            onClick = { if (selectable) onSelect(project.id) },
            leading = { ConversationIcon(ConversationGlyph.Folder, ConversationAppearance.ink) },
            trailing = { if (selected?.id == project.id) ConversationIcon(ConversationGlyph.CheckSmall, ConversationAppearance.ink) },
        ) {
            RailLabel(project.name, maxLines = 1)
            // Live appends the directory's own name in 49.8% white.
            RailLabel(project.roots.single().path.trimEnd('/').substringAfterLast('/'), ConversationAppearance.tertiary,
                maxLines = 1)
        }
    }
    // The live menu has no "no match" row: only the create action remains below the divider.
    if (state.visibleProjects.isNotEmpty() || state.query.isNotBlank()) Divider()
    RailMenuRow(
        selected = false,
        testTag = "local-project-manage",
        onClick = { if (selectable) onManage() },
        leading = { ConversationIcon(ConversationGlyph.PlusLarge, ConversationAppearance.ink) },
    ) { RailLabel("新建项目") }
    if (selected != null) {
        RailMenuRow(
            selected = false,
            testTag = "local-project-picker-item-none",
            onClick = onClear,
            leading = { ConversationIcon(ConversationGlyph.CrossLarge, ConversationAppearance.ink) },
        ) { RailLabel("不在项目中工作") }
    }
}

@Composable
private fun SearchRow(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, focus: FocusRequester) {
    Row(
        Modifier.fillMaxWidth().height(HarnessTokens.Composer.RailSearchHeight)
            .semantics { testTag = "local-project-search-row" }
            .padding(horizontal = HarnessTokens.Composer.RailChipInsetX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.RailChipGap),
    ) {
        ConversationIcon(ConversationGlyph.Search, ConversationAppearance.tertiary, 14.dp)
        Box(Modifier.weight(1f)) {
            if (value.text.isEmpty()) RailLabel("搜索项目", ConversationAppearance.tertiary)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .semantics { testTag = "local-project-search" },
                textStyle = railTextStyle(ConversationAppearance.ink),
                cursorBrush = SolidColor(ConversationAppearance.ink),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Composer.RailChipInsetX, vertical = 4.dp)
        .height(1.dp).background(ConversationAppearance.railDivider))
}

@Composable
private fun RailMenuRow(
    selected: Boolean,
    testTag: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val modifier = Modifier.fillMaxWidth().height(HarnessTokens.Composer.RailMenuRowHeight)
        .clip(RoundedCornerShape(HarnessTokens.Composer.RailMenuRowRadius))
        .background(if (selected) ConversationAppearance.railHover else Color.Transparent)
        .selectable(selected = selected, onClick = onClick)
        .padding(horizontal = HarnessTokens.Composer.RailChipInsetX)
        .then(if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier)
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.RailChipGap)) {
        leading()
        content()
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun RailChip(
    onClick: () -> Unit,
    testTag: String,
    description: String,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.height(HarnessTokens.Composer.RailChipHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (hovered) ConversationAppearance.railHover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = HarnessTokens.Composer.RailChipInsetX)
            .semantics { this.testTag = testTag; this.contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.RailChipGap),
        content = content,
    )
}

@Composable
private fun RailGear(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.width(HarnessTokens.Composer.RailGearWidth).height(HarnessTokens.Composer.RailChipHeight)
            .clip(RoundedCornerShape(HarnessTokens.Composer.RailGearRadius))
            .background(if (hovered) ConversationAppearance.railHover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { testTag = "live-composer-environment"; contentDescription = "配置本地环境" },
        contentAlignment = Alignment.Center,
    ) { ConversationIcon(ConversationGlyph.Gear, ConversationAppearance.tertiary) }
}

@Composable
private fun RailLabel(text: String, color: Color = ConversationAppearance.ink, maxLines: Int = Int.MAX_VALUE) {
    Text(text, color = color, style = railTextStyle(color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun railTextStyle(color: Color): TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = color)
