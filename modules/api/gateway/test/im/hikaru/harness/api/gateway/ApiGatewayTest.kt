package im.hikaru.harness.api.gateway

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.host.HostApiKey
import im.hikaru.harness.api.gateway.host.HostApiService
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.host.registerHostApi
import im.hikaru.harness.runtime.Context
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ApiGatewayTest {

    @Test
    fun shouldDispatchRegisteredEndpoint() = runTest {
        val gateway = ApiGateway()
        gateway.register(EchoEndpoint) { request ->
            ApiResult(
                code = ApiResult.SUCCESS_CODE,
                data = "echo:$request",
            )
        }

        val result = gateway.invoke(EchoEndpoint, "hello")

        assertTrue(result.isSuccess)
        assertEquals("echo:hello", result.data)
    }

    @Test
    fun shouldReturnNotFoundForUnknownOrDifferentEndpointToken() = runTest {
        val gateway = ApiGateway()
        gateway.register(EchoEndpoint) { request ->
            ApiResult(
                code = ApiResult.SUCCESS_CODE,
                data = request,
            )
        }

        val unknown = gateway.invoke(UnknownEndpoint, Unit)
        val differentToken = gateway.invoke(AnotherEchoEndpoint, "hello")

        assertEquals(GatewayResultCodes.NotFound, unknown.code)
        assertEquals(GatewayResultCodes.NotFound, differentToken.code)
    }

    @Test
    fun hostDescribeShouldReportMissingServiceAndReturnProvidedDescription() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerHostApi(owner)

        val missing = gateway.invoke(HostDescribeEndpoint, Unit)

        assertEquals(GatewayResultCodes.ConfigurationError, missing.code)

        val description = hostDescription()
        owner.provide(
            key = HostApiKey,
            service = HostApiService { description },
        )

        val result = gateway.invoke(HostDescribeEndpoint, Unit)

        assertTrue(result.isSuccess)
        assertSame(description, result.data)
    }

    @Test
    fun shouldMapHandlerFailureWithoutLeakingItsMessage() = runTest {
        val gateway = ApiGateway()
        gateway.register(EchoEndpoint) {
            error("sensitive handler detail")
        }

        val result = gateway.invoke(EchoEndpoint, "hello")

        assertEquals(GatewayResultCodes.InternalError, result.code)
        assertEquals("Harness API 处理失败", result.msg)
    }

    @Test
    fun shouldRejectInvalidHandlerResult() = runTest {
        val gateway = ApiGateway()
        gateway.register(EchoEndpoint) {
            ApiResult(
                code = ApiResult.SUCCESS_CODE,
                data = null,
            )
        }

        val result = gateway.invoke(EchoEndpoint, "hello")

        assertEquals(GatewayResultCodes.InternalError, result.code)
        assertEquals("Harness API 返回了无效结果", result.msg)
    }

    @Test
    fun shouldGuardDuplicatesAndRegistrationOwnership() = runTest {
        val gateway = ApiGateway()
        val first =
            gateway.register(EchoEndpoint) { request ->
                ApiResult(code = ApiResult.SUCCESS_CODE, data = "first:$request")
            }

        assertFailsWith<IllegalStateException> {
            gateway.register(EchoEndpoint) { request ->
                ApiResult(code = ApiResult.SUCCESS_CODE, data = "duplicate:$request")
            }
        }

        first.dispose()
        val second =
            gateway.register(EchoEndpoint) { request ->
                ApiResult(code = ApiResult.SUCCESS_CODE, data = "second:$request")
            }

        first.dispose()

        assertEquals("second:hello", gateway.invoke(EchoEndpoint, "hello").data)

        second.dispose()
        assertEquals(GatewayResultCodes.NotFound, gateway.invoke(EchoEndpoint, "hello").code)
    }

    @Test
    fun contextDisposalShouldUnregisterOwnedEndpoint() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.register(
            owner = owner,
            endpoint = EchoEndpoint,
        ) { request ->
            ApiResult(code = ApiResult.SUCCESS_CODE, data = request)
        }

        assertEquals(setOf("test.echo"), gateway.methods())

        owner.dispose()

        assertTrue(gateway.methods().isEmpty())
        assertEquals(GatewayResultCodes.NotFound, gateway.invoke(EchoEndpoint, "hello").code)
    }

    private fun hostDescription(): HostDescription =
        HostDescription(
            protocolVersion = ProtocolVersion(major = 1, minor = 0),
            hostId = HostId("desktop-test"),
            displayName = "Desktop Test",
        )

    private object EchoEndpoint : ApiEndpoint<String, String>("test.echo")
    private object AnotherEchoEndpoint : ApiEndpoint<String, String>("test.echo")
    private object UnknownEndpoint : ApiEndpoint<Unit, String>("test.unknown")
}
