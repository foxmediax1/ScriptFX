package net.foxmediax.scriptfx.gui

enum class DocCategory(val title: String) {
    BASIC("Базовые"),
    WORLD("Мир и игроки"),
    TRIGGERS("Триггеры"),
    SCREEN("Эффекты экрана"),
    CUTSCENE("Катсцена и камера"),
    NPC("NPC и диалоги")
}

data class CommandDoc(
    val template: String,
    val description: String,
    val category: DocCategory
)

/** Список команд для панели подсказок в редакторе скриптов. */
object CommandDocs {

    private fun doc(category: DocCategory, template: String, description: String) =
        CommandDoc(template, description, category)

    val ALL: List<CommandDoc> = listOf(
        // ---------- Базовые ----------
        doc(DocCategory.BASIC, "print \"текст\"", "Отправляет текст в чат (в режиме «По центру» — плашкой)"),
        doc(DocCategory.BASIC, "printNPC [\"аватар.png\"] \"текст\" \"цвет имени\" \"имя\" \"цвет текста\" \"ремарка\"",
            "Текст от имени NPC. Аватар и ремарка необязательны, порядок аргументов менять нельзя"),
        doc(DocCategory.BASIC, "ticktime \"1.sec\"", "Задержка: 1.sec, 0.5.sec, 20.tick, 500.ms"),
        doc(DocCategory.BASIC, "startCommand \"say hello\"", "Выполняет игровую команду (слэш не обязателен)"),
        doc(DocCategory.BASIC, "startscript \"имя_скрипта\"", "Запускает другой скрипт (учитывается лимит «Макс. количество скриптов»)"),
        doc(DocCategory.BASIC, "stopscript", "Останавливает скрипт"),
        doc(DocCategory.BASIC, "continue_startscript \"имя_скрипта.sfxs\"", "Продолжает другим скриптом (работает только после stopscript)"),

        // ---------- Мир и игроки ----------
        doc(DocCategory.WORLD, "tp_allplayers 0 64 0 overworld", "Телепортирует всех игроков: x y z, мир (overworld, nether, end)"),
        doc(DocCategory.WORLD, "weather rain", "Меняет погоду: rain / thunder / clear"),
        doc(DocCategory.WORLD, "timecycles day", "Меняет время суток: day / night / число тиков"),

        // ---------- Триггеры ----------
        doc(DocCategory.TRIGGERS, "checkpoint 100 64 200 overworld 5", "[Триггер] Игрок вошёл в зону: x y z, мир, радиус"),
        doc(DocCategory.TRIGGERS, "worldstartscript", "[Триггер] Игрок зашёл в мир"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay player_death", "[Триггер] Игрок умер"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay player_respawn", "[Триггер] Игрок возродился"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay player_tp", "[Триггер] Игрок телепортировался"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay player_eat", "[Триггер] Игрок поел еду"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay player_click \"minecraft:diamond\"", "[Триггер] Игрок использовал указанный предмет"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay ender_dragon_fight", "[Триггер] В мире игрока появился эндер-дракон"),
        doc(DocCategory.TRIGGERS, "trigger_globalplay wither_boss_fight", "[Триггер] В мире игрока заспавнился иссушитель"),

        // ---------- Эффекты экрана ----------
        doc(DocCategory.SCREEN, "cameraINEffect \"1.sec\"", "Плавно затемняет экран до чёрного (убирается cameraOUTEffect)"),
        doc(DocCategory.SCREEN, "cameraOUTEffect", "Плавно убирает чёрный экран"),
        doc(DocCategory.SCREEN, "cameraBIGText \"текст\" \"3.sec\"", "Большой текст по центру экрана (+smalltext — вместе с мелким)"),
        doc(DocCategory.SCREEN, "cameraSMALLText \"текст\" \"3.sec\"", "Маленький текст по центру экрана"),

        // ---------- Катсцена и камера ----------
        doc(DocCategory.CUTSCENE, "cutscene_start all", "Начало катсцены: all — всем, player — игроку скрипта"),
        doc(DocCategory.CUTSCENE, "cutscene_end", "Конец катсцены, игрок возвращается на место"),
        doc(DocCategory.CUTSCENE, "camera_set 100 70 200 0 10", "Мгновенно ставит камеру: x y z поворот наклон"),
        doc(DocCategory.CUTSCENE, "camera_move 110 72 210 45 5 \"2.sec\"", "Плавно двигает камеру, скрипт ждёт конца движения"),
        doc(DocCategory.CUTSCENE, "camera_path_start", "Начинает запись пути камеры"),
        doc(DocCategory.CUTSCENE, "camera_path_point 110 72 210 45 5 \"2.sec\"", "Точка пути: x y z поворот наклон время"),
        doc(DocCategory.CUTSCENE, "camera_path_end wait", "Запускает путь (wait — ждать его окончания)"),
        doc(DocCategory.CUTSCENE, "camera_lookat 100 65 200 \"1.sec\"", "Поворачивает камеру на точку (время необязательно)"),
        doc(DocCategory.CUTSCENE, "camera_lookat_entity \"Ник\" \"1.sec\"", "Поворачивает камеру на игрока (@s — игрок скрипта)"),
        doc(DocCategory.CUTSCENE, "camera_fov 50 \"1.sec\"", "Угол обзора 10–170 (время необязательно)"),
        doc(DocCategory.CUTSCENE, "player_lock true", "Блокирует движение и поворот игрока"),
        doc(DocCategory.CUTSCENE, "hud_hide true", "Скрывает интерфейс (хотбар, здоровье)"),
        doc(DocCategory.CUTSCENE, "letterbox true 0.12", "Чёрные полосы сверху и снизу (высота 0–0.4)"),

        // ---------- NPC и диалоги ----------
        doc(DocCategory.NPC, "npc_spawn \"id\" ~ ~ ~ ~180 0 \"idle\" \"dialog\"",
            "Создаёт NPC: x y z (можно ~), поворот и наклон (можно ~180), мир, анимация, режим. Всё после координат необязательно"),
        doc(DocCategory.NPC, "npc_despawn \"id\"", "Убирает NPC из мира"),
        doc(DocCategory.NPC, "npc_repack \"id\" N_look \"anim=idle\"",
            "Меняет NPC: флаги Y_/N_ (damage, look, gravity, nametag, visible, silent, collide), anim=, mode:, name=, hp, speed"),
        doc(DocCategory.NPC, "see_npc_K \"id\" 10 64 -20", "NPC смотрит в точку x y z (можно ~). Выключает N_look"),
        doc(DocCategory.NPC, "see_npc_C \"id\" 180", "NPC поворачивается по горизонтали на градус, необязательно [наклон]. Выключает N_look"),
        doc(DocCategory.NPC, "npc_interact_key \"X\"", "Скрипт ждёт, пока игрок нажмёт указанную клавишу"),
        doc(DocCategory.NPC, "npc_dialog_hud \"id\" :: dialog_window_text \"текст\" :: button1 = \"Да\" :: button2 = \"Нет\"",
            "Диалог с NPC (до 5 кнопок). Номер выбора попадает в dialog_button"),
        doc(DocCategory.NPC, "if dialog_button == 1 {", "Выполняет блок, если выбрана кнопка 1"),
        doc(DocCategory.NPC, "}", "Закрывает блок if (обязательна для каждого if)")
    )
}