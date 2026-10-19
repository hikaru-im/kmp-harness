package im.hikaru.harness.exec.jvm

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.tools.ToolExecutionContext
import im.hikaru.harness.tools.ToolExecutionResult

/**
 * Local execution seam behind the shell tools. Shell syntax, command
 * defaulting, and presentation belong to `tool-bash` / `tool-pwsh`; this
 * service owns working-directory confinement, deadlines, bounded output,
 * background process inventory, and Host-scoped cleanup.
 */
public interface SubprocessService {
    /**
     * Run one fully specified command for the calling Session.
     *
     * @param command the argv, cwd override, background flag, timeout, and stdin disposition.
     * @param callId the model tool-call id; one call may start at most one process.
     * @param execution the trusted caller supplied by the Agent runtime.
     */
    public suspend fun execute(
        command: ExecCommand,
        callId: CallId,
        execution: ToolExecutionContext,
    ): ToolExecutionResult
}

public object SubprocessKey : ServiceKey<SubprocessService>("subprocess.local")
