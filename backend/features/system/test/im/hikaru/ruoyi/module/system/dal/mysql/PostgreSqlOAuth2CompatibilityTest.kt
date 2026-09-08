package im.hikaru.ruoyi.module.system.dal.mysql

import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenDao
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

class PostgreSqlOAuth2CompatibilityTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRESQL_INTEGRATION_URL", matches = ".+")
    fun `access token lookup binds the smallint deletion flag correctly`() {
        Database.connect(
            url = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_URL")),
            driver = "org.postgresql.Driver",
            user = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_USERNAME")),
            password = requireNotNull(System.getenv("POSTGRESQL_INTEGRATION_PASSWORD")),
        )

        assertNull(OAuth2AccessTokenDao.selectByAccessToken("__postgresql_compatibility_test__"))
    }
}
