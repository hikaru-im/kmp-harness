package im.hikaru.ruoyi.module.infra.dal.mysql

import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiErrorLogDO
import im.hikaru.ruoyi.module.infra.dal.mysql.logger.ApiErrorLogDao
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class PostgreSqlInfraCompatibilityTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRESQL_INTEGRATION_URL", matches = ".+")
    fun `api error log uses the reference sequence and column types`() {
        connect()
        var insertedId: Long? = null

        try {
            transaction {
                val log = ApiErrorLogDO().apply {
                    traceId = "postgresql-compatibility-test"
                    userId = 0
                    userType = 0
                    applicationName = "yudao-integration-test"
                    requestMethod = "GET"
                    requestUrl = "/integration-test"
                    requestParams = "{}"
                    userIp = "127.0.0.1"
                    userAgent = "JUnit"
                    exceptionTime = LocalDateTime(2026, 7, 18, 14, 30)
                    exceptionName = "CompatibilityTestException"
                    exceptionMessage = "integration test"
                    exceptionRootCauseMessage = "integration test"
                    exceptionStackTrace = "integration test"
                    exceptionClassName = javaClass.name
                    exceptionFileName = "PostgreSqlInfraCompatibilityTest.kt"
                    exceptionMethodName = "api error log uses the reference sequence and column types"
                    exceptionLineNumber = 1
                    processStatus = 0
                }
                insertedId = ApiErrorLogDao.insert(log)

                val inserted = ApiErrorLogDao.selectById(requireNotNull(insertedId))
                assertNotNull(inserted)
                assertEquals("postgresql-compatibility-test", inserted?.traceId)
                rollback()
            }

            assertNull(ApiErrorLogDao.selectById(requireNotNull(insertedId)))
        } finally {
            insertedId?.let(ApiErrorLogDao::deleteById)
        }
    }

    private fun connect() {
        Database.connect(
            url = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_URL")),
            driver = "org.postgresql.Driver",
            user = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_USERNAME")),
            password = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_PASSWORD")),
        )
    }
}
