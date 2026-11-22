package dev.aidanbooth.pigracing.input;

import org.bukkit.plugin.Plugin;

/**
 * Picks the rider input source for this server: Bukkit's own API on 1.21.3+, PacketEvents before that.
 */
public final class RiderInputs {
    private static boolean packetsLoaded;

    private RiderInputs() {
    }

    /** Call from onLoad. */
    public static void load(Plugin plugin) {
        if (NativeRiderInput.tryCreate() == null) {
            PacketRiderInput.load(plugin);
            packetsLoaded = true;
        }
    }

    /** Call from onEnable, after {@link #load}. */
    public static RiderInput start() {
        if (packetsLoaded) {
            return PacketRiderInput.start();
        }
        return NativeRiderInput.tryCreate();
    }
}
