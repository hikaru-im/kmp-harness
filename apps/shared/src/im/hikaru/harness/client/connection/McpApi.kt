package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.mcp.McpAuthenticateRequest
import im.hikaru.contracts.harness.mcp.McpAuthenticateResponse
import im.hikaru.contracts.harness.mcp.McpEnabledRequest
import im.hikaru.contracts.harness.mcp.McpListResponse
import im.hikaru.contracts.harness.mcp.McpMutationResponse
import im.hikaru.contracts.harness.mcp.McpRemoveRequest
import im.hikaru.contracts.harness.mcp.McpUpsertRequest

/** Client-facing MCP management API. Remote connections remain unavailable until relay support exists. */
public interface McpApi {
    public suspend fun list(): ApiResult<McpListResponse>
    public suspend fun upsert(request: McpUpsertRequest): ApiResult<McpMutationResponse>
    public suspend fun setEnabled(request: McpEnabledRequest): ApiResult<McpMutationResponse>
    public suspend fun remove(request: McpRemoveRequest): ApiResult<McpMutationResponse>
    public suspend fun authenticate(request: McpAuthenticateRequest): ApiResult<McpAuthenticateResponse>
}

public object UnavailableMcpApi : McpApi {
    private fun <T> unavailable(): ApiResult<T> = ApiResult(
        code = 502,
        msg = "MCP management is unavailable on this connection",
    )

    override suspend fun list(): ApiResult<McpListResponse> = unavailable()
    override suspend fun upsert(request: McpUpsertRequest): ApiResult<McpMutationResponse> = unavailable()
    override suspend fun setEnabled(request: McpEnabledRequest): ApiResult<McpMutationResponse> = unavailable()
    override suspend fun remove(request: McpRemoveRequest): ApiResult<McpMutationResponse> = unavailable()
    override suspend fun authenticate(request: McpAuthenticateRequest): ApiResult<McpAuthenticateResponse> = unavailable()
}
