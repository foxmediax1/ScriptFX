package net.foxmediax.scriptfx.scriptengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptRunnerTest {

    private fun cmd(name: String) = ScriptCommand(name, emptyList(), 1)

    /** Раннер с подменой команд: executed хранит имена выполненных команд по порядку. */
    private class Harness(
        commands: List<ScriptCommand>,
        scripts: Map<String, List<ScriptCommand>> = emptyMap(),
        behave: (ScriptCommand) -> CommandResult = { CommandResult.Continue }
    ) {
        val executed = mutableListOf<String>()
        val runner = ScriptRunner(
            commands, "test",
            { command -> executed.add(command.name); behave(command) },
            { scripts[it] }
        )
    }

    @Test
    fun `все команды выполняются подряд за один тик`() {
        val h = Harness(listOf(cmd("a"), cmd("b"), cmd("c")))
        h.runner.tick(1)
        assertEquals(listOf("a", "b", "c"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `пустой скрипт завершается на первом тике`() {
        val h = Harness(emptyList())
        h.runner.tick(1)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `Wait приостанавливает скрипт на указанное число тиков`() {
        val h = Harness(listOf(cmd("a"), cmd("b")), behave = {
            if (it.name == "a") CommandResult.Wait(10) else CommandResult.Continue
        })
        h.runner.tick(100)
        assertEquals(listOf("a"), h.executed)

        h.runner.tick(109)
        assertEquals(listOf("a"), h.executed)

        h.runner.tick(110)
        assertEquals(listOf("a", "b"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `ожидание в последней команде держит скрипт запущенным до конца паузы`() {
        val h = Harness(listOf(cmd("a")), behave = { CommandResult.Wait(5) })
        h.runner.tick(1)
        assertFalse(h.runner.finished)

        h.runner.tick(5)
        assertFalse(h.runner.finished)

        h.runner.tick(6)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `Stop завершает скрипт и пропускает остальные команды`() {
        val h = Harness(listOf(cmd("a"), cmd("stopscript"), cmd("c")), behave = {
            if (it.name == "stopscript") CommandResult.Stop else CommandResult.Continue
        })
        h.runner.tick(1)
        assertEquals(listOf("a", "stopscript"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `исключение в команде не ломает скрипт`() {
        val h = Harness(listOf(cmd("a"), cmd("boom"), cmd("c")), behave = {
            if (it.name == "boom") throw IllegalStateException("тестовая ошибка")
            CommandResult.Continue
        })
        h.runner.tick(1)
        assertEquals(listOf("a", "boom", "c"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `остановка до первого тика`() {
        val h = Harness(listOf(cmd("a")))
        h.runner.stop("тест")
        h.runner.tick(1)
        assertTrue(h.executed.isEmpty())
        assertTrue(h.runner.finished)
    }

    @Test
    fun `остановка прямо во время выполнения команды`() {
        lateinit var h: Harness
        h = Harness(listOf(cmd("a"), cmd("b")), behave = {
            if (it.name == "a") h.runner.stop("тест")
            CommandResult.Continue
        })
        h.runner.tick(1)
        assertEquals(listOf("a"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `повторная остановка безвредна`() {
        val h = Harness(listOf(cmd("a")))
        h.runner.stop("тест")
        h.runner.stop("тест")
        assertTrue(h.runner.finished)
    }

    @Test
    fun `завершённый раннер больше ничего не выполняет`() {
        val h = Harness(listOf(cmd("a")))
        h.runner.tick(1)
        h.runner.tick(2)
        h.runner.tick(3)
        assertEquals(listOf("a"), h.executed)
    }

    @Test
    fun `ContinueWith переключает на другой скрипт со следующего тика`() {
        val h = Harness(
            commands = listOf(cmd("a"), cmd("go")),
            scripts = mapOf("second" to listOf(cmd("x"), cmd("y"))),
            behave = { if (it.name == "go") CommandResult.ContinueWith("second") else CommandResult.Continue }
        )
        h.runner.tick(1)
        assertEquals(listOf("a", "go"), h.executed)
        assertEquals("second", h.runner.name)
        assertFalse(h.runner.finished)

        h.runner.tick(2)
        assertEquals(listOf("a", "go", "x", "y"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `ContinueWith на несуществующий скрипт завершает работу`() {
        val h = Harness(listOf(cmd("go"), cmd("never")), behave = {
            CommandResult.ContinueWith("нет_такого")
        })
        h.runner.tick(1)
        assertEquals(listOf("go"), h.executed)
        assertTrue(h.runner.finished)
    }

    @Test
    fun `WaitUntil держит скрипт пока условие false`() {
        var ready = false
        val h = Harness(listOf(cmd("wait"), cmd("after")), behave = {
            if (it.name == "wait") CommandResult.WaitUntil { ready } else CommandResult.Continue
        })
        h.runner.tick(1)
        assertEquals(listOf("wait"), h.executed)
        assertFalse(h.runner.finished)

        h.runner.tick(2)
        assertEquals(listOf("wait"), h.executed)

        ready = true
        h.runner.tick(3)
        assertEquals(listOf("wait", "after"), h.executed)
        assertTrue(h.runner.finished)
    }
}