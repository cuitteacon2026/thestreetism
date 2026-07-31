package cuitteacon26.thestreetism.entity

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.banner.BannerGeometry
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.color.RgbColor
import cuitteacon26.thestreetism.client.GraffitiTextures
import cuitteacon26.thestreetism.menu.BannerMenuProvider
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
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

    val BANNER by REGISTRY.registerEntityType("banner", ::BannerEntity, MobCategory.MISC) { builder ->
        builder.noLootTable().sized(1.0f, 1.0f).clientTrackingRange(10).updateInterval(10)
    }

    val SKATEBOARD by REGISTRY.registerEntityType("skateboard", ::SkateboardEntity, MobCategory.MISC) { builder ->
        builder.noLootTable().sized(0.9f, 0.26f).clientTrackingRange(10).updateInterval(1)
    }
}

class GraffitiEntity(type: EntityType<out GraffitiEntity>, level: Level) : Entity(type, level) {
    constructor(
        level: Level,
        position: Vec3,
        attachedBlockPos: BlockPos,
        facing: Direction,
        textureKey: String,
        width: Float,
        height: Float,
        owner: UUID?,
    ) : this(ModEntities.GRAFFITI, level) {
        setTextureKey(textureKey)
        setWidth(width)
        setHeight(height)
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
        entityData.define(DATA_TEXTURE_ID, "")
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

    fun textureKey(): String = entityData.get(DATA_TEXTURE_ID)

    fun graffitiWidth(): Float = entityData.get(DATA_WIDTH)

    fun graffitiHeight(): Float = entityData.get(DATA_HEIGHT)

    fun facing(): Direction = entityData.get(DATA_FACING)

    fun graffitiRotation(): Float = entityData.get(DATA_ROTATION)

    fun attachedBlockPos(): BlockPos = entityData.get(DATA_ATTACHED_BLOCK_POS)

    fun setTextureKey(key: String) = entityData.set(DATA_TEXTURE_ID, key)

    fun setWidth(width: Float) = entityData.set(DATA_WIDTH, sanitizeStoredGraffitiSize(width))

    fun setHeight(height: Float) = entityData.set(DATA_HEIGHT, sanitizeStoredGraffitiSize(height))

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
        val textureKey = input.getString("texture").orElse("")
        setTextureKey(textureKey)
        setWidth(input.getFloatOr("width", 1.0f))
        setHeight(input.getFloatOr("height", 1.0f))
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
        boundingBox = thinSurfaceBoundingBox(position())
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

        private fun sanitizeStoredGraffitiSize(size: Float): Float {
            return if (size.isFinite() && size > 0.0f) size else 1.0f
        }
    }
}

class BannerEntity(type: EntityType<out BannerEntity>, level: Level) : Entity(type, level) {
    constructor(
        level: Level,
        anchorA: BannerGeometry.Anchor,
        anchorB: BannerGeometry.Anchor,
        height: Float,
        backgroundColor: Int,
        textColor: Int,
        text: String,
        fontScale: Float,
        textAlignment: BannerTextAlignment,
        owner: UUID?,
    ) : this(ModEntities.BANNER, level) {
        setAnchorA(anchorA)
        setAnchorB(anchorB)
        setBannerHeight(height)
        setBackgroundColor(backgroundColor)
        setTextColor(textColor)
        setText(text)
        setFontScale(fontScale)
        setTextAlignment(textAlignment)
        setOwner(owner)
        val placement = placement()
        setPos(placement.center.x, placement.center.y, placement.center.z)
        refreshDimensions()
        updateBoundingBox()
    }

    var ownerUuid: UUID?
        get() = entityData.get(DATA_OWNER).orElse(null)
        private set(value) = setOwner(value)

