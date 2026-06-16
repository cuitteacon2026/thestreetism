package cuitteacon26.thestreetism.blockentity

import cuitteacon26.thestreetism.Thestreetism
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType

/** World-level saved data tracking cloth block → controller block mapping. */
class FlagSavedData : SavedData() {

    private val memberToController = mutableMapOf<BlockPos, BlockPos>()

    fun setMember(clothPos: BlockPos, controllerPos: BlockPos) {
        memberToController[clothPos] = controllerPos
        setDirty()
    }

    fun setMembers(controllerPos: BlockPos, members: Collection<BlockPos>) {
        members.forEach { memberToController[it] = controllerPos }
        setDirty()
    }

    fun removeMember(clothPos: BlockPos) {
        memberToController.remove(clothPos)
        setDirty()
    }

    fun removeController(controllerPos: BlockPos) {
        if (memberToController.entries.removeIf { it.value == controllerPos }) {
            setDirty()
        }
    }

    fun membersFor(controllerPos: BlockPos): Set<BlockPos> =
        memberToController.entries
            .asSequence()
            .filter { it.value == controllerPos }
            .map { it.key }
            .toCollection(linkedSetOf())

    fun getController(clothPos: BlockPos): BlockPos? = memberToController[clothPos]

    fun hasMember(clothPos: BlockPos): Boolean = memberToController.containsKey(clothPos)

    companion object {
        private data class SavedEntry(val member: BlockPos, val controller: BlockPos)

        private val POS_CODEC: Codec<BlockPos> = Codec.LONG.xmap(BlockPos::of, BlockPos::asLong)
        private val ENTRY_CODEC: Codec<SavedEntry> = RecordCodecBuilder.create { instance ->
            instance.group(
                POS_CODEC.fieldOf("member").forGetter(SavedEntry::member),
                POS_CODEC.fieldOf("controller").forGetter(SavedEntry::controller)
            ).apply(instance, ::SavedEntry)
        }
        private val CODEC: Codec<FlagSavedData> = RecordCodecBuilder.create { instance ->
            instance.group(
                ENTRY_CODEC.listOf().fieldOf("entries").forGetter { data ->
                    data.memberToController.entries.map { SavedEntry(it.key, it.value) }
                }
            ).apply(instance) { entries ->
                FlagSavedData().apply {
                    entries.forEach { entry -> memberToController[entry.member] = entry.controller }
                }
            }
        }
        val TYPE: SavedDataType<FlagSavedData> = SavedDataType(
            Identifier.fromNamespaceAndPath(Thestreetism.ID, "flag_members"),
            { FlagSavedData() },
            CODEC
        )

        fun get(level: ServerLevel): FlagSavedData = level.dataStorage.computeIfAbsent(TYPE)
    }
}
