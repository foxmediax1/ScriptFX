package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.core.states.StatesManager
import net.minecraft.server.level.ServerPlayer

object TriggerConditions {

    /**
     * Проверяет все условия триггера.
     * Сначала ищет значение у игрока, если нет — в глобальных states.
     */
    fun check(conditions: List<TriggerCondition>, player: ServerPlayer?): Boolean {
        if (conditions.isEmpty()) return true

        return conditions.all { cond ->
            val playerValue = player?.let { StatesManager.player(it).get(cond.key) }
            val globalValue = StatesManager.global().get(cond.key)
            val value = playerValue ?: globalValue

            when (cond.operator) {
                "has" -> value != null
                "!has" -> value == null
                "==" -> compare(value, cond.value) == 0
                "!=" -> compare(value, cond.value) != 0
                ">"  -> compare(value, cond.value) > 0
                "<"  -> compare(value, cond.value) < 0
                ">=" -> compare(value, cond.value) >= 0
                "<=" -> compare(value, cond.value) <= 0
                else -> {
                    ScriptFXLog.warn("Неизвестный оператор условия: ${cond.operator}")
                    false
                }
            }
        }
    }

    private fun compare(actual: Any?, expected: String?): Int {
        if (expected == null) return if (actual == null) 0 else 1
        if (actual == null) return -1

        val actualNum = when (actual) {
            is Number -> actual.toDouble()
            is Boolean -> if (actual) 1.0 else 0.0
            is String -> actual.toDoubleOrNull()
            else -> null
        }
        val expectedNum = expected.toDoubleOrNull()

        return if (actualNum != null && expectedNum != null) {
            actualNum.compareTo(expectedNum)
        } else {
            actual.toString().compareTo(expected)
        }
    }
}