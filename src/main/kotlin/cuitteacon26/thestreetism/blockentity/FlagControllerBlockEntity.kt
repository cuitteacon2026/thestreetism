package cuitteacon26.thestreetism.blockentity

import cuitteacon26.thestreetism.multiblock.FlagStructureValidator.Corners
import cuitteacon26.thestreetism.multiblock.FlagStructureValidator.Plane
import cuitteacon26.thestreetism.multiblock.FlagStructureValidator.SupportType
import cuitteacon26.thestreetism.serialization.FlagStyleData
import cuitteacon26.thestreetism.serialization.FlagTextSerialization
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.UUID
import kotlin.random.Random

class FlagControllerBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlockEntities.FLAG_CONTROLLER, pos, state) {

    var uuid: UUID = UUID.randomUUID()
        private set

    var flagWidth: Int = 1
        private set

    var flagHeight: Int = 1
        private set

    var plane: Plane = Plane.ZY
        private set

    var corners: Corners = Corners(pos, pos, pos, pos)
        private set

    var supportType: SupportType = SupportType.TYPE_B
        private set

    var seed: Long = Random.nextLong()
        private set

    var richTextJson: String = FlagTextSerialization.componentToJson(net.minecraft.network.chat.CommonComponents.EMPTY)
        private set

    var fontId: String = "default"
        private set

    var styleJson: String = FlagStyleData.DEFAULT.toJson()
        private set

    var customName: String = ""
        private set

    var createdAt: Long = System.currentTimeMillis()
        private set

    var version: Int = DATA_VERSION
        private set

    private val members = linkedSetOf<BlockPos>()
    private val supports = linkedSetOf<BlockPos>()

    fun configure(
        flagWidth: Int,
        flagHeight: Int,
        plane: Plane,
        corners: Corners,
        supportType: SupportType,
        memberPositions: Collection<BlockPos>,
        supportPositions: Collection<BlockPos>,
    ) {
        this.flagWidth = flagWidth
        this.flagHeight = flagHeight
        this.plane = plane
        this.corners = corners
        this.supportType = supportType
        this.members.clear()
        this.members.addAll(memberPositions)
        this.supports.clear()
        this.supports.addAll(supportPositions)
        this.version = DATA_VERSION
        setChanged()
    }

    fun updateTextData(richTextJson: String, fontId: String, styleJson: String, customName: String) {
        this.richTextJson = richTextJson.take(MAX_TEXT_LENGTH)
        this.fontId = fontId.take(MAX_FONT_ID_LENGTH).ifBlank { "default" }
        this.styleJson = styleJson.take(MAX_STYLE_LENGTH)
        this.customName = customName.take(MAX_NAME_LENGTH)
        this.version = DATA_VERSION
        setChanged()
    }

    fun memberPositions(): Set<BlockPos> = members.toSet()

    fun supportPositions(): Set<BlockPos> = supports.toSet()

    fun snapshot(): Snapshot = Snapshot(
        controllerPos = blockPos,
        width = flagWidth,
        height = flagHeight,
        plane = plane,
        supportType = supportType,
        richTextJson = richTextJson,
        fontId = fontId,
        styleJson = styleJson,
        customName = customName,
        seed = seed,
        createdAt = createdAt,
        version = version,
        members = members.toList(),
    )

    fun pushBlockEntityUpdate() {
        val serverLevel = level as? ServerLevel ?: return
        serverLevel.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putString("uuid", uuid.toString())
        output.putInt("flagWidth", flagWidth)
        output.putInt("flagHeight", flagHeight)
        output.putString("plane", plane.name)
        output.putString("supportType", supportType.name)
        output.putLong("seed", seed)
        output.putString("richTextJson", richTextJson)
        output.putString("fontId", fontId)
        output.putString("styleJson", styleJson)
        output.putString("customName", customName)
        output.putLong("createdAt", createdAt)
        output.putInt("version", version)
        output.store("corners", BlockPos.CODEC.listOf(), listOf(corners.topLeft, corners.topRight, corners.bottomLeft, corners.bottomRight))
        output.store("members", BlockPos.CODEC.listOf(), members.toList())
        output.store("supports", BlockPos.CODEC.listOf(), supports.toList())
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        uuid = input.getString("uuid").map { raw -> runCatching { UUID.fromString(raw) }.getOrElse { UUID.randomUUID() } }.orElseGet(UUID::randomUUID)
        flagWidth = input.getIntOr("flagWidth", 1)
        flagHeight = input.getIntOr("flagHeight", 1)
        plane = runCatching { Plane.valueOf(input.getString("plane").orElse(Plane.ZY.name)) }.getOrDefault(Plane.ZY)
        supportType = runCatching { SupportType.valueOf(input.getString("supportType").orElse(SupportType.TYPE_B.name)) }.getOrDefault(SupportType.TYPE_B)
        seed = input.getLongOr("seed", Random.nextLong())
        richTextJson = input.getString("richTextJson").orElse(FlagTextSerialization.componentToJson(net.minecraft.network.chat.CommonComponents.EMPTY))
        fontId = input.getString("fontId").orElse("default")
        styleJson = input.getString("styleJson").orElse(FlagStyleData.DEFAULT.toJson())
        customName = input.getString("customName").orElse("")
        createdAt = input.getLongOr("createdAt", System.currentTimeMillis())
        version = input.getIntOr("version", DATA_VERSION)

        val savedCorners = input.read("corners", BlockPos.CODEC.listOf()).orElse(emptyList())
        corners = if (savedCorners.size == 4) {
            Corners(savedCorners[0], savedCorners[1], savedCorners[2], savedCorners[3])
        } else {
            Corners(blockPos, blockPos, blockPos, blockPos)
        }

        members.clear()
        members.addAll(input.read("members", BlockPos.CODEC.listOf()).orElse(emptyList()))
        supports.clear()
        supports.addAll(input.read("supports", BlockPos.CODEC.listOf()).orElse(emptyList()))
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveCustomOnly(registries)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    data class Snapshot(
        val controllerPos: BlockPos,
        val width: Int,
        val height: Int,
        val plane: Plane,
        val supportType: SupportType,
        val richTextJson: String,
        val fontId: String,
        val styleJson: String,
        val customName: String,
        val seed: Long,
        val createdAt: Long,
        val version: Int,
        val members: List<BlockPos>,
    )

    companion object {
        private const val DATA_VERSION = 2
        const val MAX_TEXT_LENGTH = 32767
        const val MAX_STYLE_LENGTH = 8192
        const val MAX_FONT_ID_LENGTH = 256
        const val MAX_NAME_LENGTH = 128

        fun markClothMember(level: Level, clothPos: BlockPos, controllerPos: BlockPos) {
            (level as? ServerLevel)?.let { FlagSavedData.get(it).setMember(clothPos, controllerPos) }
        }

        fun clearClothMember(level: Level, clothPos: BlockPos) {
            (level as? ServerLevel)?.let { FlagSavedData.get(it).removeMember(clothPos) }
        }

        fun getControllerPos(level: Level, clothPos: BlockPos): BlockPos? =
            (level as? ServerLevel)?.let { FlagSavedData.get(it).getController(clothPos) }
    }
}
