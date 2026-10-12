package net.foxmediax.scriptfx.particles.molang

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Урезанный Molang для частиц (фаза 2).
 * Поддерживает: числа, + - * /, скобки, Math.*, variable.particle_random_1..4
 */
object SimpleMolang {

    data class Ctx(
        val random1: Float = Random.nextFloat(),
        val random2: Float = Random.nextFloat(),
        val random3: Float = Random.nextFloat(),
        val random4: Float = Random.nextFloat(),
        val particleAge: Float = 0f,
        val particleLifetime: Float = 1f
    ) {
        fun varValue(name: String): Float = when (name.lowercase()) {
            "particle_random_1", "variable.particle_random_1" -> random1
            "particle_random_2", "variable.particle_random_2" -> random2
            "particle_random_3", "variable.particle_random_3" -> random3
            "particle_random_4", "variable.particle_random_4" -> random4
            "particle_age", "variable.particle_age" -> particleAge
            "particle_lifetime", "variable.particle_lifetime" -> particleLifetime
            else -> 0f
        }
    }

    fun eval(expr: String?, def: Float, ctx: Ctx = Ctx()): Float {
        if (expr.isNullOrBlank()) return def
        val t = expr.trim()
        t.toFloatOrNull()?.let { return it }
        return runCatching { parseExpr(t, ctx) }.getOrDefault(def)
    }

    // ---- parser (рекурсивный спуск) ----

    private class P(val s: String, var i: Int = 0) {
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun peek(): Char? = if (i < s.length) s[i] else null
        fun next(): Char = s[i++]
        fun match(c: Char): Boolean {
            skip()
            if (peek() == c) { i++; return true }
            return false
        }
        fun ident(): String {
            skip()
            val start = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_' || s[i] == '.')) i++
            return s.substring(start, i)
        }
    }

    private fun parseExpr(src: String, ctx: Ctx): Float {
        val p = P(src)
        val v = parseAdd(p, ctx)
        p.skip()
        return v
    }

    private fun parseAdd(p: P, ctx: Ctx): Float {
        var v = parseMul(p, ctx)
        while (true) {
            p.skip()
            when (p.peek()) {
                '+' -> { p.next(); v += parseMul(p, ctx) }
                '-' -> { p.next(); v -= parseMul(p, ctx) }
                else -> return v
            }
        }
    }

    private fun parseMul(p: P, ctx: Ctx): Float {
        var v = parseUnary(p, ctx)
        while (true) {
            p.skip()
            when (p.peek()) {
                '*' -> { p.next(); v *= parseUnary(p, ctx) }
                '/' -> {
                    p.next()
                    val d = parseUnary(p, ctx)
                    v = if (d == 0f) 0f else v / d
                }
                else -> return v
            }
        }
    }

    private fun parseUnary(p: P, ctx: Ctx): Float {
        p.skip()
        if (p.match('+')) return parseUnary(p, ctx)
        if (p.match('-')) return -parseUnary(p, ctx)
        return parsePrimary(p, ctx)
    }

    private fun parsePrimary(p: P, ctx: Ctx): Float {
        p.skip()
        if (p.match('(')) {
            val v = parseAdd(p, ctx)
            p.match(')')
            return v
        }
        val c = p.peek()
        if (c != null && (c.isDigit() || c == '.')) {
            val start = p.i
            while (p.peek()?.let { it.isDigit() || it == '.' } == true) p.next()
            return p.s.substring(start, p.i).toFloatOrNull() ?: 0f
        }
        val id = p.ident()
        if (id.isEmpty()) return 0f

        // Math.xxx( / math.xxx(
        val lower = id.lowercase()
        if (lower.startsWith("math.") || lower.startsWith("math")) {
            val fn = lower.removePrefix("math.").removePrefix("math")
            // уже math.xxx в id
        }
        if (p.match('(')) {
            val args = mutableListOf<Float>()
            p.skip()
            if (p.peek() != ')') {
                args.add(parseAdd(p, ctx))
                while (p.match(',')) args.add(parseAdd(p, ctx))
            }
            p.match(')')
            return callFn(id, args, ctx)
        }

        // variable.xxx или просто имя
        return ctx.varValue(id)
    }

    private fun callFn(name: String, args: List<Float>, ctx: Ctx): Float {
        val n = name.lowercase().removePrefix("math.")
        return when (n) {
            "random" -> {
                val a = args.getOrElse(0) { 0f }
                val b = args.getOrElse(1) { 1f }
                a + Random.nextFloat() * (b - a)
            }
            "floor" -> floor(args.getOrElse(0) { 0f })
            "ceil" -> kotlin.math.ceil(args.getOrElse(0) { 0f })
            "abs" -> kotlin.math.abs(args.getOrElse(0) { 0f })
            "sin" -> sin(Math.toRadians(args.getOrElse(0) { 0f }.toDouble())).toFloat()
            "cos" -> cos(Math.toRadians(args.getOrElse(0) { 0f }.toDouble())).toFloat()
            "sqrt" -> sqrt(args.getOrElse(0) { 0f }.coerceAtLeast(0f))
            "min" -> minOf(args.getOrElse(0) { 0f }, args.getOrElse(1) { 0f })
            "max" -> maxOf(args.getOrElse(0) { 0f }, args.getOrElse(1) { 0f })
            "clamp" -> args.getOrElse(0) { 0f }
                .coerceIn(args.getOrElse(1) { 0f }, args.getOrElse(2) { 1f })
            "lerp" -> {
                val a = args.getOrElse(0) { 0f }
                val b = args.getOrElse(1) { 1f }
                val t = args.getOrElse(2) { 0.5f }
                a + (b - a) * t
            }
            else -> 0f
        }
    }
}