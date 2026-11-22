package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The track's checkpoint gates, in order. Checkpoint numbers start at 1; passing the last one
 * completes a lap.
 */
public final class Checkpoints {
    private static final String PATH = "Data.Checkpoints";

    private final List<Region> regions = new ArrayList<>();

    /** Loads from config. Gates in worlds that aren't loaded yet are still kept, by world name. */
    public void load(FileConfiguration config) {
        regions.clear();
        ConfigurationSection section = config.getConfigurationSection(PATH);
        if (section == null) {
            return;
        }
        Map<Integer, Region> ordered = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            Region region = parse(section.getString(key + ".Loc1"), section.getString(key + ".Loc2"));
            if (region == null) {
                continue;
            }
            try {
                ordered.put(Integer.parseInt(key), region);
            } catch (NumberFormatException ignored) {
            }
        }
        regions.addAll(ordered.values());
    }

    private static Region parse(String loc1, String loc2) {
        String[] a = LocationSerialiser.split(loc1);
        String[] b = LocationSerialiser.split(loc2);
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

    public int size() {
        return regions.size();
    }

    /** Checkpoint by number, starting at 1. */
    public Region get(int number) {
        if (number < 1 || number > regions.size()) {
            return null;
        }
        return regions.get(number - 1);
    }

    public List<Region> all() {
        return Collections.unmodifiableList(regions);
    }
}
