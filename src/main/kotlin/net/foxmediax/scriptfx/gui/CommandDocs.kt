package net.foxmediax.scriptfx.gui

enum class DocCategory(val title: String) {
    BASIC("Базовые"),
    WORLD("Мир и игроки"),
    TRIGGERS("Триггеры"),
    SCREEN("Эффекты экрана"),
    CUTSCENE("Катсцена и камера"),
    NPC("NPC и диалоги"),
    PARTICLES("Частицы"),
    MEDIA("Звук и видео"),
    QUESTS("Квесты")
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
        doc(DocCategory.NPC, "}", "Закрывает блок if (обязательна для каждого if)"),
        doc(DocCategory.NPC, "npc_walk \"id\" ~5 ~ ~10 0.35",
            "NPC идёт к точке (x y z, можно ~). Опционально скорость 0.05–1.5. Анимация running, по прибытии — idle"),
        doc(DocCategory.NPC, "npc_stop \"id\"",
            "Останавливает ходьбу NPC и ставит анимацию idle"),
        doc(DocCategory.NPC, "npc_hand \"id\" \"minecraft:iron_sword\"",
            "Предмет в главной руке NPC. Пустая строка \"\" — убрать предмет"),
        doc(DocCategory.NPC, "npc_drop \"id\" \"minecraft:bread\" 5",
            "NPC выбрасывает предмет рядом с собой. Количество 0…100 (0 — ничего не делает)"),

        // ---------- Частицы ----------
        doc(DocCategory.PARTICLES, "particle_play \"id\" ~ ~1 ~",
            "Запускает эффект частиц в точке. id из description.identifier. Координаты: число или ~ / ~N. Визуал — preview через ванильные частицы"),
        doc(DocCategory.PARTICLES, "particle_stop \"id\"",
            "Останавливает эмиттеры с этим id"),
        doc(DocCategory.PARTICLES, "particle_stop_all",
            "Останавливает все клиентские эмиттеры частиц"),
        doc(DocCategory.PARTICLES, "particle_attach \"effectId\" \"npcId\" 0 1 0",
            "Привязывает эффект к NPC. Смещение ox oy oz (по умолчанию 0 1 0)"),
        doc(DocCategory.PARTICLES, "particle_detach \"npcId\"",
            "Снимает все эффекты с NPC"),
        doc(DocCategory.PARTICLES, "particle_detach \"npcId\" \"effectId\"",
            "Снимает только указанный эффект с NPC"),

        // ---------- Звук и видео ----------
        doc(DocCategory.MEDIA, "sound_play \"id\" [volume] [pitch] [loop]",
            "OGG из projects/*/audio/<id>.ogg. volume/pitch по умолчанию 1. loop = true|loop|1"),
        doc(DocCategory.MEDIA, "sound_stop \"id\"",
            "Останавливает звук по id"),
        doc(DocCategory.MEDIA, "sound_stop all",
            "Останавливает все кастомные звуки ScriptFX"),
        doc(DocCategory.MEDIA, "video_play \"id\"",
            "Видео fullscreen: projects/*/video/<id>.mp4 (нужен ffmpeg в PATH или config ffmpegPath)"),
        doc(DocCategory.MEDIA, "video_play \"id\" 0 0 1 1",
            "Видео в области экрана: x y w h в долях 0..1"),
        doc(DocCategory.MEDIA, "video_stop \"id\"",
            "Останавливает видео"),
        doc(DocCategory.MEDIA, "video_stop all",
            "Останавливает все видео"),

        // ---------- Квесты ----------
        doc(DocCategory.QUESTS, "quest_start \"questId\"",
            "Выдаёт квест игроку"),
        doc(DocCategory.QUESTS, "quest_progress \"questId\" \"objectiveId\" [value]",
            "Обновляет прогресс цели квеста"),
        doc(DocCategory.QUESTS, "quest_complete \"questId\"",
            "Отмечает квест выполненным + тост"),
        doc(DocCategory.QUESTS, "quest_fail \"questId\"",
            "Провал квеста + тост"),
        doc(DocCategory.QUESTS, "quest_status \"questId\"",
            "Печатает статус квеста в лог/чат")
    )
}