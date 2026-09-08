package im.hikaru.ruoyi.framework.common.util

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.enums.DateIntervalEnum
import im.hikaru.ruoyi.framework.common.pojo.SortablePageParam
import im.hikaru.ruoyi.framework.common.pojo.SortingField
import im.hikaru.ruoyi.framework.common.util.collection.ArrayUtils
import im.hikaru.ruoyi.framework.common.util.collection.MapUtils
import im.hikaru.ruoyi.framework.common.util.date.LocalDateTimeUtils
import im.hikaru.ruoyi.framework.common.util.io.FileUtils
import im.hikaru.ruoyi.framework.common.util.io.IoUtils
import im.hikaru.ruoyi.framework.common.util.number.MoneyUtils
import im.hikaru.ruoyi.framework.common.util.number.NumberUtils
import im.hikaru.ruoyi.framework.common.util.`object`.PageUtils
import im.hikaru.ruoyi.framework.common.util.spring.SpringUtils
import im.hikaru.ruoyi.framework.common.validation.Telephone
import jakarta.validation.Validation
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

class CommonUtilityParityTest {

    @Test
    fun `date utilities parse convert and split ranges`() {
        assertEquals(LocalDateTime.of(2026, 7, 18, 0, 0), LocalDateTimeUtils.parse("2026-07-18"))
        assertEquals(LocalDateTime.of(1970, 1, 1, 8, 0), LocalDateTimeUtils.ofEpochSecond(0))
        assertEquals(0L, LocalDateTimeUtils.toEpochSecond(LocalDateTime.of(1970, 1, 1, 8, 0)))

        val ranges = LocalDateTimeUtils.getDateRangeList(
            LocalDateTime.of(2026, 7, 18, 13, 30),
            LocalDateTime.of(2026, 7, 18, 14, 30),
            DateIntervalEnum.HOUR.interval,
        )
        assertEquals(24, ranges.size)
        assertEquals(LocalDateTime.of(2026, 7, 18, 0, 0), ranges.first()[0])
        assertEquals(LocalDateTime.of(2026, 7, 18, 23, 59, 59, 999_999_999), ranges.last()[1])
        assertEquals(
            listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 19)),
            LocalDateTimeUtils.getDateList(LocalDate.of(2026, 7, 18), 2),
        )
    }

    @Test
    fun `collection number and money helpers preserve reference behavior`() {
        assertArrayEquals(arrayOf("1", "2"), ArrayUtils.toArray(listOf(1, 2)) { it.toString() })
        assertNull(ArrayUtils.get(arrayOf("a"), 2))
        assertEquals(listOf("a", "b"), MapUtils.getList(mapOf(1 to listOf("a"), 2 to listOf("b")), listOf(1, 2)))
        assertEquals(mapOf("a" to 1), MapUtils.convertMap(listOf(KeyValue("a", 1))))
        assertEquals(BigDecimal("12.50"), MapUtils.getBigDecimal(mapOf("price" to "12.50"), "price"))

        assertEquals(602, MoneyUtils.calculator(1000, 1, 6020))
        assertEquals("0.01", MoneyUtils.fenToYuanStr(1))
        assertEquals(BigDecimal("6.00"), MoneyUtils.priceMultiply(BigDecimal("2.40"), BigDecimal("2.5")))
        assertEquals(BigDecimal("6"), NumberUtils.mul(BigDecimal("2"), BigDecimal("3")))
        assertNull(NumberUtils.mul(BigDecimal.ONE, null))
        assertTrue(NumberUtils.isAllNumber(listOf("1", "-2.5")))
        assertFalse(NumberUtils.isAllNumber(emptyList()))
    }

    @Test
    fun `file io page and spring compatibility helpers work without hutool`() {
        val file = FileUtils.createTempFile("hello")
        try {
            assertEquals("hello", file.readText())
        } finally {
            file.delete()
        }

        val stream = TrackingInputStream("world".toByteArray())
        assertEquals("world", IoUtils.readUtf8(stream, true))
        assertTrue(stream.closed)

        val page = SortablePageParam()
        PageUtils.buildDefaultSortingField(page, "createTime")
        assertEquals(listOf(SortingField("createTime", SortingField.ORDER_DESC)), page.sortingFields)
        assertEquals(20, PageUtils.getStart(page.apply { pageNo = 3; pageSize = 10 }))

        val context = mock(ApplicationContext::class.java)
        val environment = mock(Environment::class.java)
        `when`(context.environment).thenReturn(environment)
        `when`(environment.activeProfiles).thenReturn(arrayOf("prod"))
        `when`(environment.getProperty("feature.name")).thenReturn("migration")
        `when`(context.getBean("answer")).thenReturn(42)
        SpringUtils().setApplicationContext(context)
        assertTrue(SpringUtils.isProd())
        assertEquals("migration", SpringUtils.getProperty("feature.name"))
        assertEquals(42, SpringUtils.getBean("answer"))
    }

    @Test
    fun `telephone annotation accepts mobile landline and empty values`() {
        val validator = Validation.buildDefaultValidatorFactory().validator
        assertTrue(validator.validate(PhoneForm(null)).isEmpty())
        assertTrue(validator.validate(PhoneForm("15601691300")).isEmpty())
        assertTrue(validator.validate(PhoneForm("010-12345678")).isEmpty())
        assertTrue(validator.validate(PhoneForm("400-123-4567")).isEmpty())
        assertEquals(1, validator.validate(PhoneForm("not-a-phone")).size)
    }

    private class TrackingInputStream(data: ByteArray) : ByteArrayInputStream(data) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    private class PhoneForm(@field:Telephone val phone: String?)
}
