package im.hikaru.harness.client.page

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*

/** True only when the page navigator is displayed alongside the content, rather than in a drawer. */
public val LocalWorkspaceNavigatorVisible = staticCompositionLocalOf { false }

/** Workspace pages own their column; the shell supplies the canvas and available viewport. */
@Composable
public fun WorkspacePageFrame(
    testTag: String, modifier: Modifier = Modifier, maxContentWidth: Dp = 1120.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().semantics { this.testTag = testTag }) {
        val inset = if (maxWidth < 600.dp) 16.dp else 32.dp
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxSize()
                .semantics { this.testTag = "$testTag-column" }, content = content)
        }
    }
}

@Composable
public fun WorkspacePageHeader(title: String, actions: @Composable () -> Unit = {}) {
    WorkspacePageHeader(title = { HarnessText(title, style = MaterialTheme.typography.headlineSmall) }, actions = actions)
}

@Composable
public fun WorkspacePageHeader(title: @Composable () -> Unit, actions: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title()
                actions()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { title() }
                actions()
            }
        }
    }
}

@Composable
public fun WorkspaceEmptyState(title: String, description: String? = null, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HarnessText(title, style = MaterialTheme.typography.titleLarge)
            description?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted) }
            action?.invoke()
        }
    }
}
