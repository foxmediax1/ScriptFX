package net.foxmediax.scriptfx.gui

data class CommandDoc(val template: String, val description: String)

/** Список команд для панели подсказок в редакторе скриптов. */
object CommandDocs {
    val ALL: List<CommandDoc> = listOf(
        CommandDoc("print \"текст\"", "Отправляет текст в чат"),
        CommandDoc("printNPC [\"аватар.png\"] \"текст\" \"цвет имени\" \"имя\" \"цвет текста\" \"ремарка\"", "Текст от имени NPC (аватар и ремарка необязательны)"),
        CommandDoc("ticktime \"1.sec\"", "Задержка перед следующей командой"),
        CommandDoc("stopscript", "Останавливает скрипт"),
        CommandDoc("continue_startscript \"имя_скрипта.sfxs\"", "Продолжает другим скриптом (только после stopscript)"),
        CommandDoc("startscript \"имя_скрипта\"", "Запускает другой скрипт"),
        CommandDoc("startCommand \"say hello\"", "Выполняет игровую команду"),
        CommandDoc("tp_allplayers 0 64 0 overworld", "Телепортирует всех игроков"),
        CommandDoc("cameraINEffect \"1.sec\"", "Плавно затемняет экран до чёрного"),
        CommandDoc("cameraOUTEffect", "Плавно убирает чёрный экран"),
        CommandDoc("cameraBIGText \"текст\" \"3.sec\"", "Большой текст по центру экрана (+smalltext — вместе с мелким)"),
        CommandDoc("cameraSMALLText \"текст\" \"3.sec\"", "Маленький текст по центру экрана"),
        CommandDoc("weather rain", "Меняет погоду: rain / thunder / clear"),
        CommandDoc("timecycles day", "Меняет время суток: day / night / число тиков"),
        CommandDoc("checkpoint 100 64 200 overworld 5", "[Триггер] Игрок вошёл в зону по координатам"),
        CommandDoc("worldstartscript", "[Триггер] Игрок зашёл в мир"),
        CommandDoc("trigger_globalplay player_death", "[Триггер] Игрок умер"),
        CommandDoc("trigger_globalplay player_respawn", "[Триггер] Игрок возродился"),
        CommandDoc("trigger_globalplay player_tp", "[Триггер] Игрок телепортировался"),
        CommandDoc("trigger_globalplay player_eat", "[Триггер] Игрок поел еду"),
        CommandDoc("trigger_globalplay player_click \"minecraft:diamond\"", "[Триггер] Игрок использовал указанный предмет"),
        CommandDoc("trigger_globalplay ender_dragon_fight", "[Триггер] В мире игрока появился/уже летает эндер-дракон"),
        CommandDoc("trigger_globalplay wither_boss_fight", "[Триггер] В мире игрока заспавнился босс-иссушитель")
    )
}