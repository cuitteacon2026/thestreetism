package cuitteacon26.thestreetism.command

import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.graffiti.GraffitiRegistry
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.item.SprayCanItem
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.event.RegisterCommandsEvent

object ThestreetismCommands {
    fun register(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal(Thestreetism.ID)
                .then(sprayCanCommand("spraycan"))
                .then(spraySizeCommand())
                .then(Commands.literal("spraylist").executes { context -> listGraffiti(context.source.playerOrException) })
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

    private fun sprayCanCommand(name: String) =
        Commands.literal(name)
            .then(
                Commands.argument("name", StringArgumentType.string())
                    .executes { context -> setSprayCan(context.source.playerOrException, "local", StringArgumentType.getString(context, "name")) }
            )
            .then(
                Commands.literal("local")
                    .then(
                        Commands.argument("name", StringArgumentType.string())
                            .executes { context -> setSprayCan(context.source.playerOrException, "local", StringArgumentType.getString(context, "name")) }
                    )
            )
            .then(
                Commands.literal("remote")
                    .then(
                        Commands.argument("url", StringArgumentType.greedyString())
                            .executes { context -> setSprayCan(context.source.playerOrException, "remote", StringArgumentType.getString(context, "url")) }
                    )
            )

    private fun setSprayCan(player: net.minecraft.server.level.ServerPlayer, source: String, value: String): Int {
        val stack = player.mainHandItem
        if (stack.item != ModItems.SPRAY_CAN) {
            player.sendSystemMessage(Component.literal("请先把喷漆罐拿在主手。"))
            return 0
        }

        SprayCanItem.setGraffitiSelection(stack, source, value)
        player.sendSystemMessage(Component.literal("喷漆罐图案已设置为 $source:$value"))
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

    private fun listGraffiti(player: net.minecraft.server.level.ServerPlayer): Int {
        val names = GraffitiRegistry.BUILT_INS.joinToString(", ") { it.id.path }
        player.sendSystemMessage(Component.literal("可用 local 喷漆: $names"))
        return GraffitiRegistry.BUILT_INS.size
    }
}
