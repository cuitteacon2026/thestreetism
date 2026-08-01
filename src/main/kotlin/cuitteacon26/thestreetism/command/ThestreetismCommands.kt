package cuitteacon26.thestreetism.command

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.item.SprayCanItem
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.net.URI

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
            player.sendSystemMessage(Component.literal("请先把喷漆罐拿在主手。"))
            return 0
        }

        val url = value.trim()
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            player.sendSystemMessage(Component.literal("喷漆图片必须使用有效的 HTTP/HTTPS URL。"))
            return 0
        }

        SprayCanItem.setRemoteGraffitiUrl(stack, url)
        player.sendSystemMessage(Component.literal("喷漆罐远程图片已更新。"))
        return 1
    }

    private fun setSpraySize(player: net.minecraft.server.level.ServerPlayer, length: Float, width: Float): Int {
        val stack = player.mainHandItem
        if (stack.item != ModItems.SPRAY_CAN) {
            player.sendSystemMessage(Component.literal("请先把喷漆罐拿在主手。"))
            return 0
        }

        SprayCanItem.setGraffitiSize(stack, length, width)
        player.sendSystemMessage(Component.literal("喷漆尺寸已设置为 ${length}x${width}"))
        return 1
    }
}
