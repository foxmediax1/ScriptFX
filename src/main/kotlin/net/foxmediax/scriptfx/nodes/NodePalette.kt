package net.foxmediax.scriptfx.nodes

data class NodeTemplate(
    val kind: NodeKind,
    val title: String,
    val params: Map<String, String> = emptyMap(),
    val category: String = "Общие"
) {
    /** Краткая строка скрипта для превью на ноде / в каталоге. */
    fun scriptPreview(): String = NodeScriptPreview.fromParams(kind, params)
}

object NodePalette {

    val all: List<NodeTemplate> = listOf(
        // —— Поток ——
        NodeTemplate(NodeKind.START, "Начало скрипта", mapOf("cmd" to "startscript"), "Поток"),
        NodeTemplate(NodeKind.END, "Конец скрипта", mapOf("cmd" to "stopscript"), "Поток"),
        NodeTemplate(NodeKind.WAIT, "Ожидание", mapOf("duration" to "1.sec"), "Поток"),
        NodeTemplate(NodeKind.SCRIPT, "Запустить скрипт", mapOf("script" to "other"), "Поток"),
        NodeTemplate(NodeKind.COMMAND, "Команда Minecraft", mapOf("cmd" to "startCommand", "args" to "say hello"), "Поток"),
        NodeTemplate(NodeKind.BRANCH, "Ветка if", mapOf("var" to "dialog_button", "op" to "==", "value" to "1"), "Поток"),

        // —— Чат ——
        NodeTemplate(NodeKind.COMMAND, "Сообщение в чат", mapOf("cmd" to "print", "args" to "Привет, мир!"), "Чат"),
        NodeTemplate(NodeKind.COMMAND, "Сообщение NPC", mapOf("cmd" to "printNPC", "args" to "Текст WHITE Имя WHITE"), "Чат"),

        // —— Мир ——
        NodeTemplate(NodeKind.COMMAND, "Погода", mapOf("cmd" to "weather", "args" to "clear"), "Мир"),
        NodeTemplate(NodeKind.COMMAND, "Время суток", mapOf("cmd" to "timecycles", "args" to "day"), "Мир"),
        NodeTemplate(NodeKind.COMMAND, "ТП всех игроков", mapOf("cmd" to "tp_allplayers", "args" to "0 64 0 overworld"), "Мир"),

        // —— States ——
        NodeTemplate(NodeKind.COMMAND, "State set", mapOf("cmd" to "state_set", "args" to "flag 1"), "States"),
        NodeTemplate(NodeKind.COMMAND, "State add", mapOf("cmd" to "state_add", "args" to "flag 1"), "States"),
        NodeTemplate(NodeKind.COMMAND, "State get", mapOf("cmd" to "state_get", "args" to "flag"), "States"),
        NodeTemplate(NodeKind.COMMAND, "State remove", mapOf("cmd" to "state_remove", "args" to "flag"), "States"),
        NodeTemplate(NodeKind.COMMAND, "State has", mapOf("cmd" to "state_has", "args" to "flag"), "States"),
        NodeTemplate(NodeKind.COMMAND, "Player state set", mapOf("cmd" to "player_state_set", "args" to "flag 1"), "States"),
        NodeTemplate(NodeKind.COMMAND, "Player state add", mapOf("cmd" to "player_state_add", "args" to "flag 1"), "States"),
        NodeTemplate(NodeKind.COMMAND, "Player state get", mapOf("cmd" to "player_state_get", "args" to "flag"), "States"),
        NodeTemplate(NodeKind.COMMAND, "Player state remove", mapOf("cmd" to "player_state_remove", "args" to "flag"), "States"),

        // —— NPC ——
        NodeTemplate(NodeKind.COMMAND, "NPC spawn", mapOf("cmd" to "npc_spawn", "args" to "\"id\" ~ ~ ~"), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC despawn", mapOf("cmd" to "npc_despawn", "args" to "\"id\""), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC walk", mapOf("cmd" to "npc_walk", "args" to "\"id\" ~ ~ ~ 0.25"), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC stop", mapOf("cmd" to "npc_stop", "args" to "\"id\""), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC look point", mapOf("cmd" to "see_npc_K", "args" to "\"id\" ~ ~ ~"), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC look angle", mapOf("cmd" to "see_npc_C", "args" to "\"id\" 0 0"), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC interact key", mapOf("cmd" to "npc_interact_key", "args" to "X"), "NPC"),
        NodeTemplate(NodeKind.COMMAND, "NPC dialog HUD", mapOf("cmd" to "npc_dialog_hud", "args" to "\"id\" :: dialog_window_text=\"Текст\""), "NPC"),

        // —— Particles ——
        NodeTemplate(NodeKind.COMMAND, "Particle play", mapOf("cmd" to "particle_play", "args" to "effect_id"), "Частицы"),
        NodeTemplate(NodeKind.COMMAND, "Particle stop", mapOf("cmd" to "particle_stop", "args" to "effect_id"), "Частицы"),
        NodeTemplate(NodeKind.COMMAND, "Particle stop all", mapOf("cmd" to "particle_stop_all"), "Частицы"),
        NodeTemplate(NodeKind.COMMAND, "Particle attach", mapOf("cmd" to "particle_attach", "args" to "effect_id npc_id"), "Частицы"),
        NodeTemplate(NodeKind.COMMAND, "Particle detach", mapOf("cmd" to "particle_detach", "args" to "npc_id"), "Частицы"),
        NodeTemplate(NodeKind.COMMAND, "AAA particle", mapOf("cmd" to "aaa_particle", "args" to "aaa_particles:LightningStrike ~ ~1 ~ 1"), "Частицы"),

        // —— Media ——
        NodeTemplate(NodeKind.COMMAND, "Sound play", mapOf("cmd" to "sound_play", "args" to "sound_id 1 1"), "Медиа"),
        NodeTemplate(NodeKind.COMMAND, "Sound stop", mapOf("cmd" to "sound_stop", "args" to "all"), "Медиа"),
        NodeTemplate(NodeKind.COMMAND, "Video play", mapOf("cmd" to "video_play", "args" to "video_id fullscreen"), "Медиа"),
        NodeTemplate(NodeKind.COMMAND, "Video stop", mapOf("cmd" to "video_stop", "args" to "all"), "Медиа"),

        // —— HUD ——
        NodeTemplate(NodeKind.COMMAND, "HUD on", mapOf("cmd" to "hud_on", "args" to "hud_id"), "HUD"),
        NodeTemplate(NodeKind.COMMAND, "HUD off", mapOf("cmd" to "hud_off", "args" to "hud_id"), "HUD"),
        NodeTemplate(NodeKind.COMMAND, "HUD off all", mapOf("cmd" to "hud_off_all"), "HUD"),

        // —— VFX / камера ——
        NodeTemplate(NodeKind.COMMAND, "VFX play", mapOf("cmd" to "vfx", "args" to "camera_shake 1.sec"), "VFX"),
        NodeTemplate(NodeKind.COMMAND, "VFX stop", mapOf("cmd" to "vfx_stop", "args" to "camera_shake"), "VFX"),
        NodeTemplate(NodeKind.COMMAND, "Cam shake", mapOf("cmd" to "cam_shake", "args" to "0.3 1.sec"), "VFX"),
        NodeTemplate(NodeKind.COMMAND, "Cam roll", mapOf("cmd" to "cam_roll", "args" to "2.sec"), "VFX"),
        NodeTemplate(NodeKind.COMMAND, "Cam letterbox", mapOf("cmd" to "cam_letterbox", "args" to "3.sec"), "VFX"),
        NodeTemplate(NodeKind.COMMAND, "Cam flash", mapOf("cmd" to "cam_flash", "args" to "0.5.sec"), "VFX"),

        // —— Катсцена (скрипт) ——
        NodeTemplate(NodeKind.CUTSCENE_START, "Катсцена: старт", mapOf("mode" to "player"), "Катсцена"),
        NodeTemplate(NodeKind.CUTSCENE_END, "Катсцена: конец", category = "Катсцена"),
        NodeTemplate(
            NodeKind.CUTSCENE_SET, "Камера: set",
            mapOf("x" to "0", "y" to "64", "z" to "0", "yaw" to "0", "pitch" to "0"), "Катсцена"
        ),
        NodeTemplate(
            NodeKind.CUTSCENE_MOVE, "Камера: move",
            mapOf("x" to "0", "y" to "64", "z" to "0", "yaw" to "0", "pitch" to "0", "duration" to "1.sec"),
            "Катсцена"
        ),

        // —— Квесты ——
        NodeTemplate(NodeKind.QUEST_START, "Квест: старт", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.QUEST_COMPLETE, "Квест: завершить", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.QUEST_FAIL, "Квест: провал", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.COMMAND, "Квест: прогресс", mapOf("cmd" to "quest_progress", "args" to "\"id\" \"objective\" 1"), "Квесты"),
        NodeTemplate(NodeKind.COMMAND, "Квест: статус", mapOf("cmd" to "quest_status", "args" to "\"id\""), "Квесты"),

        // —— Graph ——
        NodeTemplate(NodeKind.COMMAND, "Graph run", mapOf("cmd" to "graph_run", "args" to "graph_id"), "Граф"),
        NodeTemplate(NodeKind.COMMAND, "Graph stop", mapOf("cmd" to "graph_stop", "args" to "graph_id"), "Граф"),
        NodeTemplate(NodeKind.COMMAND, "Graph reload", mapOf("cmd" to "graph_reload"), "Граф"),

        // Cutscene
        NodeTemplate(NodeKind.COMMAND, "Cutscene start", mapOf("cmd" to "cutscene_start", "args" to "player"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Cutscene end", mapOf("cmd" to "cutscene_end", "args" to ""), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera set", mapOf("cmd" to "camera_set", "args" to "0 70 0 0 20"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera move", mapOf("cmd" to "camera_move", "args" to "10 72 10 -45 15 2.sec"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera FOV", mapOf("cmd" to "camera_fov", "args" to "40 1.sec"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera lookat", mapOf("cmd" to "camera_lookat", "args" to "0 64 0 1.sec"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera path start", mapOf("cmd" to "camera_path_start", "args" to ""), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera path point", mapOf("cmd" to "camera_path_point", "args" to "10 72 10 -45 15 1.sec"), "Cutscene"),
        NodeTemplate(NodeKind.COMMAND, "Camera path end", mapOf("cmd" to "camera_path_end", "args" to "wait"), "Cutscene"),

// Variables (если VarCommands уже подключён)
        NodeTemplate(NodeKind.COMMAND, "Var set", mapOf("cmd" to "var_set", "args" to "global flag 1"), "Variables"),
        NodeTemplate(NodeKind.COMMAND, "Var if", mapOf("cmd" to "var_if", "args" to "player score >= 10"), "Variables"),
        NodeTemplate(NodeKind.COMMAND, "Var add", mapOf("cmd" to "var_add", "args" to "player score 1"), "Variables"),

// Quest
        NodeTemplate(NodeKind.COMMAND, "Quest start", mapOf("cmd" to "quest_start", "args" to "\"tutorial\""), "Quests"),
        NodeTemplate(NodeKind.COMMAND, "Quest complete", mapOf("cmd" to "quest_complete", "args" to "\"tutorial\""), "Quests"),
        NodeTemplate(NodeKind.COMMAND, "Quest progress", mapOf("cmd" to "quest_progress", "args" to "\"tutorial\" \"obj_1\""), "Quests"),

// HUD
        NodeTemplate(NodeKind.COMMAND, "HUD on", mapOf("cmd" to "hud_on", "args" to "\"main\""), "HUD"),
        NodeTemplate(NodeKind.COMMAND, "HUD off", mapOf("cmd" to "hud_off", "args" to "\"main\""), "HUD"),
        NodeTemplate(NodeKind.COMMAND, "HUD set", mapOf("cmd" to "hud_set", "args" to "\"main\" \"title\" \"Текст\""), "HUD"),

        NodeTemplate(
            NodeKind.COMMAND,
            "Камера (файл катсцены)",
            mapOf("cmd" to "cutscene_play", "args" to "intro"),
            "Cutscene"
        ),
    )

    fun search(query: String): List<NodeTemplate> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.title.lowercase().contains(q) ||
                    it.kind.name.lowercase().contains(q) ||
                    it.category.lowercase().contains(q) ||
                    it.scriptPreview().lowercase().contains(q)
        }
    }

    fun spawn(template: NodeTemplate, id: String, x: Float, y: Float): GraphNode =
        GraphNode(
            id = id,
            kind = template.kind,
            title = template.title,
            params = template.params.toMutableMap(),
            x = x,
            y = y
        )
}

