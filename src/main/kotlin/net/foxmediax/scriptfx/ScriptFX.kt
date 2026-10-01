package net.foxmediax.scriptfx

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.foxmediax.scriptfx.network.CameraPayload
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object ScriptFX : ModInitializer {
	const val MOD_ID: String = "scriptfx"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		// Пакеты сервер -> клиент. Регистрируются на обеих сторонах.
		PayloadTypeRegistry.clientboundPlay().register(ScriptMessagePayload.TYPE, ScriptMessagePayload.CODEC)
		PayloadTypeRegistry.clientboundPlay().register(CameraPayload.TYPE, CameraPayload.CODEC)

		LOGGER.info("ScriptFX mod initialized!")
	}

	fun id(path: String): Identifier
			= Identifier.fromNamespaceAndPath(MOD_ID, path)
}