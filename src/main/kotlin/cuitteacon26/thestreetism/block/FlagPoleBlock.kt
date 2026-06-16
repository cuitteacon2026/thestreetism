package cuitteacon26.thestreetism.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class FlagPoleBlock(properties: Properties) : Block(properties), SimpleWaterloggedBlock {

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.WATERLOGGED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, BlockStateProperties.WATERLOGGED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val waterlogged = context.level.getFluidState(context.clickedPos).type == Fluids.WATER
        val fallback = context.horizontalDirection.opposite
        val facing = determineFacing(context.level, context.clickedPos, fallback)
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(BlockStateProperties.WATERLOGGED, waterlogged)
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

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        super.onPlace(state, level, pos, oldState, movedByPiston)
        if (oldState.block != this) {
            refreshOrientationCluster(level, pos)
        }
    }

    override fun affectNeighborsAfterRemoval(state: BlockState, level: ServerLevel, pos: BlockPos, movedByPiston: Boolean) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)
        refreshAdjacentOrientations(level, pos)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        when (state.getValue(FACING)) {
            Direction.NORTH, Direction.SOUTH -> NS_SHAPE
            Direction.EAST, Direction.WEST -> EW_SHAPE
            else -> NS_SHAPE
        }

    private fun refreshOrientationCluster(level: Level, pos: BlockPos) {
        refreshOrientation(level, pos)
        refreshAdjacentOrientations(level, pos)
    }

    private fun refreshAdjacentOrientations(level: Level, pos: BlockPos) {
        for (direction in Direction.Plane.HORIZONTAL) {
            refreshOrientation(level, pos.relative(direction))
        }
    }

    private fun refreshOrientation(level: Level, pos: BlockPos) {
        val state = level.getBlockState(pos)
        if (state.block !is FlagPoleBlock) return
        val currentFacing = state.getValue(FACING)
        val newFacing = determineFacing(level, pos, currentFacing)
        if (currentFacing != newFacing) {
            level.setBlock(pos, state.setValue(FACING, newFacing), 3)
        }
    }

    private fun determineFacing(level: BlockGetter, pos: BlockPos, fallback: Direction): Direction {
        val eastWest = isPole(level, pos.east()) || isPole(level, pos.west())
        val northSouth = isPole(level, pos.north()) || isPole(level, pos.south())
        return when {
            eastWest && !northSouth -> Direction.NORTH
            northSouth && !eastWest -> Direction.EAST
            eastWest && northSouth -> fallback
            else -> fallback
        }
    }

    private fun isPole(level: BlockGetter, pos: BlockPos): Boolean =
        level.getBlockState(pos).block is FlagPoleBlock

    companion object {
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        private val NS_SHAPE: VoxelShape = box(0.0, 7.0, 7.0, 16.0, 9.0, 9.0)
        private val EW_SHAPE: VoxelShape = box(7.0, 7.0, 0.0, 9.0, 9.0, 16.0)
    }
}
