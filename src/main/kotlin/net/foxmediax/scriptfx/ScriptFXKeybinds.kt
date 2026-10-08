package net.foxmediax.scriptfx

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object ScriptFXKeybinds {
    private val CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(ScriptFX.MOD_ID, "main")
    )

    val openControlPanel: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.scriptfx.open_control_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            CATEGORY
        )
    )

    /** Тестовый экран Modern UI (временная клавиша) */
    val openMuiTest: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(
            "key.scriptfx.open_mui_test",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET, // ]
            CATEGORY
        )
    )
}