package net.foxmediax.scriptfx.scriptengine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TriggerParserTest {

    private fun parse(script: String, name: String = "test"): List<Trigger> =
        TriggerParser.parseAll(name, ScriptParser.parse(script))

    @Test
    fun `checkpoint с телом`() {
        val triggers = parse(
            """
            checkpoint 100 64 200 overworld 5
            print "Вы дошли до точки!"
            """.trimIndent()
        )
        val cp = assertIs<Trigger.Checkpoint>(triggers.single())
        assertEquals(100.0, cp.x)
        assertEquals(64.0, cp.y)
        assertEquals(200.0, cp.z)
        assertEquals("overworld", cp.dimension)
        assertEquals(5.0, cp.radius)
        assertEquals(listOf("print"), cp.body.map { it.name })
        assertEquals("test", cp.scriptName)
    }

    @Test
    fun `checkpoint с неверными аргументами пропускается`() {
        assertTrue(parse("checkpoint 1 2").isEmpty())
        assertTrue(parse("checkpoint a b c overworld 5").isEmpty())
        assertTrue(parse("checkpoint 1 2 3 nether").isEmpty())   // нет радиуса
    }

    @Test
    fun `тело заканчивается на следующем триггере`() {
        val triggers = parse(
            """
            trigger_globalplay player_death
            print "a"
            trigger_globalplay player_respawn
            print "b"
            print "c"
            """.trimIndent()
        )
        assertEquals(2, triggers.size)

        val death = assertIs<Trigger.GlobalPlay>(triggers[0])
        val respawn = assertIs<Trigger.GlobalPlay>(triggers[1])
        assertEquals(GlobalPlayEvent.PLAYER_DEATH, death.event)
        assertEquals(1, death.body.size)
        assertEquals(GlobalPlayEvent.PLAYER_RESPAWN, respawn.event)
        assertEquals(2, respawn.body.size)
    }

    @Test
    fun `команды до триггера в тело не попадают`() {
        val triggers = parse(
            """
            print "до триггера"
            worldstartscript
            print "после"
            """.trimIndent()
        )
        val start = assertIs<Trigger.WorldStart>(triggers.single())
        assertEquals(1, start.body.size)
        assertEquals(listOf("после"), start.body.single().args)
    }

    @Test
    fun `все типы trigger_globalplay распознаются`() {
        val expected = mapOf(
            "player_death" to GlobalPlayEvent.PLAYER_DEATH,
            "player_respawn" to GlobalPlayEvent.PLAYER_RESPAWN,
            "player_tp" to GlobalPlayEvent.PLAYER_TP,
            "player_eat" to GlobalPlayEvent.PLAYER_EAT,
            "ender_dragon_fight" to GlobalPlayEvent.ENDER_DRAGON_FIGHT,
            "wither_boss_fight" to GlobalPlayEvent.WITHER_BOSS_FIGHT
        )
        for ((word, event) in expected) {
            val gp = assertIs<Trigger.GlobalPlay>(parse("trigger_globalplay $word").single())
            assertEquals(event, gp.event, word)
        }
    }

    @Test
    fun `тип события не зависит от регистра`() {
        val gp = assertIs<Trigger.GlobalPlay>(parse("trigger_globalplay PLAYER_DEATH").single())
        assertEquals(GlobalPlayEvent.PLAYER_DEATH, gp.event)
    }

    @Test
    fun `неизвестный тип события пропускается`() {
        assertTrue(parse("trigger_globalplay player_fly").isEmpty())
        assertTrue(parse("trigger_globalplay").isEmpty())
    }

    @Test
    fun `player_click требует id предмета`() {
        assertTrue(parse("trigger_globalplay player_click").isEmpty())

        val gp = assertIs<Trigger.GlobalPlay>(
            parse("trigger_globalplay player_click \"minecraft:diamond\"").single()
        )
        assertEquals(GlobalPlayEvent.PLAYER_CLICK, gp.event)
        assertEquals("minecraft:diamond", gp.itemId)
    }

    @Test
    fun `у остальных событий id предмета пуст`() {
        val gp = assertIs<Trigger.GlobalPlay>(parse("trigger_globalplay player_death").single())
        assertNull(gp.itemId)
    }

    @Test
    fun `скрипт без триггеров даёт пустой список`() {
        assertTrue(parse("print a\nticktime 1.sec").isEmpty())
    }
}