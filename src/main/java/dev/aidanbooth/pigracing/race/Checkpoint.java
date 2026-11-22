package dev.aidanbooth.pigracing.race;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * A ring racers fly through: a centre, the way it faces, and its size in blocks. A size 3 ring fits a
 * 3x3 opening exactly. Flying through either way counts; the way it faces only sets how the ring is
 * tilted and which way a crashed racer faces when they respawn there. Holds the world by name so a
 * track can load before its world does.
 */
public final class Checkpoint {
    /** Extra room around the ring's edge, so a pig's body clipping the rim still counts. */
    private static final double MARGIN = 0.5;

    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final double size;
    /** Unit vector the ring faces: the direction racers fly through it. */
    private final Vector normal;
    /** Two unit vectors across the ring's face, for drawing it. */
    private final Vector across, up;

    public Checkpoint(String world, double x, double y, double z, float yaw, float pitch, double size) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.size = size;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        normal = new Vector(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vector side = new Vector(Math.cos(yawRad), 0, Math.sin(yawRad));
        across = side.normalize();
        up = across.clone().crossProduct(normal).normalize();
    }

    /**
     * A ring placed by a player standing at the bottom-middle of an opening, looking through it.
     * Angles snap to 15 degrees. A ring facing straight along an axis snaps to the block grid too, so it
     * lines up with the blocks around it whatever the player's exact position.
     */
    public static Checkpoint placedBy(Location feet, int size) {
        float yaw = (float) snap(normalise(feet.getYaw()), 15);
        float pitch = (float) snap(feet.getPitch(), 15);
        if (yaw >= 360) {
            yaw -= 360;
        }
        double half = size / 2.0;
        double x = feet.getX();
        double y = feet.getBlockY() + half;
        double z = feet.getZ();
        if (pitch == 0 && yaw % 90 == 0) {
            boolean facesZ = yaw % 180 == 0;
            // Along the way racers fly: middle of the block the player stands in.
            // Across: odd sizes centre on a block, even sizes on the line between two.
            double alongBlock = Math.floor(facesZ ? z : x) + 0.5;
            double acrossPos = facesZ ? x : z;
            double acrossSnapped = size % 2 == 1 ? Math.floor(acrossPos) + 0.5 : Math.round(acrossPos);
            if (facesZ) {
                z = alongBlock;
                x = acrossSnapped;
            } else {
                x = alongBlock;
                z = acrossSnapped;
            }
        } else {
            x = Math.floor(x) + 0.5;
            z = Math.floor(z) + 0.5;
        }
        return new Checkpoint(feet.getWorld().getName(), x, y, z, yaw, pitch, size);
    }

    private static double snap(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static float normalise(float yaw) {
        float y = yaw % 360;
        return y < 0 ? y + 360 : y;
    }

    public String getWorldName() {
        return world;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public double getSize() {
        return size;
    }

    public double getRadius() {
        return size / 2.0;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /** The middle of the ring, facing through it, or null if its world isn't loaded. */
    public Location center() {
        World w = getWorld();
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    /** A point on the ring's face: {@code a} blocks across and {@code b} blocks up from the centre. */
    public Vector pointOnFace(double a, double b) {
        return new Vector(x, y, z).add(across.clone().multiply(a)).add(up.clone().multiply(b));
    }

    /** Where a crashed racer comes back: just past the ring, facing on through it. */
    public Location respawn(double forward, double drop) {
        Location c = center();
        if (c == null) {
            return null;
        }
        Location at = c.add(normal.clone().multiply(forward)).subtract(0, drop, 0);
        at.setYaw(yaw);
        at.setPitch(0);
        return at;
    }

    /**
     * Whether something moving from one point to the next went through the ring, either way. Works
     * from the path between the two, so a fast pig can't skip the ring between ticks.
     */
    public boolean crossedBy(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()
                || to.getWorld() == null || !to.getWorld().getName().equals(world)) {
            return false;
        }
        // A teleport isn't flying through anything
        if (from.distanceSquared(to) > 100) {
            return false;
        }
        Vector center = new Vector(x, y, z);
        Vector a = from.toVector();
        Vector b = to.toVector();
        double before = a.clone().subtract(center).dot(normal);
        double after = b.clone().subtract(center).dot(normal);
        // Either way through counts: only crossing the ring's plane matters
        if ((before < 0) == (after < 0)) {
            return false;
        }
        double t = before / (before - after);
        Vector hit = a.add(b.subtract(a).multiply(t));
        double reach = getRadius() + MARGIN;
        return hit.distanceSquared(center) <= reach * reach;
    }

    public double distanceSquared(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double dx = location.getX() - x, dy = location.getY() - y, dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Stored as {@code world~x~y~z~yaw~pitch}, the same format as the other saved locations. */
    public String serialiseLocation() {
        return world + "~" + x + "~" + y + "~" + z + "~" + yaw + "~" + pitch;
    }
}
