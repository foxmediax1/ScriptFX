package net.foxmediax.scriptfx.scriptengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptSchedulerTest {

    private val warnings = mutableListOf<String>()

    private fun cmd(name: String) = ScriptCommand(name, emptyList(), 1)

    /** Раннер, который "висит" (ждёт очень долго), пока его не остановят. */
    private fun hanging(name: String = "s") =
        ScriptRunner(listOf(cmd("wait")), name, { CommandResult.Wait(1_000_000) }, { null })

    /** Раннер, который завершается за первый тик. */
    private fun quick(name: String = "q") =
        ScriptRunner(listOf(cmd("x")), name, { CommandResult.Continue }, { null })

    private fun scheduler(limit: () -> Int) = ScriptScheduler(limit, warnings::add)

    @Test
    fun `запуски в пределах лимита принимаются`() {
        val s = scheduler { 2 }
        assertTrue(s.add("a") { hanging("a") })
        assertTrue(s.add("b") { hanging("b") })
        assertEquals(2, s.activeCount)
    }

    @Test
    fun `при достижении лимита запуск отклоняется и раннер не создаётся`() {
        val s = scheduler { 1 }
        assertTrue(s.add("a") { hanging("a") })

        var created = false
        assertFalse(s.add("b") { created = true; hanging("b") })

        assertFalse(created)
        assertEquals(1, s.activeCount)
    }

    @Test
    fun `завершённые скрипты освобождают место`() {
        val s = scheduler { 1 }
        assertTrue(s.add("q") { quick() })
        assertFalse(s.add("x") { hanging() })   // место занято до первого тика

        s.tick(1)                                // quick завершился и удалён
        assertEquals(0, s.activeCount)
        assertTrue(s.add("x") { hanging() })
    }

    @Test
    fun `предупреждение о лимите не чаще раза в 100 тиков`() {
        val s = scheduler { 1 }
        s.add("a") { hanging("a") }

        repeat(3) { s.add("b") { hanging("b") } }
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("'b'"))

        s.tick(50)
        s.add("b") { hanging("b") }
        assertEquals(1, warnings.size)

        s.tick(100)
        s.add("b") { hanging("b") }
        assertEquals(2, warnings.size)
    }

    @Test
    fun `лимит можно менять на лету`() {
        var limit = 2
        val s = scheduler { limit }
        s.add("a") { hanging("a") }
        s.add("b") { hanging("b") }

        limit = 1
        assertFalse(s.add("c") { hanging("c") })   // уже 2 >= 1

        s.stop("a", "тест")
        assertFalse(s.add("c") { hanging("c") })   // осталось 1 >= 1

        limit = 3
        assertTrue(s.add("c") { hanging("c") })
    }

    @Test
    fun `лимит меньше единицы считается единицей`() {
        val s = scheduler { 0 }
        assertTrue(s.add("a") { hanging("a") })
        assertFalse(s.add("b") { hanging("b") })
    }

    @Test
    fun `stop останавливает все экземпляры с этим именем`() {
        val s = scheduler { 10 }
        val a1 = hanging("a")
        val a2 = hanging("a")
        val b = hanging("b")
        s.add("a") { a1 }
        s.add("a") { a2 }
        s.add("b") { b }

        assertEquals(2, s.stop("a", "тест"))
        assertTrue(a1.finished)
        assertTrue(a2.finished)
        assertFalse(b.finished)
        assertEquals(1, s.activeCount)

        assertEquals(0, s.stop("нет_такого", "тест"))
    }

    @Test
    fun `stopAll останавливает всё и считает работающие`() {
        val s = scheduler { 10 }
        val a = hanging("a")
        val b = hanging("b")
        s.add("a") { a }
        s.add("b") { b }

        assertEquals(2, s.stopAll("тест"))
        assertTrue(a.finished)
        assertTrue(b.finished)
        assertEquals(0, s.activeCount)
    }

    @Test
    fun `stopAll на пустом списке возвращает ноль`() {
        assertEquals(0, scheduler { 10 }.stopAll("тест"))
    }
}