package dev.aidanbooth.pigracing.race;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Racer {
    private final Player player;
    private LivingEntity pig;
    private int lap;
    private int checkpoint;
    private boolean inGame;
    /** Checkpoints passed since the pig last touched the ground. Each one adds speed. */
    private int speedLevel;
    /** Forward speed, eased towards the pig's target speed. */
    private double speed;
    /** The velocity flown last tick, smoothed towards where the rider looks. */
    private Vector velocity = new Vector();
    /** Ticks left that the pig holds its height after a respawn. */
    private int hoverTicks;
    private long finishMillis;
    /** A shove from another racer, fading over a few ticks. */
    private Vector knockback = new Vector();
    /** Where the pig was last tick, to catch gates it flew through between ticks. */
    private Location lastPigLocation;
    /** Set once the pig leaves the ground, so sitting at the start or a respawn isn't a crash. */
    private boolean airborne;
    private int groundTicks;

    public Racer(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean is(Player other) {
        return player.getUniqueId().equals(other.getUniqueId());
    }

    public LivingEntity getPig() {
        return pig;
    }

    public void setPig(LivingEntity pig) {
        this.pig = pig;
    }

    public int getLap() {
        return lap;
    }

    public void addLap() {
        lap++;
    }

    public int getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(int checkpoint) {
        this.checkpoint = checkpoint;
    }

    public void nextCheckpoint() {
        checkpoint++;
        speedLevel++;
    }

    public boolean inGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    /** Back to a standstill after a crash or respawn, hovering for a moment. */
    public void resetSpeed(int hoverTicks) {
        speedLevel = 0;
        speed = 0;
        velocity = new Vector();
        this.hoverTicks = hoverTicks;
        knockback = new Vector();
        lastPigLocation = null;
        airborne = false;
        groundTicks = 0;
    }

    /** Counts ticks spent on the ground since the last respawn, and returns the new count. */
    public int addGroundTick() {
        return ++groundTicks;
    }

    public void addKnockback(Vector push) {
        knockback.add(push);
    }

    /** This tick's share of any knockback, which then fades. */
    public Vector takeKnockback(double fade) {
        Vector now = knockback.clone();
        knockback.multiply(fade);
        if (knockback.lengthSquared() < 0.0001) {
            knockback = new Vector();
        }
        return now;
    }

    public Location getLastPigLocation() {
        return lastPigLocation;
    }

    public void setLastPigLocation(Location lastPigLocation) {
        this.lastPigLocation = lastPigLocation;
    }

    public boolean isAirborne() {
        return airborne;
    }

    public void setAirborne(boolean airborne) {
        this.airborne = airborne;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public Vector getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }

    /** Whether the pig is still hovering after a respawn; counts down one tick. */
    public boolean tickHover() {
        if (hoverTicks <= 0) {
            return false;
        }
        hoverTicks--;
        return true;
    }

    public long getFinishMillis() {
        return finishMillis;
    }

    public void setFinishMillis(long finishMillis) {
        this.finishMillis = finishMillis;
    }
}
