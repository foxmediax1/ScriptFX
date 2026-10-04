package net.foxmediax.scriptfx.scriptengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptParserTest {

    @Test
    fun `простая команда с аргументами`() {
        val cmd = ScriptParser.parse("weather rain").single()
        assertEquals("weather", cmd.name)
        assertEquals(listOf("rain"), cmd.args)
        assertEquals(1, cmd.lineNumber)
    }

    @Test
    fun `аргумент в кавычках с пробелами`() {
        val cmd = ScriptParser.parse("print \"Привет, мир\"").single()
        assertEquals(listOf("Привет, мир"), cmd.args)
    }

    @Test
    fun `экранированная кавычка внутри текста`() {
        // В тексте скрипта: print "a \"b\" c"
        val cmd = ScriptParser.parse("print \"a \\\"b\\\" c\"").single()
        assertEquals(listOf("a \"b\" c"), cmd.args)
    }

    @Test
    fun `пустая строка в кавычках не теряется`() {
        val cmd = ScriptParser.parse("printNPC \"текст\" \"\" \"Имя\"").single()
        assertEquals(listOf("текст", "", "Имя"), cmd.args)
    }

    @Test
    fun `несколько пробелов и табуляция между аргументами`() {
        val cmd = ScriptParser.parse("tp_allplayers   0\t64  0   overworld").single()
        assertEquals(listOf("0", "64", "0", "overworld"), cmd.args)
    }

    @Test
    fun `отступ в начале строки игнорируется`() {
        val cmd = ScriptParser.parse("    stopscript").single()
        assertEquals("stopscript", cmd.name)
        assertTrue(cmd.args.isEmpty())
    }

    @Test
    fun `комментарии и пустые строки пропускаются а номера строк сохраняются`() {
        val text = "# заголовок\n\nprint a\n// ещё комментарий\nprint b"
        val commands = ScriptParser.parse(text)
        assertEquals(listOf("print", "print"), commands.map { it.name })
        assertEquals(listOf(3, 5), commands.map { it.lineNumber })
    }

    @Test
    fun `решётка внутри текста не комментарий`() {
        val cmd = ScriptParser.parse("print \"# не комментарий\"").single()
        assertEquals(listOf("# не комментарий"), cmd.args)
    }

    @Test
    fun `переводы строк Windows`() {
        val commands = ScriptParser.parse("print a\r\nprint b\r\n")
        assertEquals(2, commands.size)
        assertEquals("print", commands[0].name)
        assertEquals(listOf("a"), commands[0].args)
    }

    @Test
    fun `BOM в начале файла не портит первую команду`() {
        val cmd = ScriptParser.parse("\uFEFFprint hi").single()
        assertEquals("print", cmd.name)
    }

    @Test
    fun `незакрытая кавычка не ломает разбор`() {
        val cmd = ScriptParser.parse("print \"без конца").single()
        assertEquals(listOf("без конца"), cmd.args)
    }

    @Test
    fun `пустой текст даёт пустой список`() {
        assertTrue(ScriptParser.parse("").isEmpty())
        assertTrue(ScriptParser.parse("\n\n   \n").isEmpty())
    }
}