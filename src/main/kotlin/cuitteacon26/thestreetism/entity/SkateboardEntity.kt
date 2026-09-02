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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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

    /**
     * Momentum-preserving skateboard physics.
     *
     * Velocity is kept in world space and decomposed against the board's own
     * axes each tick. Steering rotates the deck without touching the existing
     * momentum, so the board keeps travelling along its old heading until wheel
     * grip pulls it back in line. That gap between "where it points" and "where
     * it moves" is what produces carving, drift and coasting inertia.
     */
    private fun tickMovement(controls: Controls) {
        val grounded = onGround()
        val movement = deltaMovement
        val entrySpeed = hypot(movement.x, movement.z)

        steer(controls, entrySpeed, grounded)

        // Decompose against the (possibly just-rotated) deck axes.
        val forward = forwardVector()
        val right = Vec3(-forward.z, 0.0, forward.x)
        var alongDeck = movement.x * forward.x + movement.z * forward.z
        var acrossDeck = movement.x * right.x + movement.z * right.z

        if (grounded && controls.forward && !forwardWasDown) {
            alongDeck = (alongDeck + KICK_IMPULSE).coerceAtMost(MAX_SPEED)
        }
        forwardWasDown = controls.forward

        if (grounded && controls.brake) {
            alongDeck *= BRAKE_FACTOR
            acrossDeck *= BRAKE_FACTOR
        }

        // Wheels resist sideways travel. Part of the scrubbed sideways speed is
        // handed back to forward travel so a clean carve keeps its momentum
        // instead of bleeding away.
        val keptAcross = acrossDeck * (if (grounded) LATERAL_GRIP else AIR_LATERAL_GRIP)
        if (grounded) {
            val scrubbed = abs(acrossDeck) - abs(keptAcross)
            alongDeck += scrubbed * CARVE_RECOVERY * (if (alongDeck < 0.0) -1.0 else 1.0)
        }
        acrossDeck = keptAcross

        val drag = if (grounded) GROUND_DRAG else AIR_DRAG
        alongDeck *= drag
        acrossDeck *= drag

        if (hypot(alongDeck, acrossDeck) < STOP_SPEED) {
            alongDeck = 0.0
            acrossDeck = 0.0
        }

        val exitSpeed = hypot(alongDeck, acrossDeck)
        if (exitSpeed > MAX_SPEED) {
            val scale = MAX_SPEED / exitSpeed
            alongDeck *= scale
            acrossDeck *= scale
        }

        val verticalSpeed = if (isNoGravity) movement.y else movement.y - gravity
        setDeltaMovement(
            forward.x * alongDeck + right.x * acrossDeck,
            verticalSpeed,
            forward.z * alongDeck + right.z * acrossDeck,
        )
        move(MoverType.SELF, deltaMovement)

        // Preserve whatever the collision pass left behind; only settle the
        // vertical component so resting on the ground does not accumulate fall
        // speed forever.
        val resolved = deltaMovement
        val resolvedVerticalSpeed = if (onGround() && resolved.y < 0.0) 0.0 else resolved.y
        setDeltaMovement(resolved.x, resolvedVerticalSpeed, resolved.z)
    }

    /**
     * Rotate the deck. Turn rate tightens at low speed and widens as the board
     * picks up pace, which keeps the effective turning circle believable rather
     * than letting it pivot on a coin at full tilt.
     */
    private fun steer(controls: Controls, speed: Double, grounded: Boolean) {
        val turnInput = (if (controls.right) 1 else 0) - (if (controls.left) 1 else 0)
        if (turnInput == 0 || speed <= MIN_STEERING_SPEED) return

        val speedRatio = (speed / MAX_SPEED).coerceIn(0.0, 1.0)
        val authority = if (grounded) 1.0 else AIR_STEERING_AUTHORITY
        val turnDegrees = turnInput * (MAX_TURN_DEGREES - (MAX_TURN_DEGREES - MIN_TURN_DEGREES) * speedRatio) * authority
        yRot = Mth.wrapDegrees(yRot + turnDegrees.toFloat())
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
        private const val GROUND_DRAG = 0.988
        private const val AIR_DRAG = 0.997
        private const val BRAKE_FACTOR = 0.62
        private const val STOP_SPEED = 0.006
        private const val MIN_STEERING_SPEED = 0.015

        /** Turn rate at full speed (wide arc) and at crawling speed (tight pivot). */
        private const val MIN_TURN_DEGREES = 1.6
        private const val MAX_TURN_DEGREES = 4.5

        /** Fraction of sideways velocity the wheels keep each tick. Lower = more grip. */
        private const val LATERAL_GRIP = 0.55

        /** Airborne the wheels bite nothing, so sideways drift persists. */
        private const val AIR_LATERAL_GRIP = 0.97

        /** Share of scrubbed sideways speed converted back into forward drive. */
        private const val CARVE_RECOVERY = 0.5

        /** Steering effectiveness while airborne. */
        private const val AIR_STEERING_AUTHORITY = 0.35
        private const val GRAVITY = 0.08
        private const val MAX_STEP_HEIGHT = 0.6f
        private const val RIDER_HEIGHT_OFFSET = 0.04
    }
}
