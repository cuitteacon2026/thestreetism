package cuitteacon26.thestreetism.entity

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.graffiti.GraffitiRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import java.util.Optional
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

object ModEntities {
    val REGISTRY = DeferredRegister.createEntities(Thestreetism.ID)

    val GRAFFITI by REGISTRY.registerEntityType("graffiti", ::GraffitiEntity, MobCategory.MISC) { builder ->
        builder.noLootTable().sized(1.0f, 1.0f).clientTrackingRange(10).updateInterval(10)
    }
}

class GraffitiEntity(type: EntityType<out GraffitiEntity>, level: Level) : Entity(type, level) {
    constructor(
        level: Level,
        position: Vec3,
        attachedBlockPos: BlockPos,
        facing: Direction,
        definition: GraffitiRegistry.GraffitiDefinition,
        owner: UUID?,
    ) : this(ModEntities.GRAFFITI, level) {
        setTextureId(definition.id)
        setWidth(definition.width)
        setHeight(definition.height)
        setAttachedBlockPos(attachedBlockPos)
        setFacing(facing)
        setOwner(owner)
        setPos(position.x, position.y, position.z)
        refreshDimensions()
        updateBoundingBox()
    }

    var ownerUuid: UUID?
        get() = entityData.get(DATA_OWNER).orElse(null)
        private set(value) = setOwner(value)

    override fun defineSynchedData(entityData: SynchedEntityData.Builder) {
        entityData.define(DATA_TEXTURE_ID, GraffitiRegistry.DEFAULT.id.toString())
        entityData.define(DATA_WIDTH, 1.0f)
        entityData.define(DATA_HEIGHT, 1.0f)
        entityData.define(DATA_FACING, Direction.NORTH)
        entityData.define(DATA_ROTATION, 0.0f)
        entityData.define(DATA_OWNER, Optional.empty())
        entityData.define(DATA_ATTACHED_BLOCK_POS, BlockPos.ZERO)
    }

    override fun onSyncedDataUpdated(accessor: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(accessor)
        if (accessor == DATA_WIDTH || accessor == DATA_HEIGHT) {
            refreshDimensions()
            updateBoundingBox()
        }
        if (accessor == DATA_FACING || accessor == DATA_ROTATION || accessor == DATA_ATTACHED_BLOCK_POS) {
            updateBoundingBox()
        }
    }

    override fun getDimensions(pose: Pose): EntityDimensions = EntityDimensions.scalable(graffitiWidth(), graffitiHeight())

    override fun makeBoundingBox(position: Vec3): AABB = thinSurfaceBoundingBox(position)

    fun textureId(): Identifier {
        val key = textureKey()
        val localKey = key.removePrefix("local:")
        val parsed = Identifier.tryParse(localKey) ?: return GraffitiRegistry.DEFAULT.id
        return if (parsed.namespace == "minecraft" && !localKey.contains(':')) {
            Identifier.fromNamespaceAndPath(Thestreetism.ID, parsed.path)
        } else {
            parsed
        }
    }

    fun textureKey(): String = entityData.get(DATA_TEXTURE_ID)

    fun definition(): GraffitiRegistry.GraffitiDefinition = GraffitiRegistry.get(textureId())

    fun texture(): Identifier = definition().texture

    fun graffitiWidth(): Float = entityData.get(DATA_WIDTH)

    fun graffitiHeight(): Float = entityData.get(DATA_HEIGHT)

    fun facing(): Direction = entityData.get(DATA_FACING)

    fun graffitiRotation(): Float = entityData.get(DATA_ROTATION)

    fun attachedBlockPos(): BlockPos = entityData.get(DATA_ATTACHED_BLOCK_POS)

    fun setTextureId(id: Identifier) = entityData.set(DATA_TEXTURE_ID, id.toString())

    fun setTextureKey(key: String) = entityData.set(DATA_TEXTURE_ID, key)

    fun setWidth(width: Float) = entityData.set(DATA_WIDTH, width)

    fun setHeight(height: Float) = entityData.set(DATA_HEIGHT, height)

    fun setFacing(facing: Direction) = entityData.set(DATA_FACING, facing)

    fun setGraffitiRotation(rotation: Float) = entityData.set(DATA_ROTATION, rotation.coerceIn(0.0f, 360.0f))

    fun setOwner(owner: UUID?) = entityData.set(DATA_OWNER, Optional.ofNullable(owner))

