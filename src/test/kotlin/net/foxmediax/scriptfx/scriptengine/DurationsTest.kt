package net.foxmediax.scriptfx.scriptengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationsTest {

    @Test
    fun `секунды`() {
        assertEquals(20L, Durations.parseTicks("1.sec"))
        assertEquals(40L, Durations.parseTicks("2.sec"))
        assertEquals(0L, Durations.parseTicks("0.sec"))
    }

    @Test
    fun `дробные секунды`() {
        assertEquals(10L, Durations.parseTicks("0.5.sec"))
        assertEquals(30L, Durations.parseTicks("1.5.sec"))
        assertEquals(2L, Durations.parseTicks("0.1.sec"))
    }

    @Test
    fun `дробная часть округляется а не отбрасывается`() {
        assertEquals(7L, Durations.parseTicks("0.35.sec"))
        assertEquals(6L, Durations.parseTicks("0.29.sec"))  // 5.8 тика
    }

    @Test
    fun `тики`() {
        assertEquals(20L, Durations.parseTicks("20.tick"))
        assertEquals(20L, Durations.parseTicks("20.ticks"))
        assertEquals(5L, Durations.parseTicks("5.t"))
    }

    @Test
    fun `миллисекунды`() {
        assertEquals(10L, Durations.parseTicks("500.ms"))
        assertEquals(2L, Durations.parseTicks("100.ms"))
        assertEquals(1L, Durations.parseTicks("10.ms"))   // минимум 1 тик
    }

    @Test
    fun `без единицы значит секунды`() {
        assertEquals(40L, Durations.parseTicks("2"))
        assertEquals(10L, Durations.parseTicks("0.5"))
    }

    @Test
    fun `регистр и пробелы не важны`() {
        assertEquals(20L, Durations.parseTicks("1.SEC"))
        assertEquals(20L, Durations.parseTicks("  1.sec  "))
    }

    @Test
    fun `неизвестная единица не угадывается`() {
        assertNull(Durations.parseTicks("1.min"))
        assertNull(Durations.parseTicks("5.minutes"))
    }

    @Test
    fun `мусор возвращает null`() {
        assertNull(Durations.parseTicks(""))
        assertNull(Durations.parseTicks("abc"))
        assertNull(Durations.parseTicks("-1.sec"))
        assertNull(Durations.parseTicks(".sec"))
        assertNull(Durations.parseTicks("1..sec"))
        assertNull(Durations.parseTicks("1.sec.sec"))
    }

    @Test
    fun `огромные значения ограничены сутками`() {
        assertEquals(Durations.MAX_TICKS, Durations.parseTicks("999999999.sec"))
        assertEquals(Durations.MAX_TICKS, Durations.parseTicks("99999999999999999999.tick"))
    }
}