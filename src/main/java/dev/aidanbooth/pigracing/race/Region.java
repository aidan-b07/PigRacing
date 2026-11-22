package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import org.bukkit.Location;

/**
 * A box of whole blocks, both corners included: the optional race boundary. Holds the world by name
 * so it can load before its world does.
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

    /** Reads two stored corners, or null if either is missing or they're in different worlds. */
    public static Region parse(String pos1, String pos2) {
        String[] a = LocationSerialiser.split(pos1);
        String[] b = LocationSerialiser.split(pos2);
        if (a == null || b == null || !a[0].equals(b[0])) {
            return null;
        }
        try {
            return new Region(a[0],
                    (int) Math.floor(Double.parseDouble(a[1])), (int) Math.floor(Double.parseDouble(a[2])), (int) Math.floor(Double.parseDouble(a[3])),
                    (int) Math.floor(Double.parseDouble(b[1])), (int) Math.floor(Double.parseDouble(b[2])), (int) Math.floor(Double.parseDouble(b[3])));
        } catch (NumberFormatException e) {
            return null;
        }
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

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }
}