object NodeScriptPreview {
    fun fromParams(kind: NodeKind, params: Map<String, String>): String = when (kind) {
        NodeKind.START -> "startscript"
        NodeKind.END -> "stopscript"
        NodeKind.WAIT, NodeKind.DELAY -> "ticktime ${params["duration"] ?: "1.sec"}"
        NodeKind.SCRIPT -> "startscript ${params["script"].orEmpty()}"
        NodeKind.QUEST_START -> "quest_start ${params["quest"].orEmpty()}"
        NodeKind.QUEST_COMPLETE -> "quest_complete ${params["quest"].orEmpty()}"
        NodeKind.QUEST_FAIL -> "quest_fail ${params["quest"].orEmpty()}"
        NodeKind.CUTSCENE_START -> "cutscene_start ${params["mode"] ?: "player"}"
        NodeKind.CUTSCENE_END -> "cutscene_end"
        NodeKind.CUTSCENE_SET ->
            "camera_set ${params["x"]} ${params["y"]} ${params["z"]} ${params["yaw"]} ${params["pitch"]}"
        NodeKind.CUTSCENE_MOVE ->
            "camera_move ${params["x"]} ${params["y"]} ${params["z"]} ${params["yaw"]} ${params["pitch"]} ${params["duration"]}"
        NodeKind.BRANCH -> "if ${params["var"]} ${params["op"]} ${params["value"]}"
        NodeKind.COMMAND -> {
            val cmd = params["cmd"].orEmpty()
            val args = params["args"].orEmpty()
            if (args.isBlank()) cmd else "$cmd $args"
        }
    }.trim().take(42)

    fun fromNode(n: GraphNode): String = fromParams(n.kind, n.params)
}