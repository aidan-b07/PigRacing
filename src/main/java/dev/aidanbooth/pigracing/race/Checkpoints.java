package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * The track's rings, in order. Numbers start at 1. Ring 1 is the start/finish line: racers start
 * just behind it, and a lap is done when they fly back through it after every other ring.
 *
 * <pre>
 * track.yml:
 * Rings:
 *   '1':
 *     Location: world~41.5~89.5~343.5~180.0~0.0   # centre, then the way it faces (yaw, pitch)
 *     Size: 3                                     # fits a 3x3 opening
 * </pre>
 */
public final class Checkpoints {
    public static final String PATH = "Rings";

    private final List<Checkpoint> rings = new ArrayList<>();

    /** Loads from config. Rings in worlds that aren't loaded yet are still kept, by world name. */
    public void load(FileConfiguration config, Logger log) {
        rings.clear();
        ConfigurationSection section = config.getConfigurationSection(PATH);
        if (section == null) {
            return;
        }
        Map<Integer, Checkpoint> ordered = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                log.warning("Skipping checkpoint '" + key + "': its name has to be a number");
                continue;
            }
            Checkpoint ring = parse(section.getString(key + ".Location"), section.getDouble(key + ".Size", 3));
            if (ring == null) {
                log.warning("Skipping checkpoint " + key + ": it needs a Location (world~x~y~z~yaw~pitch) and a Size."
                        + " Old two-corner checkpoints have to be placed again with /race ring add.");
                continue;
            }
            ordered.put(number, ring);
        }
        rings.addAll(ordered.values());
    }

    private static Checkpoint parse(String location, double size) {
        String[] p = LocationSerialiser.split(location);
        if (p == null || size <= 0) {
            return null;
        }
        try {
            float yaw = p.length == 6 ? Float.parseFloat(p[4]) : 0;
            float pitch = p.length == 6 ? Float.parseFloat(p[5]) : 0;
            return new Checkpoint(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), yaw, pitch, size);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Writes every ring back to config, numbered from 1 in track order. */
    public void save(FileConfiguration config) {
        config.set(PATH, null);
        for (int i = 0; i < rings.size(); i++) {
            Checkpoint ring = rings.get(i);
            String key = PATH + "." + (i + 1);
            config.set(key + ".Location", ring.serialiseLocation());
            config.set(key + ".Size", ring.getSize() == Math.rint(ring.getSize()) ? (Object) (int) ring.getSize() : ring.getSize());
        }
    }

    public void add(Checkpoint ring) {
        rings.add(ring);
    }

    /** Removes a ring by number; the ones after it move up. */
    public boolean remove(int number) {
        if (number < 1 || number > rings.size()) {
            return false;
        }
        rings.remove(number - 1);
        return true;
    }

    public void clear() {
        rings.clear();
    }

    public int size() {
        return rings.size();
    }

    /** Ring by number, starting at 1. */
    public Checkpoint get(int number) {
        if (number < 1 || number > rings.size()) {
            return null;
        }
        return rings.get(number - 1);
    }

    public int indexOf(Checkpoint ring) {
        return rings.indexOf(ring) + 1;
    }

    public List<Checkpoint> all() {
        return Collections.unmodifiableList(rings);
    }
}
