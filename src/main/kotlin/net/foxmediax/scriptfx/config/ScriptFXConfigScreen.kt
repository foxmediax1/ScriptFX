package net.foxmediax.scriptfx.config

import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import net.foxmediax.scriptfx.client.mui.MuiScreens

object ScriptFXConfigScreen {

    fun build(parent: Screen): Screen = MuiScreens.createSettings(parent)

    private fun colorEntry(
        eb: ConfigEntryBuilder,
        title: String,
        tooltip: String,
        current: Int,
        default: Int,
        save: (Int) -> Unit
    ) = eb.startAlphaColorField(Component.literal(title), current)
        .setDefaultValue(default)
        .setTooltip(Component.literal(tooltip))
        .setSaveConsumer { save(it) }
        .build()

    fun buildCloth(parent: Screen): Screen {
        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Настройки ScriptFX"))
            .setSavingRunnable { ScriptFXConfig.save() }

        val entryBuilder = builder.entryBuilder()
        val general = builder.getOrCreateCategory(Component.literal("Общие"))

        general.addEntry(
            entryBuilder.startEnumSelector(
                Component.literal("Вид сообщений скриптов"),
                MessageDisplayMode::class.java,
                ScriptFXConfig.messageMode
            )
                .setDefaultValue(MessageDisplayMode.CENTER)
                .setEnumNameProvider { Component.literal((it as MessageDisplayMode).displayName) }
                .setTooltip(Component.literal(
                    "Как показывать сообщения скриптов и основной чат (клавиша T): " +
                            "в обычном чате (ванилла) или по центру экрана."
                ))
                .setSaveConsumer { value -> ScriptFXConfig.messageMode = value }
                .build()
        )

        general.addEntry(
            entryBuilder.startBooleanToggle(Component.literal("Показывать подсказки"), ScriptFXConfig.showHints)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Включает всплывающие подсказки в панели управления. (в разработке...)"))
                .setSaveConsumer { value -> ScriptFXConfig.showHints = value }
                .build()
        )

        general.addEntry(
            entryBuilder.startIntSlider(Component.literal("Макс. количество скриптов"), ScriptFXConfig.maxScripts, 1, 50)
                .setSaveConsumer { value -> ScriptFXConfig.maxScripts = value }
                .setTooltip(Component.literal("Показывает определённое количество скриптов, в разделе Проекты."))
                .build()
        )

        general.addEntry(
            entryBuilder.startStrField(Component.literal("Тема оформления"), ScriptFXConfig.theme)
                .setSaveConsumer { value -> ScriptFXConfig.theme = value }
                .setTooltip(Component.literal("Позволяет менять тему оформления панели уравления. (в разработке...)"))
                .build()
        )

        general.addEntry(
            entryBuilder.startBooleanToggle(Component.literal("Обводка NPC в диалоге"), ScriptFXConfig.npcOutline)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Подсвечивает белой обводкой NPC, с которым сейчас идёт диалог."))
                .setSaveConsumer { value -> ScriptFXConfig.npcOutline = value }
                .build()
        )

        val messages = builder.getOrCreateCategory(Component.literal("Сообщения"))

        messages.addEntry(colorEntry(
            entryBuilder, "Фон сообщения",
            "Цвет и прозрачность фона плашки сообщения (формат #AARRGGBB).",
            ScriptFXConfig.messageBg, ScriptFXConfig.DEFAULT_MESSAGE_BG
        ) { ScriptFXConfig.messageBg = it })

        messages.addEntry(colorEntry(
            entryBuilder, "Обводка сообщения",
            "Цвет и прозрачность обводки плашки сообщения.",
            ScriptFXConfig.messageBorder, ScriptFXConfig.DEFAULT_MESSAGE_BORDER
        ) { ScriptFXConfig.messageBorder = it })

        messages.addEntry(
            entryBuilder.startBooleanToggle(
                Component.literal("Системные сообщения по центру"),
                ScriptFXConfig.centerSystemMessages
            )
                .setDefaultValue(true)
                .setTooltip(Component.literal(
                    "Показывать плашкой по центру ответы команд, вход/выход игроков и прочие " +
                            "системные сообщения. Если выключено, они видны только в окне чата (T)."
                ))
                .setSaveConsumer { value -> ScriptFXConfig.centerSystemMessages = value }
                .build()
        )

        return builder.build()
    }
}