package im.hikaru.harness.client.projects

public data class ProjectFileSearchTarget(val binding: LocalProjectBinding, val project: LocalProject)
public data class ProjectFileMatch(val path: String)
public data class ProjectFileSearchResult(val files: List<ProjectFileMatch>, val truncated: Boolean = false)

/** Paths are project-relative; adapters validate the binding and containment on every operation. */
public interface ProjectFileSearch {
    public suspend fun search(target: ProjectFileSearchTarget, query: String): ProjectFileSearchResult
    public suspend fun read(target: ProjectFileSearchTarget, file: ProjectFileMatch): String
}

public object UnavailableProjectFileSearch : ProjectFileSearch {
    override suspend fun search(target: ProjectFileSearchTarget, query: String): ProjectFileSearchResult =
        error("当前平台或 Host 尚未接入文件搜索。")
    override suspend fun read(target: ProjectFileSearchTarget, file: ProjectFileMatch): String =
        error("当前平台或 Host 尚未接入文件预览。")
}
