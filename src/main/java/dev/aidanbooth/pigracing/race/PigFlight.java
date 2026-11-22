package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.compat.Entities;
import dev.aidanbooth.pigracing.input.RiderInput;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Flies each racer's pig, once per tick, using only Bukkit's setVelocity so it works on every version.
 *
 * The pig flies where its rider looks, up and down included, like a glider: hold W to fly, let go and
 * it slows down and sinks. Climbing costs speed and diving gains it, and every checkpoint passed since
 * the pig last crashed raises its top speed. Turns are smoothed so the pig banks round instead of
 * snapping to the camera.
 *
 * The velocity is kept per racer instead of read back from the pig, because the server applies its own
 * gravity and friction to the pig between ticks, and reading that back would count them twice.
 */
public final class PigFlight {
    private final RiderInput input;

    private double baseSpeed;
    private double speedPerCheckpoint;
    private double maxSpeed;
    private boolean requireForwardKey;
    private double acceleration;
    private double deceleration;
    private double handling;
    private double maxClimb;
    private double maxDive;
    private double takeoffAngle;
    private double diveBoost;
    private double climbPenalty;
    private double sinkSpeed;
    private int respawnHoverTicks;
    private double knockbackFade;

    public PigFlight(RiderInput input, ConfigurationSection config) {
        this.input = input;
        reload(config);
    }

    public void reload(ConfigurationSection config) {
        baseSpeed = Math.max(0.01, config.getDouble("BaseSpeed", 0.45));
        speedPerCheckpoint = config.getDouble("SpeedPerCheckpoint", 0.03);
        maxSpeed = Math.max(baseSpeed, config.getDouble("MaxSpeed", 0.9));
        requireForwardKey = config.getBoolean("RequireForwardKey", true);
        acceleration = Math.max(0.001, config.getDouble("Acceleration", 0.04));
        deceleration = Math.max(0.001, config.getDouble("Deceleration", 0.03));
        handling = clamp(config.getDouble("Handling", 0.3), 0.05, 1);
        maxClimb = Math.toRadians(clamp(config.getDouble("MaxClimbAngle", 50), 0, 90));
        maxDive = Math.toRadians(clamp(config.getDouble("MaxDiveAngle", 70), 0, 90));
        takeoffAngle = Math.toRadians(clamp(config.getDouble("TakeoffAngle", 25), 0, 90));
        diveBoost = Math.max(0, config.getDouble("DiveBoost", 0.25));
        climbPenalty = clamp(config.getDouble("ClimbPenalty", 0.2), 0, 1);
        sinkSpeed = Math.max(0, config.getDouble("SinkSpeed", 0.3));
        respawnHoverTicks = Math.max(0, config.getInt("RespawnHoverTicks", 30));
        knockbackFade = clamp(config.getDouble("KnockbackFade", 0.7), 0, 0.95);
    }

    public RiderInput getInput() {
        return input;
    }

    public int getRespawnHoverTicks() {
        return respawnHoverTicks;
    }

    /** The speed a racer flies at right now, for the action bar. */
    public double topSpeed(Racer racer) {
        return Math.min(baseSpeed + speedPerCheckpoint * racer.getSpeedLevel(), maxSpeed);
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

        Location eye = player.getLocation();
        float yaw = eye.getYaw();
        // Minecraft pitch is negative looking up; here up is positive
        double pitch = clamp(-Math.toRadians(eye.getPitch()), -maxDive, maxClimb);
        boolean throttle = !requireForwardKey || input.isForward(player);
        if (throttle && pig.isOnGround()) {
            // Looking level or down on the ground would just scrape along it, so take off instead
            pitch = Math.max(pitch, takeoffAngle);
        }

        // Speed eases towards its target instead of jumping there
        double target = 0;
        if (throttle) {
            double slope = Math.sin(pitch);
            double factor = slope >= 0 ? 1 - climbPenalty * slope : 1 + diveBoost * -slope;
            target = topSpeed(racer) * factor;
        }
        double speed = racer.getSpeed();
        speed = speed < target ? Math.min(target, speed + acceleration) : Math.max(target, speed - deceleration);
        racer.setSpeed(speed);

        double radians = Math.toRadians(yaw);
        double flat = Math.cos(pitch);
        Vector desired = new Vector(-Math.sin(radians) * flat, Math.sin(pitch), Math.cos(radians) * flat).multiply(speed);

        // Without enough speed to glide the pig sinks, all the way to SinkSpeed once it's stopped
        double lift = Math.min(1, speed / baseSpeed);
        double sink = sinkSpeed * (1 - lift);
        if (racer.tickHover()) {
            sink = 0;
            desired.setY(Math.max(0, desired.getY()));
        }
        desired.setY(desired.getY() - sink);

        Vector velocity = racer.getVelocity();
        velocity.add(desired.subtract(velocity).multiply(handling));
        racer.setVelocity(velocity);

        pig.setVelocity(velocity.clone().add(racer.takeKnockback(knockbackFade)));
        Entities.face(pig, yaw, 0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