    fun setAttachedBlockPos(pos: BlockPos) = entityData.set(DATA_ATTACHED_BLOCK_POS, pos)

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount % 20 == 0 && !hasSupport()) {
            discard()
        }
    }

    fun getCoveredBlocks(): List<BlockPos> {
        val width = ceil(graffitiWidth().toDouble()).coerceAtLeast(1.0)
        val height = ceil(graffitiHeight().toDouble()).coerceAtLeast(1.0)
        val halfWidth = width / 2.0
        val halfHeight = height / 2.0
        val pos = position()
        val attached = attachedBlockPos()

        fun range(center: Double, half: Double): IntRange {
            val min = floor(center - half + 1.0E-6).toInt()
            val max = floor(center + half - 1.0E-6).toInt()
            return min..max
        }

        val blocks = mutableListOf<BlockPos>()
        when (facing()) {
            Direction.NORTH, Direction.SOUTH -> {
                for (x in range(pos.x, halfWidth)) for (y in range(pos.y, halfHeight)) blocks += BlockPos(x, y, attached.z)
            }
            Direction.EAST, Direction.WEST -> {
                for (z in range(pos.z, halfWidth)) for (y in range(pos.y, halfHeight)) blocks += BlockPos(attached.x, y, z)
            }
            Direction.UP, Direction.DOWN -> {
                for (x in range(pos.x, halfWidth)) for (z in range(pos.z, halfHeight)) blocks += BlockPos(x, attached.y, z)
            }
        }
        return blocks
    }

    fun hasSupport(): Boolean = getCoveredBlocks().all { Block.canSupportCenter(level(), it, facing()) }

    override fun isPickable(): Boolean = true

    override fun isPushable(): Boolean = false

    override fun canCollideWith(entity: Entity): Boolean = false

    override fun hurtServer(level: ServerLevel, source: DamageSource, damage: Float): Boolean = false

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.putString("texture", textureKey())
        output.putFloat("width", graffitiWidth())
        output.putFloat("height", graffitiHeight())
        output.putFloat("rotation", graffitiRotation())
        output.store("facing", Direction.CODEC, facing())
        output.store("attachedBlockPos", BlockPos.CODEC, attachedBlockPos())
        ownerUuid?.let { output.putString("ownerUUID", it.toString()) }
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        val textureKey = input.getString("texture").orElse(GraffitiRegistry.DEFAULT.id.toString())
        setTextureKey(textureKey)
        val loadedDefinition = definition()
        setWidth(input.getFloatOr("width", loadedDefinition.width))
        setHeight(input.getFloatOr("height", loadedDefinition.height))
        setGraffitiRotation(input.getFloatOr("rotation", 0.0f))
        setFacing(input.read("facing", Direction.CODEC).orElse(Direction.NORTH))
        setAttachedBlockPos(input.read("attachedBlockPos", BlockPos.CODEC).orElse(BlockPos.containing(position())))
        setOwner(input.getString("ownerUUID").flatMap {
            try {
                Optional.of(UUID.fromString(it))
            } catch (_: IllegalArgumentException) {
                Optional.empty()
            }
        }.orElse(null))
        refreshDimensions()
        updateBoundingBox()
    }

    override fun getPickResult(): ItemStack = ItemStack.EMPTY

    private fun updateBoundingBox() {
        setBoundingBox(thinSurfaceBoundingBox(position()))
    }

    private fun thinSurfaceBoundingBox(position: Vec3): AABB {
        val width = graffitiWidth().toDouble()
        val height = graffitiHeight().toDouble()
        val depth = BOUNDING_BOX_DEPTH
        val center = position.add(
            facing().stepX * depth / 2.0,
            facing().stepY * depth / 2.0,
            facing().stepZ * depth / 2.0,
        )
        return when (facing()) {
            Direction.NORTH, Direction.SOUTH -> AABB.ofSize(center, width, height, depth)
            Direction.EAST, Direction.WEST -> AABB.ofSize(center, depth, height, width)
            Direction.UP, Direction.DOWN -> AABB.ofSize(center, width, depth, height)
        }
    }

    companion object {
        private const val BOUNDING_BOX_DEPTH = 0.01
        private val DATA_TEXTURE_ID: EntityDataAccessor<String> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.STRING)
        private val DATA_WIDTH: EntityDataAccessor<Float> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_HEIGHT: EntityDataAccessor<Float> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_FACING: EntityDataAccessor<Direction> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.DIRECTION)
        private val DATA_ROTATION: EntityDataAccessor<Float> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_OWNER: EntityDataAccessor<Optional<UUID>> = SynchedEntityData.defineId(GraffitiEntity::class.java, ModEntityDataSerializers.OPTIONAL_UUID)
        private val DATA_ATTACHED_BLOCK_POS: EntityDataAccessor<BlockPos> = SynchedEntityData.defineId(GraffitiEntity::class.java, EntityDataSerializers.BLOCK_POS)
    }
}