    override fun defineSynchedData(entityData: SynchedEntityData.Builder) {
        entityData.define(DATA_ANCHOR_A, BlockPos.ZERO)
        entityData.define(DATA_ANCHOR_A_FACE, Direction.NORTH)
        entityData.define(DATA_ANCHOR_B, BlockPos.ZERO)
        entityData.define(DATA_ANCHOR_B_FACE, Direction.NORTH)
        entityData.define(DATA_HEIGHT, BannerGeometry.DEFAULT_HEIGHT)
        entityData.define(DATA_BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
        entityData.define(DATA_TEXT_COLOR, DEFAULT_TEXT_COLOR)
        entityData.define(DATA_TEXT, "")
        entityData.define(DATA_FONT_SCALE, DEFAULT_FONT_SCALE)
        entityData.define(DATA_TEXT_ALIGNMENT, BannerTextAlignment.CENTER.serializedName)
        entityData.define(DATA_OWNER, Optional.empty())
    }

    override fun onSyncedDataUpdated(accessor: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(accessor)
        if (accessor == DATA_ANCHOR_A || accessor == DATA_ANCHOR_A_FACE || accessor == DATA_ANCHOR_B || accessor == DATA_ANCHOR_B_FACE || accessor == DATA_HEIGHT) {
            val placement = placement()
            setPos(placement.center.x, placement.center.y, placement.center.z)
            refreshDimensions()
            updateBoundingBox()
        }
    }

    override fun getDimensions(pose: Pose): EntityDimensions {
        val placement = placement()
        return EntityDimensions.scalable(placement.length, placement.height)
    }

    override fun makeBoundingBox(position: Vec3): AABB = BannerGeometry.boundingBox(placement(), BannerGeometry.BOUNDING_BOX_DEPTH)

    fun anchorA(): BannerGeometry.Anchor = BannerGeometry.Anchor(entityData.get(DATA_ANCHOR_A), entityData.get(DATA_ANCHOR_A_FACE))

    fun anchorB(): BannerGeometry.Anchor = BannerGeometry.Anchor(entityData.get(DATA_ANCHOR_B), entityData.get(DATA_ANCHOR_B_FACE))

    fun bannerHeight(): Float = entityData.get(DATA_HEIGHT)

    fun backgroundColor(): Int = entityData.get(DATA_BACKGROUND_COLOR)

    fun textColor(): Int = entityData.get(DATA_TEXT_COLOR)

    fun text(): String = entityData.get(DATA_TEXT)

    fun fontScale(): Float = entityData.get(DATA_FONT_SCALE)

    fun textAlignment(): BannerTextAlignment = BannerTextAlignment.bySerializedName(entityData.get(DATA_TEXT_ALIGNMENT))

    fun textureKey(): String {
        val placement = placement()
        return GraffitiTextures.resolveBannerTexture(
            placement.length,
            placement.height,
            backgroundColor(),
            textColor(),
            text(),
            fontScale(),
            textAlignment(),
        )
    }

    fun placement(): BannerGeometry.Placement = BannerGeometry.create(anchorA(), anchorB(), bannerHeight())

    fun facing(): Direction = anchorA().face

    fun rotation(): Float = BannerGeometry.rotationDegrees(placement().horizontalDirection)

    fun supportFaces(): List<Pair<BlockPos, Direction>> {
        return listOf(
            anchorA().pos to anchorA().face,
            anchorB().pos to anchorB().face,
        )
    }

    fun setAnchorA(anchor: BannerGeometry.Anchor) {
        entityData.set(DATA_ANCHOR_A, anchor.pos)
        entityData.set(DATA_ANCHOR_A_FACE, anchor.face)
    }

    fun setAnchorB(anchor: BannerGeometry.Anchor) {
        entityData.set(DATA_ANCHOR_B, anchor.pos)
        entityData.set(DATA_ANCHOR_B_FACE, anchor.face)
    }

    fun setBannerHeight(height: Float) = entityData.set(DATA_HEIGHT, sanitizeBannerHeight(height))

    fun setBackgroundColor(color: Int) = entityData.set(DATA_BACKGROUND_COLOR, RgbColor.opaqueArgb(color))

    fun setTextColor(color: Int) = entityData.set(DATA_TEXT_COLOR, RgbColor.opaqueArgb(color))

    fun setText(text: String) = entityData.set(DATA_TEXT, text.take(MAX_TEXT_LENGTH))

    fun setFontScale(scale: Float) = entityData.set(DATA_FONT_SCALE, sanitizeFontScale(scale))

    fun setTextAlignment(alignment: BannerTextAlignment) = entityData.set(DATA_TEXT_ALIGNMENT, alignment.serializedName)

    fun setOwner(owner: UUID?) = entityData.set(DATA_OWNER, Optional.ofNullable(owner))

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount % 20 == 0 && !hasSupport()) {
            discard()
        }
    }

    fun hasSupport(): Boolean = supportFaces().all { (pos, face) -> Block.canSupportCenter(level(), pos, face) }

    override fun isPickable(): Boolean = true

    override fun isPushable(): Boolean = false

    override fun canCollideWith(entity: Entity): Boolean = false

    override fun hurtServer(level: ServerLevel, source: DamageSource, damage: Float): Boolean = false

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS
        }
        val serverPlayer = player as? net.minecraft.server.level.ServerPlayer ?: return InteractionResult.PASS
        serverPlayer.openMenu(BannerMenuProvider(this))
        return InteractionResult.CONSUME
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.store("anchorA", BlockPos.CODEC, anchorA().pos)
        output.store("anchorAFace", Direction.CODEC, anchorA().face)
        output.store("anchorB", BlockPos.CODEC, anchorB().pos)
        output.store("anchorBFace", Direction.CODEC, anchorB().face)
        output.putFloat("height", bannerHeight())
        output.putInt("backgroundColor", backgroundColor())
        output.putInt("textColor", textColor())
        output.putString("text", text())
        output.putFloat("fontScale", fontScale())
        output.putString("textAlignment", textAlignment().serializedName)
        ownerUuid?.let { output.putString("ownerUUID", it.toString()) }
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        val anchorAPos = input.read("anchorA", BlockPos.CODEC).orElse(BlockPos.ZERO)
        val anchorAFace = input.read("anchorAFace", Direction.CODEC).orElse(Direction.NORTH)
        val anchorBPos = input.read("anchorB", BlockPos.CODEC).orElse(anchorAPos)
        val anchorBFace = input.read("anchorBFace", Direction.CODEC).orElse(anchorAFace)
        setAnchorA(BannerGeometry.Anchor(anchorAPos, anchorAFace))
        setAnchorB(BannerGeometry.Anchor(anchorBPos, anchorBFace))
        setBannerHeight(input.getFloatOr("height", BannerGeometry.DEFAULT_HEIGHT))
        setBackgroundColor(input.getIntOr("backgroundColor", DEFAULT_BACKGROUND_COLOR))
        setTextColor(input.getIntOr("textColor", DEFAULT_TEXT_COLOR))
        setText(input.getString("text").orElse(""))
        setFontScale(input.getFloatOr("fontScale", DEFAULT_FONT_SCALE))
        setTextAlignment(BannerTextAlignment.bySerializedName(input.getString("textAlignment").orElse(BannerTextAlignment.CENTER.serializedName)))
        setOwner(input.getString("ownerUUID").flatMap {
            try {
                Optional.of(UUID.fromString(it))
            } catch (_: IllegalArgumentException) {
                Optional.empty()
            }
        }.orElse(null))
        val placement = placement()
        setPos(placement.center.x, placement.center.y, placement.center.z)
        refreshDimensions()
        updateBoundingBox()
    }

    override fun getPickResult(): ItemStack = ItemStack.EMPTY

    private fun updateBoundingBox() {
        boundingBox = BannerGeometry.boundingBox(placement(), BannerGeometry.BOUNDING_BOX_DEPTH)
    }

    companion object {
        const val DEFAULT_BACKGROUND_COLOR = 0xFFF5E0B5.toInt()
        const val DEFAULT_TEXT_COLOR = 0xFF111111.toInt()
        const val DEFAULT_FONT_SCALE = 1.0f
        const val MAX_TEXT_LENGTH = 256

        private val DATA_ANCHOR_A: EntityDataAccessor<BlockPos> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.BLOCK_POS)
        private val DATA_ANCHOR_A_FACE: EntityDataAccessor<Direction> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.DIRECTION)
        private val DATA_ANCHOR_B: EntityDataAccessor<BlockPos> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.BLOCK_POS)
        private val DATA_ANCHOR_B_FACE: EntityDataAccessor<Direction> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.DIRECTION)
        private val DATA_HEIGHT: EntityDataAccessor<Float> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_BACKGROUND_COLOR: EntityDataAccessor<Int> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.INT)
        private val DATA_TEXT_COLOR: EntityDataAccessor<Int> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.INT)
        private val DATA_TEXT: EntityDataAccessor<String> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.STRING)
        private val DATA_FONT_SCALE: EntityDataAccessor<Float> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_TEXT_ALIGNMENT: EntityDataAccessor<String> = SynchedEntityData.defineId(BannerEntity::class.java, EntityDataSerializers.STRING)
        private val DATA_OWNER: EntityDataAccessor<Optional<UUID>> = SynchedEntityData.defineId(BannerEntity::class.java, ModEntityDataSerializers.OPTIONAL_UUID)

        private fun sanitizeBannerHeight(height: Float): Float {
            return if (height.isFinite() && height > 0.25f) height else BannerGeometry.DEFAULT_HEIGHT
        }

        private fun sanitizeFontScale(scale: Float): Float {
            return if (scale.isFinite() && scale > 0.1f) scale.coerceAtMost(8.0f) else DEFAULT_FONT_SCALE
        }

    }
}
