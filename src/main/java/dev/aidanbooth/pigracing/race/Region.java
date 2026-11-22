package dev.aidanbooth.pigracing.race;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A box of whole blocks, both corners included. Replaces WorldEdit 6's CuboidSelection, which no
 * longer exists in WorldEdit 7. Holds the world by name so a track can load before its world does.
 */
public final class Region {
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Region(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Region between(Location a, Location b) {
        return new Region(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String getWorldName() {
        return world;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        double x = location.getX(), y = location.getY(), z = location.getZ();
        return x >= minX && x < maxX + 1
                && y >= minY && y < maxY + 1
                && z >= minZ && z < maxZ + 1;
    }

    /**
     * Whether a move from one point to another passed through this region. A fast pig can skip a
     * thin gate between two move events, so the path between them is checked too.
     */
    public boolean crossedBy(Location from, Location to) {
        if (contains(to)) {
            return true;
        }
        if (from == null || from.getWorld() != to.getWorld()) {
            return false;
        }
        double distance = from.distance(to);
        int steps = (int) Math.ceil(distance / 0.25);
        if (steps <= 1 || steps > 200) {
            return false;
        }
        Location point = from.clone();
        double dx = (to.getX() - from.getX()) / steps;
        double dy = (to.getY() - from.getY()) / steps;
        double dz = (to.getZ() - from.getZ()) / steps;
        for (int i = 1; i < steps; i++) {
            point.add(dx, dy, dz);
            if (contains(point)) {
                return true;
            }
        }
        return false;
    }

    /** The middle of the region, or null if its world isn't loaded. */
    public Location center() {
        World w = getWorld();
        if (w == null) {
            return null;
        }
        return new Location(w, (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    /** True when the gate is wider along X than Z, i.e. racers fly through it along Z. */
    public boolean facesAlongX() {
        return (maxX - minX) > (maxZ - minZ);
    }

    public double distanceSquared(Location location) {
        Location c = center();
        if (c == null || location.getWorld() != c.getWorld()) {
            return Double.MAX_VALUE;
        }
        return c.distanceSquared(location);
    }
}
