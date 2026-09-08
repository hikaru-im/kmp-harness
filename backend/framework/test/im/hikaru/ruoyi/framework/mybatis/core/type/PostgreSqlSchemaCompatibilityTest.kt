package im.hikaru.ruoyi.framework.mybatis.core.type

import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgreSqlSchemaCompatibilityTest {

    @Test
    fun `explicit sequence and smallint boolean match the reference schema`() {
        Database.connect(
            "jdbc:h2:mem:schema_compatibility_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction {
            exec("CREATE SEQUENCE schema_compatibility_seq START WITH 1")
            exec(
                "CREATE TABLE schema_compatibility (" +
                    "id BIGINT NOT NULL PRIMARY KEY, " +
                    "deleted SMALLINT NOT NULL DEFAULT 0)",
            )

            val id = SchemaCompatibilityTable.insert { }.get(SchemaCompatibilityTable.id)

            assertEquals(1L, id)
            assertEquals(
                1L,
                SchemaCompatibilityTable.selectAll()
                    .where { SchemaCompatibilityTable.deleted eq false }
                    .count(),
            )
        }
    }

    private object SchemaCompatibilityTable : Table("schema_compatibility") {
        val id = long("id").autoIncrement("schema_compatibility_seq")
        val deleted = smallIntBoolean("deleted").default(false)
        override val primaryKey = PrimaryKey(id)
    }
}
