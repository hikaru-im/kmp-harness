package im.hikaru.harness.api.gateway

/** API Gateway 使用的 RuoYi 通用数值结果码。 */
public object GatewayResultCodes {
    public const val BadRequest: Int = 400
    public const val NotFound: Int = 404
    public const val Conflict: Int = 409
    public const val Unsupported: Int = 422
    public const val Forbidden: Int = 403
    public const val InternalError: Int = 500
    public const val ConfigurationError: Int = 502
}
