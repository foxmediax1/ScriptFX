package net.foxmediax.scriptfx.client

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import java.util.Optional

/**
 * Идея: при ensure() файлы остаются на диске, а ResourceManager видит их как
 * assets/scriptfx/geckolib/models/<safe>.geo.json через кастомный PackResources.
 *
 * Регистрация на клиенте (ScriptFXClient):
 *   // после ClientModInitializer — через Fabric Resource Pack API или
 *   // RepositorySource в PackRepository
 *
 * Model id тогда:
 *   scriptfx:local_npc/<safe>  →  GeckoLib ищет geckolib/models/local_npc/<safe>.geo.json
 * Нужно согласовать path в Identifier с тем, как GeckoLib резолвит geo.
 */
object LocalNpcPackIds {
    fun modelPath(safeKey: String) = "geckolib/models/local_npc/$safeKey"
    fun animPath(safeKey: String) = "geckolib/animations/local_npc/$safeKey"
}