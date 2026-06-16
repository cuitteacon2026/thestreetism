package cuitteacon26.thestreetism.multiblock

import cuitteacon26.thestreetism.block.FlagClothBlock
import cuitteacon26.thestreetism.block.FlagPoleBlock
import cuitteacon26.thestreetism.blockentity.FlagControllerBlockEntity
import cuitteacon26.thestreetism.blockentity.FlagSavedData
import cuitteacon26.thestreetism.network.FlagEditorOpenPayload
import cuitteacon26.thestreetism.network.FlagSyncPayload
import cuitteacon26.thestreetism.serialization.FlagStyleData
import cuitteacon26.thestreetism.serialization.FlagTextSerialization
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.PacketDistributor

object FlagMultiblockManager {

    data class StitchResult(val success: Boolean, val errorKey: String? = null)

    fun tryStitch(level: Level, clickedPos: BlockPos, player: Player): StitchResult {
        if (level !is ServerLevel) return StitchResult(false)
        if (level.getBlockState(clickedPos).block !is FlagClothBlock) {
            return StitchResult(false, "thestreetism.flag.error.not_cloth")
        }

        val cloth = FlagStructureDetector.findConnectedCloth(level, clickedPos)
        if (cloth.isEmpty()) return StitchResult(false, "thestreetism.flag.error.empty")
        if (cloth.any { FlagSavedData.get(level).hasMember(it) }) {
            return StitchResult(false, "thestreetism.flag.error.already_stitched")
        }

        val box = FlagStructureDetector.boundingBox(cloth)
            ?: return StitchResult(false, "thestreetism.flag.error.empty")
        val validation = FlagStructureValidator.validate(level, cloth, box)
        if (!validation.valid) return StitchResult(false, validation.errorKey)

        val controllerPos = validation.corners!!.topLeft
        cloth.forEach { memberPos ->
            restoreClothState(level, memberPos, stitched = true, controller = memberPos == controllerPos)
        }
        val controllerState = level.getBlockState(controllerPos)
        val controller = FlagControllerBlockEntity(controllerPos, controllerState)
        controller.configure(
            flagWidth = validation.width,
            flagHeight = validation.height,
            plane = validation.plane!!,
            corners = validation.corners,
            supportType = validation.supportType!!,
            memberPositions = cloth,
            supportPositions = validation.supportPositions,
        )
        controller.updateTextData(
            richTextJson = FlagTextSerialization.componentToJson(net.minecraft.network.chat.CommonComponents.EMPTY),
            fontId = "default",
            styleJson = FlagStyleData.DEFAULT.toJson(),
            customName = "",
        )
        level.setBlockEntity(controller)
        FlagSavedData.get(level).setMembers(controllerPos, cloth)
        controller.pushBlockEntityUpdate()
        broadcastControllerText(level, controller)
        (player as? ServerPlayer)?.let { openEditor(it, controller) }
        return StitchResult(true)
    }

    fun controllerFor(level: ServerLevel, pos: BlockPos): FlagControllerBlockEntity? {
        val controllerPos = FlagSavedData.get(level).getController(pos)
            ?: if (level.getBlockEntity(pos) is FlagControllerBlockEntity) pos else return null
        return level.getBlockEntity(controllerPos) as? FlagControllerBlockEntity
    }

    fun openEditor(player: ServerPlayer, controller: FlagControllerBlockEntity) {
        PacketDistributor.sendToPlayer(player, FlagEditorOpenPayload.fromController(controller))
    }

    fun applyTextUpdate(
        level: ServerLevel,
        controllerPos: BlockPos,
        richTextJson: String,
        fontId: String,
        styleJson: String,
        customName: String,
    ) {
        val controller = level.getBlockEntity(controllerPos) as? FlagControllerBlockEntity ?: return
        controller.updateTextData(richTextJson, fontId, styleJson, customName)
        controller.pushBlockEntityUpdate()
        broadcastControllerText(level, controller)
    }

