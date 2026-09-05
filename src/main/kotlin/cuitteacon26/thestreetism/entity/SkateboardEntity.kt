package cuitteacon26.thestreetism.entity

import cuitteacon26.thestreetism.item.ModItems
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.InterpolationHandler
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.VehicleEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SkateboardEntity(type: EntityType<out SkateboardEntity>, level: Level) : VehicleEntity(type, level) {
    private val interpolation = InterpolationHandler(this, 3)
    private var jumpWasDown = false
    private var pendingFoldPlayer: ServerPlayer? = null
    private var previousWheelRotation = 0.0f
    private var wheelRotation = 0.0f
    private var previousLean = 0.0f
    private var lean = 0.0f
    private var previousPitch = 0.0f
    private var pitch = 0.0f

    init {
        blocksBuilding = true
    }

    override fun tick() {
        super.tick()

        val positionBeforeInterpolation = position()
        val yawBeforeInterpolation = yRot
        interpolation.interpolate()
        tickDamageAnimation()

        if (level().isClientSide) {
            tickVisualAnimation(position().subtract(positionBeforeInterpolation), Mth.wrapDegrees(yRot - yawBeforeInterpolation))
            return
        }

        pendingFoldPlayer?.let { player ->
            pendingFoldPlayer = null
            foldIntoItem(player)
            return
        }

        tickMovement(readControls())
    }

    private fun tickDamageAnimation() {
        if (hurtTime > 0) hurtTime -= 1
        if (damage > 0.0f) damage -= 1.0f
    }

    private fun readControls(): Controls {
        val rider = controllingPassenger as? ServerPlayer ?: return Controls.NONE
        val input = rider.lastClientInput
        return Controls(
            left = input.left(),
            right = input.right(),
            forward = input.forward(),
            backward = input.backward(),
            jump = input.jump(),
        )
    }

    private fun tickMovement(controls: Controls) {
        val grounded = onGround()
        val movement = deltaMovement
        var horizontalX = movement.x
        var horizontalZ = movement.z
        var verticalSpeed = if (isNoGravity) movement.y else movement.y - gravity

        if (grounded) {
            var forward = forwardVector()
            var signedSpeed = horizontalX * forward.x + horizontalZ * forward.z
            signedSpeed = when {
                controls.forward && !controls.backward -> {
                    approachSpeed(
                        signedSpeed,
                        MAX_FORWARD_SPEED,
                        if (signedSpeed < 0.0) DIRECTION_CHANGE_ACCELERATION else FORWARD_ACCELERATION,
                    )
                }
                controls.backward && !controls.forward -> {
                    approachSpeed(
                        signedSpeed,
                        -REVERSE_SPEED,
                        if (signedSpeed > 0.0) DIRECTION_CHANGE_ACCELERATION else REVERSE_ACCELERATION,
                    )
                }
                else -> signedSpeed * GROUND_DRAG
            }

            if (abs(signedSpeed) < STOP_SPEED) signedSpeed = 0.0

            val turnInput = (if (controls.right) 1 else 0) - (if (controls.left) 1 else 0)
            if (turnInput != 0 && abs(signedSpeed) > MIN_STEERING_SPEED) {
                val speedRatio = (abs(signedSpeed) / MAX_FORWARD_SPEED).coerceIn(0.0, 1.0)
                val direction = if (signedSpeed < 0.0) -1.0 else 1.0
                val turnDegrees = turnInput * direction *
                    (MIN_TURN_DEGREES + (MAX_TURN_DEGREES - MIN_TURN_DEGREES) * speedRatio)
                yRot = Mth.wrapDegrees(yRot + turnDegrees.toFloat())
                forward = forwardVector()
            }

            horizontalX = forward.x * signedSpeed
            horizontalZ = forward.z * signedSpeed

            if (controls.jump && !jumpWasDown) {
                verticalSpeed = JUMP_VELOCITY
            }
        } else {
            horizontalX *= AIR_DRAG
            horizontalZ *= AIR_DRAG
        }
        jumpWasDown = controls.jump

        setDeltaMovement(horizontalX, verticalSpeed, horizontalZ)
        move(MoverType.SELF, deltaMovement)

        val resolvedMovement = deltaMovement
        horizontalX = resolvedMovement.x
        horizontalZ = resolvedMovement.z
        if (hypot(horizontalX, horizontalZ) < STOP_SPEED) {
            horizontalX = 0.0
            horizontalZ = 0.0
        }
        val resolvedVerticalSpeed = if (onGround() && resolvedMovement.y < 0.0) 0.0 else resolvedMovement.y
        setDeltaMovement(horizontalX, resolvedVerticalSpeed, horizontalZ)
    }

    private fun approachSpeed(current: Double, target: Double, amount: Double): Double = when {
        current < target -> min(current + amount, target)
        current > target -> max(current - amount, target)
        else -> target
    }

    private fun tickVisualAnimation(displacement: Vec3, yawDelta: Float) {
        previousWheelRotation = wheelRotation
        previousLean = lean
        previousPitch = pitch

        val distance = hypot(displacement.x, displacement.z)
        if (distance > 1.0E-5) {
            val forward = forwardVector()
            val direction = if (displacement.x * forward.x + displacement.z * forward.z < 0.0) -1.0 else 1.0
            val degrees = Math.toDegrees(distance / WHEEL_RADIUS) * direction
            wheelRotation += degrees.toFloat()
            if (abs(wheelRotation) > MAX_WHEEL_ROTATION) {
                val completedTurns = (wheelRotation / 360.0f).toInt() * 360.0f
                wheelRotation -= completedTurns
                previousWheelRotation -= completedTurns
            }
        }

        val targetLean = if (onGround()) {
            Mth.clamp(yawDelta * LEAN_PER_TURN_DEGREE, -MAX_LEAN_DEGREES, MAX_LEAN_DEGREES)
        } else {
            0.0f
        }
        lean += (targetLean - lean) * LEAN_SMOOTHING

        val targetPitch = if (onGround()) {
            0.0f
        } else {
            Mth.clamp((-deltaMovement.y * 35.0).toFloat(), -18.0f, 24.0f)
        }
        pitch += (targetPitch - pitch) * PITCH_SMOOTHING
    }

    fun getWheelRotation(partialTicks: Float): Float = Mth.lerp(partialTicks, previousWheelRotation, wheelRotation)

    fun getLean(partialTicks: Float): Float = Mth.lerp(partialTicks, previousLean, lean)

    fun getBoardPitch(partialTicks: Float): Float = Mth.lerp(partialTicks, previousPitch, pitch)

    private fun forwardVector(): Vec3 {
        val radians = Math.toRadians(yRot.toDouble())
        return Vec3(-sin(radians), 0.0, cos(radians))
    }

    override fun interact(player: Player, hand: InteractionHand, location: Vec3): InteractionResult {
        val parentResult = super.interact(player, hand, location)
        if (parentResult != InteractionResult.PASS) return parentResult
        if (player.isSecondaryUseActive) return InteractionResult.PASS
        if (!level().isClientSide && !player.startRiding(this)) return InteractionResult.PASS
        return InteractionResult.SUCCESS
    }

    override fun getControllingPassenger(): LivingEntity? = firstPassenger as? LivingEntity

    override fun canAddPassenger(passenger: Entity): Boolean = passengers.isEmpty() && passenger is Player

    override fun removePassenger(passenger: Entity) {
        val foldingPlayer = (passenger as? ServerPlayer)?.takeIf {
            !level().isClientSide && !isRemoved && it.isShiftKeyDown
        }
        super.removePassenger(passenger)
        if (!isRemoved && foldingPlayer != null) {
            pendingFoldPlayer = foldingPlayer
        }
    }

    override fun getPassengerAttachmentPoint(passenger: Entity, dimensions: EntityDimensions, scale: Float): Vec3 =
        Vec3(0.0, dimensions.height().toDouble() + RIDER_HEIGHT_OFFSET, 0.0)

    override fun shouldRiderSit(): Boolean = false

    override fun isPickable(): Boolean = !isRemoved

    override fun isPushable(): Boolean = true

    override fun canBeCollidedWith(other: Entity?): Boolean = true

    override fun maxUpStep(): Float = MAX_STEP_HEIGHT

    override fun getInterpolation(): InterpolationHandler = interpolation

    override fun isClientAuthoritative(): Boolean = false

    override fun isLocalClientAuthoritative(): Boolean = false

    override fun getDefaultGravity(): Double = GRAVITY

    override fun getDropItem(): Item = ModItems.SKATEBOARD

    override fun destroy(level: ServerLevel, source: DamageSource) {
        kill(level)
        if (level.gameRules.get(GameRules.ENTITY_DROPS)) {
            spawnAtLocation(level, createItemStack())
        }
    }

    override fun getPickResult(): ItemStack = createItemStack()

    override fun addAdditionalSaveData(output: ValueOutput) = Unit

    override fun readAdditionalSaveData(input: ValueInput) = Unit

    private fun foldIntoItem(player: ServerPlayer) {
        if (isRemoved) return

        if (!player.abilities.instabuild) {
            val stack = createItemStack()
            val inventory = player.inventory
            val slot = inventory.freeSlot
            if (slot >= 0 && inventory.add(slot, stack)) {
                player.connection.send(inventory.createInventoryUpdatePacket(slot))
            } else if (!stack.isEmpty) {
                spawnAtLocation(player.level(), stack)
            }
        }

        level().playSound(null, x, y, z, SoundEvents.ITEM_PICKUP, player.soundSource, 0.35f, 1.4f)
        discard()
    }

    private fun createItemStack(): ItemStack = ItemStack(ModItems.SKATEBOARD).also { stack ->
        stack.copyFrom(DataComponents.CUSTOM_NAME, this)
        stack.copyFrom(DataComponents.CUSTOM_DATA, this)
    }

    private data class Controls(
        val left: Boolean,
        val right: Boolean,
        val forward: Boolean,
        val backward: Boolean,
        val jump: Boolean,
    ) {
        companion object {
            val NONE = Controls(left = false, right = false, forward = false, backward = false, jump = false)
        }
    }

    companion object {
        private const val FORWARD_ACCELERATION = 0.035
        private const val REVERSE_ACCELERATION = 0.055
        private const val DIRECTION_CHANGE_ACCELERATION = 0.10
        private const val MAX_FORWARD_SPEED = 0.62
        private const val REVERSE_SPEED = 0.44
        private const val GROUND_DRAG = 0.985
        private const val AIR_DRAG = 0.997
        private const val STOP_SPEED = 0.006
        private const val MIN_STEERING_SPEED = 0.015
        private const val MIN_TURN_DEGREES = 1.5
        private const val MAX_TURN_DEGREES = 4.0
        private const val JUMP_VELOCITY = 0.42
        private const val GRAVITY = 0.08
        private const val MAX_STEP_HEIGHT = 0.6f
        private const val RIDER_HEIGHT_OFFSET = 0.04
        private const val WHEEL_RADIUS = 0.07
        private const val MAX_WHEEL_ROTATION = 10_000.0f
        private const val LEAN_PER_TURN_DEGREE = 2.25f
        private const val MAX_LEAN_DEGREES = 9.0f
        private const val LEAN_SMOOTHING = 0.35f
        private const val PITCH_SMOOTHING = 0.45f
    }
}
