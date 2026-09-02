package cuitteacon26.thestreetism.command

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.item.SprayCanItem
import cuitteacon26.thestreetism.remote.RemoteImageUrl
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent

object ThestreetismCommands {
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal(Thestreetism.ID)
                .then(sprayCanCommand())
                .then(spraySizeCommand())
        )
    }

    private fun spraySizeCommand() =
        Commands.literal("spraysize")
            .then(
                Commands.argument("length", FloatArgumentType.floatArg(0.1f))
                    .then(
                        Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                            .executes { context ->
                                setSpraySize(
                                    context.source.playerOrException,
                                    FloatArgumentType.getFloat(context, "length"),
                                    FloatArgumentType.getFloat(context, "width"),
                                )
                            }
                    )
            )

    private fun sprayCanCommand() =
        Commands.literal("spraycan")
            .then(
                Commands.literal("remote")
                    .then(
                        Commands.argument("url", StringArgumentType.greedyString())
                            .executes { context -> setRemoteSprayCan(context.source.playerOrException, StringArgumentType.getString(context, "url")) }
                    )
            )

    private fun setRemoteSprayCan(player: net.minecraft.server.level.ServerPlayer, value: String): Int {
        val stack = player.mainHandItem
        if (stack.item != ModItems.SPRAY_CAN) {
            player.sendSystemMessage(Component.translatable("thestreetism.spray.not_holding"))
            return 0
        }

        return when (val result = RemoteImageUrl.normalize(value)) {
            is RemoteImageUrl.Result.Invalid -> {
                player.sendSystemMessage(Component.translatable(result.translationKey))
                0
            }

            is RemoteImageUrl.Result.Valid -> {
                SprayCanItem.setRemoteGraffitiUrl(stack, result.url)
                player.sendSystemMessage(Component.translatable("thestreetism.spray.url.updated", result.url))
                1
            }
        }
    }

    private fun setSpraySize(player: net.minecraft.server.level.ServerPlayer, length: Float, width: Float): Int {
        val stack = player.mainHandItem
        if (stack.item != ModItems.SPRAY_CAN) {
            player.sendSystemMessage(Component.translatable("thestreetism.spray.not_holding"))
            return 0
        }

        SprayCanItem.setGraffitiSize(stack, length, width)
        player.sendSystemMessage(Component.translatable("thestreetism.spray.size.updated", length, width))
        return 1
    }
}
