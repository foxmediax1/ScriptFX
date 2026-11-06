package net.foxmediax.scriptfx.core.states

import java.util.concurrent.ConcurrentHashMap

/**
 * Хранилище состояний (ключ → значение).
 * Значения могут быть Number, Boolean, String.
 */
class StateStorage {

    private val values = ConcurrentHashMap<String, Any>()

    fun get(key: String): Any? = values[key]

    fun getNumber(key: String, default: Double = 0.0): Double {
        return when (val v = values[key]) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return when (val v = values[key]) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> default
        }
    }

    fun getString(key: String, default: String = ""): String {
        return values[key]?.toString() ?: default
    }

    fun set(key: String, value: Any) {
        values[key] = value
    }

    fun add(key: String, amount: Double) {
        val current = getNumber(key)
        set(key, current + amount)
    }

    fun remove(key: String) {
        values.remove(key)
    }

    fun clear() {
        values.clear()
    }

    fun has(key: String): Boolean = values.containsKey(key)

    fun keys(): Set<String> = values.keys.toSet()

    fun asMap(): Map<String, Any> = values.toMap()

    fun load(data: Map<String, Any>) {
        values.clear()
        values.putAll(data)
    }
}