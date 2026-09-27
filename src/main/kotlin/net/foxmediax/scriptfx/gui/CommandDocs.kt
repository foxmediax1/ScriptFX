package net.foxmediax.scriptfx.gui

data class CommandDoc(val template: String, val description: String)

/** Список команд для панели подсказок в редакторе скриптов. */
object CommandDocs {
    val ALL: List<CommandDoc> = listOf(
        CommandDoc("print \"текст\"", "Отправляет текст в чат"),
        CommandDoc("printNPC \"текст\" \"имя\" \"цвет\"", "Отправляет текст в чат от имени NPC"),
        CommandDoc("ticktime \"1.sec\"", "Задержка перед следующей командой"),
        CommandDoc("stopscript", "Останавливает скрипт"),
        CommandDoc("continue_startscript \"имя_скрипта.sfxs\"", "Продолжает другим скриптом (только после stopscript)"),
        CommandDoc("startscript \"имя_скрипта\"", "Запускает другой скрипт"),
        CommandDoc("startCommand \"say hello\"", "Выполняет игровую команду"),
        CommandDoc("tp_allplayers 0 64 0 overworld", "Телепортирует всех игроков"),
        CommandDoc("checkpoint 100 64 200 overworld 5", "[Первая строка] Триггер по координатам"),
        CommandDoc("worldstartscript", "[Первая строка] Срабатывает при заходе игрока в мир"),
        CommandDoc("weather rain", "[Первая строка] Начало дождя/грозы (rain/thunder)"),
        CommandDoc("timecycles day", "[Первая строка] Наступление времени (day/night/число тиков)"),
        CommandDoc("trigger_globalplay player_death", "[Первая строка] Игрок умер"),
        CommandDoc("trigger_globalplay player_respawn", "[Первая строка] Игрок возродился"),
        CommandDoc("trigger_globalplay player_tp", "[Первая строка] Игрок телепортировался"),
        CommandDoc("trigger_globalplay player_eat", "[Первая строка] Игрок поел еду"),
        CommandDoc("trigger_globalplay player_click \"minecraft:diamond\"", "[Первая строка] Игрок использовал указанный предмет"),
        CommandDoc("trigger_globalplay ender_dragon_fight", "[Первая строка] В мире игрока появился/уже летает эндер-дракон"),
        CommandDoc("trigger_globalplay wither_boss_fight", "[Первая строка] В мире игрока заспавнился босс-иссушитель")
    )
}