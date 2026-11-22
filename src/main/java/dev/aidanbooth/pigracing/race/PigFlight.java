package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.compat.Entities;
import dev.aidanbooth.pigracing.input.RiderInput;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Flies each racer's pig, once per tick, using only Bukkit's setVelocity so it works on every version.
 *
 * Holding space climbs and letting go falls, flappy-bird style. The pig flies where its rider looks,
 * and gets faster with each checkpoint passed until it touches the ground.
 *
 * Vertical speed is kept per racer instead of read back from the pig, because the server applies its
 * own gravity to the pig between ticks and reading it back would count gravity twice.
 */
public final class PigFlight {
    private final RiderInput input;

    private double baseSpeed;
    private double speedPerCheckpoint;
    private double maxSpeed;
    private boolean requireForwardKey;
    private double climbAcceleration;
    private double climbSpeed;
    private double gravity;
    private double maxFallSpeed;
    private double knockbackFade;

    public PigFlight(RiderInput input, ConfigurationSection config) {
        this.input = input;
        reload(config);
    }

    public void reload(ConfigurationSection config) {
        baseSpeed = config.getDouble("BaseSpeed", 0.4);
        speedPerCheckpoint = config.getDouble("SpeedPerCheckpoint", 0.03);
        maxSpeed = config.getDouble("MaxSpeed", 0.9);
        requireForwardKey = config.getBoolean("RequireForwardKey", true);
        climbAcceleration = config.getDouble("ClimbAcceleration", 0.4);
        climbSpeed = config.getDouble("ClimbSpeed", 0.1);
        gravity = config.getDouble("Gravity", 0.08);
        maxFallSpeed = config.getDouble("MaxFallSpeed", 0.3);
        knockbackFade = Math.max(0, Math.min(0.95, config.getDouble("KnockbackFade", 0.7)));
    }

    public RiderInput getInput() {
        return input;
    }

    /** Moves one racer's pig for this tick. */
    public void tick(Racer racer) {
        LivingEntity pig = racer.getPig();
        Player player = racer.getPlayer();
        if (pig == null || !pig.isValid()) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle == null || !vehicle.getUniqueId().equals(pig.getUniqueId())) {
            return;
        }

        double vy = racer.getVerticalSpeed();
        if (input.isJumping(player)) {
            vy = Math.min(vy + climbAcceleration, climbSpeed);
        } else {
            vy = Math.max(vy - gravity, -maxFallSpeed);
        }
        racer.setVerticalSpeed(vy);

        double speed = 0;
        if (!requireForwardKey || input.isForward(player)) {
            speed = Math.min(baseSpeed + speedPerCheckpoint * racer.getSpeedLevel(), maxSpeed);
        }

        float yaw = player.getLocation().getYaw();
        double radians = Math.toRadians(yaw);
        Vector velocity = new Vector(-Math.sin(radians) * speed, vy, Math.cos(radians) * speed);
        velocity.add(racer.takeKnockback(knockbackFade));
        pig.setVelocity(velocity);
        Entities.face(pig, yaw, 0);
    }
}
