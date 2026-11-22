package dev.aidanbooth.pigracing.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Stores locations as {@code world~x~y~z} (block position) or {@code world~x~y~z~yaw~pitch}.
 */
public class LocationSerialiser {

    public static String getStringFromLocation(Location loc) {
        if (loc == null) {
            return "";
        }
        return loc.getWorld().getName() + "~" + loc.getX() + "~" + loc.getY() + "~" + loc.getZ() + "~" + loc.getYaw() + "~" + loc.getPitch();
    }

    public static String getLiteStringFromLocation(Location loc) {
        if (loc == null) {
            return "";
        }
        return loc.getWorld().getName() + "~" + loc.getBlockX() + "~" + loc.getBlockY() + "~" + loc.getBlockZ();
    }

    /** Splits a stored location into its parts, or null if it isn't one. */
    public static String[] split(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String[] parts = s.split("~");
        return parts.length == 4 || parts.length == 6 ? parts : null;
    }

    /**
     * Reads either format. Block positions are centred in their block. Returns null if the value is
     * missing, malformed, or its world isn't loaded.
     */
    public static Location parse(String s) {
        String[] parts = split(s);
        if (parts == null) {
            return null;
        }
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            if (parts.length == 4) {
                return new Location(w, x + 0.5, y, z + 0.5);
            }
            return new Location(w, x, y, z, Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
