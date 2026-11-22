package dev.aidanbooth.pigracing.compat;

import com.cryptomorin.xseries.XSound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Plays a sound named in config. Names are modern (e.g. ENTITY_PLAYER_LEVELUP); XSound maps them to
 * whatever this server calls them, back to 1.8. Unknown names, or "none", play nothing.
 */
public final class Sounds {
    private Sounds() {
    }

    /** Reads {@code <path>.sound}, {@code <path>.volume} and {@code <path>.pitch}. */
    public static void play(Player player, ConfigurationSection config, String path) {
        String name = config.getString(path + ".sound", "none");
        if (name == null || name.equalsIgnoreCase("none")) {
            return;
        }
        float volume = (float) config.getDouble(path + ".volume", 1);
        float pitch = (float) config.getDouble(path + ".pitch", 1);
        XSound.of(name).ifPresent(sound -> sound.play(player, volume, pitch));
    }
}
