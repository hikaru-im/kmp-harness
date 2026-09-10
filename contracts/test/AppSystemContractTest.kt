package im.hikaru.contracts.app.system

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class AppSystemContractTest {
    @Test
    fun areaTreePreservesNestedChildren() {
        val json = Json.encodeToString(
            listOf(
                AppAreaNodeResponse(
                    id = 1,
                    name = "Province",
                    children = listOf(AppAreaNodeResponse(id = 2, name = "City")),
                ),
            ),
        )

        assertTrue("\"id\":1" in json)
        assertTrue("\"children\":[{\"id\":2" in json)
    }

    @Test
    fun dictionaryResponseUsesAppFieldNames() {
        val json = Json.encodeToString(
            AppDictDataResponse(
                id = 9L,
                label = "Female",
                value = "2",
                dictType = "user_sex",
            ),
        )

        assertTrue("\"dictType\":\"user_sex\"" in json)
        assertTrue("\"value\":\"2\"" in json)
    }
}
