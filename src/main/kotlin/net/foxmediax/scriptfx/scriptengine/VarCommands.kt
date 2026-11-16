package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.core.states.StatesManager

/**
 * Единый API переменных (идеи как у Mappet):
 *   global  — StatesManager.global
 *   player  — StatesManager.player
 *   temp    — ScriptContext.vars (только текущий запуск скрипта)
 *   quest   — player state с префиксом quest.
 *
 * Команды:
 *   var_set    <scope> <key> <value>
 *   var_get    <scope> <key>          → печатает и кладёт в temp _last
 *   var_add    <scope> <key> <number>
 *   var_has    <scope> <key>          → temp _last = true/false
 *   var_remove <scope> <key>
 *   var_if     <scope> <key> <op> <value>   → SkipBlock если условие ложно
 *     op: == != > >= < <= contains
 */
object VarCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        register("var_set") { ctx, args ->
            if (args.size < 3) {
                ScriptFXLog.warn("var_set: var_set <scope> <key> <value>")
                return@register CommandResult.Continue
            }
            val scope = args[0]
            val key = args[1]
            val value = parseValue(args.drop(2).joinToString(" "))
            set(ctx, scope, key, value)
            ScriptFXLog.info("var_set $scope.$key = $value")
            CommandResult.Continue
        }

        register("var_get") { ctx, args ->
            if (args.size < 2) {
                ScriptFXLog.warn("var_get: var_get <scope> <key>")
                return@register CommandResult.Continue
            }
            val scope = args[0]
            val key = args[1]
            val v = get(ctx, scope, key)
            ctx.vars["_last"] = v?.toString() ?: ""
            ScriptMessenger.send(ctx, "var[$scope.$key] = $v")
            CommandResult.Continue
        }

        register("var_add") { ctx, args ->
            if (args.size < 3) {
                ScriptFXLog.warn("var_add: var_add <scope> <key> <number>")
                return@register CommandResult.Continue
            }
            val scope = args[0]
            val key = args[1]
            val amount = args[2].toDoubleOrNull() ?: 0.0
            val cur = toNumber(get(ctx, scope, key))
            set(ctx, scope, key, cur + amount)
            ScriptFXLog.info("var_add $scope.$key += $amount")
            CommandResult.Continue
        }

        register("var_has") { ctx, args ->
            if (args.size < 2) {
                ScriptFXLog.warn("var_has: var_has <scope> <key>")
                return@register CommandResult.Continue
            }
            val scope = args[0]
            val key = args[1]
            val ok = has(ctx, scope, key)
            ctx.vars["_last"] = ok.toString()
            ScriptMessenger.send(ctx, "var_has[$scope.$key] = $ok")
            CommandResult.Continue
        }

        register("var_remove") { ctx, args ->
            if (args.size < 2) {
                ScriptFXLog.warn("var_remove: var_remove <scope> <key>")
                return@register CommandResult.Continue
            }
            remove(ctx, args[0], args[1])
            ScriptFXLog.info("var_remove ${args[0]}.${args[1]}")
            CommandResult.Continue
        }

        // if без фигурных скобок парсера: var_if scope key op value  → SkipBlock
        register("var_if") { ctx, args ->
            if (args.size < 4) {
                ScriptFXLog.warn("var_if: var_if <scope> <key> <op> <value>")
                return@register CommandResult.Continue
            }
            val scope = args[0]
            val key = args[1]
            val op = args[2]
            val right = parseValue(args.drop(3).joinToString(" "))
            val left = get(ctx, scope, key)
            val ok = compare(left, op, right)
            ScriptFXLog.info("var_if $scope.$key $op $right → $ok (left=$left)")
            if (ok) CommandResult.Continue else CommandResult.SkipBlock
        }
    }

    private fun set(ctx: ScriptContext, scope: String, key: String, value: Any) {
        when (scope.lowercase()) {
            "global", "g" -> ctx.setState(key, value)
            "player", "p" -> ctx.setPlayerState(key, value)
            "temp", "t", "local" -> ctx.vars[key] = value
            "quest", "q" -> ctx.setPlayerState("quest.$key", value)
            else -> ScriptFXLog.warn("var: неизвестный scope '$scope' (global|player|temp|quest)")
        }
    }

    private fun get(ctx: ScriptContext, scope: String, key: String): Any? =
        when (scope.lowercase()) {
            "global", "g" -> ctx.getState(key)
            "player", "p" -> ctx.getPlayerState(key)
            "temp", "t", "local" -> ctx.vars[key]
            "quest", "q" -> ctx.getPlayerState("quest.$key")
            else -> null
        }

    private fun has(ctx: ScriptContext, scope: String, key: String): Boolean =
        when (scope.lowercase()) {
            "global", "g" -> ctx.hasState(key)
            "player", "p" -> ctx.getPlayerState(key) != null
            "temp", "t", "local" -> ctx.vars.containsKey(key)
            "quest", "q" -> ctx.getPlayerState("quest.$key") != null
            else -> false
        }

    private fun remove(ctx: ScriptContext, scope: String, key: String) {
        when (scope.lowercase()) {
            "global", "g" -> ctx.removeState(key)
            "player", "p" -> ctx.removePlayerState(key)
            "temp", "t", "local" -> ctx.vars.remove(key)
            "quest", "q" -> ctx.removePlayerState("quest.$key")
            else -> {}
        }
    }

    private fun parseValue(raw: String): Any {
        val v = raw.trim().removeSurrounding("\"")
        return when {
            v.equals("true", true) -> true
            v.equals("false", true) -> false
            v.toDoubleOrNull() != null -> v.toDouble()
            else -> v
        }
    }

    private fun toNumber(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        is Boolean -> if (v) 1.0 else 0.0
        else -> 0.0
    }

    private fun compare(left: Any?, op: String, right: Any): Boolean {
        val o = op.trim()
        if (o == "contains") {
            return left?.toString()?.contains(right.toString()) == true
        }
        val ln = toNumber(left)
        val rn = toNumber(right)
        val numeric = left is Number || right is Number ||
                (left is String && left.toDoubleOrNull() != null) ||
                (right is String && right.toString().toDoubleOrNull() != null)

        return when (o) {
            "==" -> if (numeric) ln == rn else left?.toString() == right.toString()
            "!=" -> if (numeric) ln != rn else left?.toString() != right.toString()
            ">" -> ln > rn
            ">=" -> ln >= rn
            "<" -> ln < rn
            "<=" -> ln <= rn
            else -> {
                ScriptFXLog.warn("var_if: неизвестный op '$op'")
                false
            }
        }
    }
}