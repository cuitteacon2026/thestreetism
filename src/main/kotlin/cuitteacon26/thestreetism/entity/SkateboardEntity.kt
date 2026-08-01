package cuitteacon26.thestreetism.entity

import cuitteacon26.thestreetism.item.ModItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
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
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class SkateboardEntity(type: EntityType<out SkateboardEntity>, level: Level) : VehicleEntity(type, level) {
    private val interpolation = InterpolationHandler(this, 3)
    private var forwardWasDown = false

    init {
        blocksBuilding = true
    }

    override fun tick() {
        super.tick()
        interpolation.interpolate()
        tickDamageAnimation()

        if (!level().isClientSide) {
            tickMovement(readControls())
        }
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
            brake = input.backward(),
        )
    }

    private fun tickMovement(controls: Controls) {
        val grounded = onGround()
        var movement = deltaMovement
        var horizontalX = movement.x
        var horizontalZ = movement.z
        var horizontalSpeed = hypot(horizontalX, horizontalZ)

        val turnInput = (if (controls.right) 1 else 0) - (if (controls.left) 1 else 0)
        if (grounded && turnInput != 0 && horizontalSpeed > MIN_STEERING_SPEED) {
            val speedRatio = (horizontalSpeed / MAX_SPEED).coerceIn(0.0, 1.0)
            val turnDegrees = turnInput * (MIN_TURN_DEGREES + (MAX_TURN_DEGREES - MIN_TURN_DEGREES) * speedRatio)
            yRot = Mth.wrapDegrees(yRot + turnDegrees.toFloat())
            val forward = forwardVector()
            horizontalX = forward.x * horizontalSpeed
            horizontalZ = forward.z * horizontalSpeed
        }

        if (grounded && controls.forward && !forwardWasDown) {
            val forward = forwardVector()
            horizontalX += forward.x * KICK_IMPULSE
            horizontalZ += forward.z * KICK_IMPULSE
            horizontalSpeed = hypot(horizontalX, horizontalZ)
            if (horizontalSpeed > MAX_SPEED) {
                val scale = MAX_SPEED / horizontalSpeed
                horizontalX *= scale
                horizontalZ *= scale
            }
        }
        forwardWasDown = controls.forward

        if (grounded && controls.brake) {
            horizontalX *= BRAKE_FACTOR
            horizontalZ *= BRAKE_FACTOR
        }

        val verticalSpeed = if (isNoGravity) movement.y else movement.y - gravity
        setDeltaMovement(horizontalX, verticalSpeed, horizontalZ)
        move(MoverType.SELF, deltaMovement)

        movement = deltaMovement
        val drag = if (onGround()) GROUND_DRAG else AIR_DRAG
        horizontalX = movement.x * drag
        horizontalZ = movement.z * drag
        if (hypot(horizontalX, horizontalZ) < STOP_SPEED) {
            horizontalX = 0.0
            horizontalZ = 0.0
        }
        val resolvedVerticalSpeed = if (onGround() && movement.y < 0.0) 0.0 else movement.y
        setDeltaMovement(horizontalX, resolvedVerticalSpeed, horizontalZ)
    }

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

    override fun getPickResult(): ItemStack = ItemStack(ModItems.SKATEBOARD)

    override fun addAdditionalSaveData(output: ValueOutput) = Unit

    override fun readAdditionalSaveData(input: ValueInput) = Unit

    private data class Controls(
        val left: Boolean,
        val right: Boolean,
        val forward: Boolean,
        val brake: Boolean,
    ) {
        companion object {
            val NONE = Controls(left = false, right = false, forward = false, brake = false)
        }
    }

    companion object {
        private const val KICK_IMPULSE = 0.16
        private const val MAX_SPEED = 0.72
        private const val GROUND_DRAG = 0.985
        private const val AIR_DRAG = 0.997
        private const val BRAKE_FACTOR = 0.62
        private const val STOP_SPEED = 0.006
        private const val MIN_STEERING_SPEED = 0.015
        private const val MIN_TURN_DEGREES = 1.5
        private const val MAX_TURN_DEGREES = 4.0
        private const val GRAVITY = 0.08
        private const val MAX_STEP_HEIGHT = 0.6f
        private const val RIDER_HEIGHT_OFFSET = 0.04
    }
}
