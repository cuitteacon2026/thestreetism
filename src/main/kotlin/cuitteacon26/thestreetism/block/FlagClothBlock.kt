package cuitteacon26.thestreetism.block

import cuitteacon26.thestreetism.blockentity.FlagControllerBlockEntity
import cuitteacon26.thestreetism.multiblock.FlagMultiblockManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class FlagClothBlock(properties: Properties) : Block(properties), SimpleWaterloggedBlock, EntityBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false)
                .setValue(STITCHED, false)
                .setValue(CONTROLLER, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, BlockStateProperties.WATERLOGGED, STITCHED, CONTROLLER)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val waterlogged = context.level.getFluidState(context.clickedPos).type == Fluids.WATER
        return defaultBlockState()
            .setValue(FACING, context.horizontalDirection.opposite)
            .setValue(BlockStateProperties.WATERLOGGED, waterlogged)
            .setValue(STITCHED, false)
            .setValue(CONTROLLER, false)
    }

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        ticks: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource,
    ): BlockState = state

    override fun getFluidState(state: BlockState): FluidState =
        if (state.getValue(BlockStateProperties.WATERLOGGED)) Fluids.WATER.getSource(false)
        else super.getFluidState(state)

    override fun getRenderShape(state: BlockState): RenderShape =
        if (state.getValue(STITCHED)) RenderShape.INVISIBLE else RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.getValue(CONTROLLER)) FlagControllerBlockEntity(pos, state) else null

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        return openEditor(state, level, pos, player)
    }

    override fun useItemOn(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): InteractionResult {
        return openEditor(state, level, pos, player)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return when (state.getValue(FACING)) {
            Direction.NORTH, Direction.SOUTH -> NS_SHAPE
            else -> EW_SHAPE
        }
    }

    private fun openEditor(state: BlockState, level: Level, pos: BlockPos, player: Player): InteractionResult {
        if (!state.getValue(STITCHED)) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return InteractionResult.PASS
        val controller = FlagMultiblockManager.controllerFor(serverLevel, pos) ?: return InteractionResult.PASS
        (player as? ServerPlayer)?.let { FlagMultiblockManager.openEditor(it, controller) }
        return InteractionResult.SUCCESS
    }

    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val STITCHED: BooleanProperty = BooleanProperty.create("stitched")
        val CONTROLLER: BooleanProperty = BooleanProperty.create("controller")
        private val NS_SHAPE: VoxelShape = box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0)
        private val EW_SHAPE: VoxelShape = box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0)
    }
}