    fun handleBlockBroken(level: ServerLevel, pos: BlockPos, brokenState: net.minecraft.world.level.block.state.BlockState) {
        when (brokenState.block) {
            is FlagClothBlock -> controllerFor(level, pos)?.let { destroyMultiblock(level, it.blockPos) }
            is FlagPoleBlock -> adjacentControllers(level, pos).forEach { revalidateOrDestroy(level, it) }
        }
    }

    fun handleClothPlaced(level: ServerLevel, pos: BlockPos) {
        adjacentControllers(level, pos).forEach { revalidateOrDestroy(level, it) }
    }

    fun revalidateOrDestroy(level: ServerLevel, controllerPos: BlockPos) {
        val controller = level.getBlockEntity(controllerPos) as? FlagControllerBlockEntity ?: return
        val controllerState = level.getBlockState(controllerPos)
        if (controllerState.block !is FlagClothBlock || !controllerState.getValue(FlagClothBlock.CONTROLLER)) {
            destroyMultiblock(level, controllerPos)
            return
        }

        val detected = FlagStructureDetector.findConnectedCloth(level, controllerPos)
        val box = FlagStructureDetector.boundingBox(detected)
        val validation = box?.let { FlagStructureValidator.validate(level, detected, it) }

        if (
            detected.isEmpty() ||
            validation == null ||
            !validation.valid ||
            validation.corners?.topLeft != controllerPos
        ) {
            destroyMultiblock(level, controllerPos)
            return
        }

        controller.configure(
            flagWidth = validation.width,
            flagHeight = validation.height,
            plane = validation.plane!!,
            corners = validation.corners,
            supportType = validation.supportType!!,
            memberPositions = detected,
            supportPositions = validation.supportPositions,
        )
        FlagSavedData.get(level).removeController(controllerPos)
        FlagSavedData.get(level).setMembers(controllerPos, detected)
        controller.pushBlockEntityUpdate()
    }

    fun destroyMultiblock(level: ServerLevel, controllerPos: BlockPos) {
        val controller = level.getBlockEntity(controllerPos) as? FlagControllerBlockEntity
        val controllerMembers = controller?.memberPositions()
        val members = if (controllerMembers != null && controllerMembers.isNotEmpty()) {
            controllerMembers
        } else {
            FlagSavedData.get(level).membersFor(controllerPos)
        }
        members.forEach { memberPos ->
            restoreClothState(level, memberPos, stitched = false, controller = false)
            FlagControllerBlockEntity.clearClothMember(level, memberPos)
        }
        FlagSavedData.get(level).removeController(controllerPos)
        level.removeBlockEntity(controllerPos)
        val state = level.getBlockState(controllerPos)
        if (state.block is FlagClothBlock && state.getValue(FlagClothBlock.CONTROLLER)) {
            level.setBlock(controllerPos, state.setValue(FlagClothBlock.CONTROLLER, false), 3)
        } else {
            level.sendBlockUpdated(controllerPos, state, state, 3)
        }
    }

    private fun adjacentControllers(level: ServerLevel, pos: BlockPos): Set<BlockPos> {
        val found = linkedSetOf<BlockPos>()
        for (direction in net.minecraft.core.Direction.entries) {
            val controllerPos = FlagSavedData.get(level).getController(pos.relative(direction))
            if (controllerPos != null) {
                found.add(controllerPos)
            }
        }
        return found
    }

    private fun broadcastControllerText(level: ServerLevel, controller: FlagControllerBlockEntity) {
        val payload = FlagSyncPayload.fromController(controller)
        level.players().forEach { PacketDistributor.sendToPlayer(it, payload) }
    }

    private fun restoreClothState(level: ServerLevel, pos: BlockPos, stitched: Boolean, controller: Boolean) {
        val state = level.getBlockState(pos)
        if (state.block !is FlagClothBlock) return
        if (state.getValue(FlagClothBlock.STITCHED) == stitched && state.getValue(FlagClothBlock.CONTROLLER) == controller) return
        val updated = state
            .setValue(FlagClothBlock.STITCHED, stitched)
            .setValue(FlagClothBlock.CONTROLLER, controller)
        level.setBlock(pos, updated, 3)
    }
}
