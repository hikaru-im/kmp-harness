package im.hikaru.harness.client.fixture

import im.hikaru.harness.client.shell.RecentTaskItem
import im.hikaru.harness.client.shell.WorkspaceItemKind
import im.hikaru.harness.client.shell.WorkspaceItemNode
import im.hikaru.harness.client.shell.WorkspaceProjectNode

/** A file row inside a change card or the project context panel. */
public data class ReferenceChangedFile(
    val path: String,
    val added: Int,
    val removed: Int,
)

public enum class ReferenceBlockKind {
    Paragraph,
    Code,
    ChangeCard,
    Checklist,
}

public data class ReferenceChecklistItem(
    val label: String,
    val done: Boolean,
)

public data class ReferenceBlock(
    val kind: ReferenceBlockKind,
    val text: String = "",
    val title: String? = null,
    val files: List<ReferenceChangedFile> = emptyList(),
    val items: List<ReferenceChecklistItem> = emptyList(),
)

public data class ReferenceTurn(
    val author: String,
    val isUser: Boolean,
    val blocks: List<ReferenceBlock>,
)

public data class ReferenceContextPanel(
    val workspacePath: String,
    val branch: String,
    val host: String,
    val changedFiles: List<ReferenceChangedFile>,
    val tasks: List<String>,
)

public data class ReferenceThread(
    val title: String,
    val projectName: String,
    val turns: List<ReferenceTurn>,
    val context: ReferenceContextPanel,
    val projects: List<WorkspaceProjectNode>,
    val recentTasks: List<RecentTaskItem>,
)

/**
 * Visual acceptance fixture matching the user's Codex reference screenshot: the kmp-harness project,
 * the "查看项目状态" thread, a change card, recent tasks and a project context panel.
 */
public object HarnessReferenceFixture {
    public val thread: ReferenceThread =
        ReferenceThread(
            title = "查看项目状态",
            projectName = "kmp-harness",
            turns =
                listOf(
                    ReferenceTurn(
                        author = "You",
                        isUser = true,
                        blocks =
                            listOf(
                                ReferenceBlock(kind = ReferenceBlockKind.Paragraph, text = "查看项目状态"),
                            ),
                    ),
                    ReferenceTurn(
                        author = "Codex",
                        isUser = false,
                        blocks =
                            listOf(
                                ReferenceBlock(
                                    kind = ReferenceBlockKind.Paragraph,
                                    text = "正在检查 kmp-harness 的工作树、模块图和 Desktop Shell 的当前状态。",
                                ),
                                ReferenceBlock(
                                    kind = ReferenceBlockKind.Checklist,
                                    title = "计划",
                                    items =
                                        listOf(
                                            ReferenceChecklistItem("读取 app.asar 的导航与 Token 证据", done = true),
                                            ReferenceChecklistItem("核对左侧导航宽度与 Shell 区域比例", done = true),
                                            ReferenceChecklistItem("挂载 Project Context Panel 与 Thread Composer", done = true),
                                        ),
                                ),
                                ReferenceBlock(
                                    kind = ReferenceBlockKind.Code,
                                    title = "检查",
                                    text = "./kotlin test --include-module=shared --platform=jvm",
                                ),
                                ReferenceBlock(
                                    kind = ReferenceBlockKind.Paragraph,
                                    text = "左侧导航已恢复成单列 275px，Thread 作为主内容区，右侧 Project Context Panel 显示仓库、分支与变更文件。",
                                ),
                                ReferenceBlock(
                                    kind = ReferenceBlockKind.ChangeCard,
                                    title = "变更",
                                    files =
                                        listOf(
                                            ReferenceChangedFile("apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt", 214, 268),
                                            ReferenceChangedFile("modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt", 132, 24),
                                            ReferenceChangedFile("modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTheme.kt", 96, 40),
                                            ReferenceChangedFile("apps/shared/src/im/hikaru/harness/client/page/ThreadPage.kt", 186, 0),
                                        ),
                                ),
                            ),
                    ),
                ),
            context =
                ReferenceContextPanel(
                    workspacePath = "/Volumes/Data/IdeaProjects/kmp-harness",
                    branch = "main",
                    host = "本地工作区",
                    changedFiles =
                        listOf(
                            ReferenceChangedFile("shell/AppShell.kt", 214, 268),
                            ReferenceChangedFile("ui-foundation/HarnessTokens.kt", 132, 24),
                            ReferenceChangedFile("ui-foundation/HarnessTheme.kt", 96, 40),
                            ReferenceChangedFile("page/ThreadPage.kt", 186, 0),
                            ReferenceChangedFile("fixture/ReferenceFixture.kt", 96, 0),
                        ),
                    tasks =
                        listOf(
                            "核对导航几何与 app.asar 一致",
                            "接通 Project Context Panel 数据",
                            "同尺寸截图对照参考线程",
                        ),
                ),
            projects =
                listOf(
                    WorkspaceProjectNode(
                        projectId = "kmp-harness",
                        name = "kmp-harness",
                        pinned = true,
                        children =
                            listOf(
                                WorkspaceItemNode(id = "view-project-status", title = "查看项目状态", kind = WorkspaceItemKind.Thread),
                                WorkspaceItemNode(id = "restore-shell-geometry", title = "恢复 Shell 几何", kind = WorkspaceItemKind.Thread),
                                WorkspaceItemNode(id = "wire-context-panel", title = "接通 Context Panel", kind = WorkspaceItemKind.Task),
                            ),
                    ),
                    WorkspaceProjectNode(
                        projectId = "harness-docs",
                        name = "harness-docs",
                        pinned = false,
                        children =
                            listOf(
                                WorkspaceItemNode(id = "nav-plan", title = "导航页面规划", kind = WorkspaceItemKind.Thread),
                            ),
                    ),
                ),
            recentTasks =
                listOf(
                    RecentTaskItem(id = "view-project-status", title = "查看项目状态", status = "已完成"),
                    RecentTaskItem(id = "restore-shell-geometry", title = "恢复 Shell 几何", status = "进行中"),
                    RecentTaskItem(id = "wire-context-panel", title = "接通 Context Panel", status = "已完成"),
                    RecentTaskItem(id = "light-theme-tokens", title = "Light Token 对齐", status = "已完成"),
                ),
        )
}
