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
    /** Up/down speed, kept here because the server's own gravity would be counted twice if read back. */
    private double verticalSpeed;
    /** Ticks left that the pig holds its height after a respawn. */
    private int hoverTicks;
    private int jumpCooldown;
    private int stuckTicks;
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
        verticalSpeed = 0;
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

    public double getVerticalSpeed() {
        return verticalSpeed;
    }

    public void setVerticalSpeed(double verticalSpeed) {
        this.verticalSpeed = verticalSpeed;
    }

    /** Ticks this racer has been riding while the server waits on a teleport confirmation. */
    public int addStuckTick() {
        return ++stuckTicks;
    }

    public void resetStuckTicks() {
        stuckTicks = 0;
    }

    public void endHover() {
        hoverTicks = 0;
    }

    /** Counts the jump cooldown down a tick. Returns true when the pig may flap. */
    public boolean tickJumpCooldown() {
        if (jumpCooldown > 0) {
            jumpCooldown--;
            return false;
        }
        return true;
    }

    public void startJumpCooldown(int ticks) {
        jumpCooldown = ticks;
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
