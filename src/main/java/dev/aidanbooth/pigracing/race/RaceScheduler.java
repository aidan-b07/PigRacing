package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens races on a schedule: every so many minutes, at set times of day, or both. Checked once a
 * second. A race isn't opened while one is already running, when too few players are online, or when
 * the track isn't set up; that slot is skipped and the next one waited for.
 */
public final class RaceScheduler implements Runnable {
    private final Main plugin;

    private boolean enabled;
    private int everyMinutes;
    private final List<LocalTime> times = new ArrayList<>();
    private int minOnline;

    /** When the interval next fires, or null if there's no interval. */
    private LocalDateTime nextInterval;
    /** The minute a set time last fired, so it only fires once. */
    private LocalDateTime lastTimeFired;

    public RaceScheduler(Main plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigurationSection config) {
        enabled = config.getBoolean("Enabled", false);
        everyMinutes = Math.max(0, config.getInt("EveryMinutes", 0));
        minOnline = Math.max(0, config.getInt("MinOnline", 2));
        times.clear();
        for (String text : config.getStringList("Times")) {
            try {
                times.add(LocalTime.parse(text.trim().length() == 4 ? "0" + text.trim() : text.trim()));
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Schedule: '" + text + "' isn't a time like 18:00 or 9:30, skipping it");
            }
        }
        times.sort(null);
        nextInterval = enabled && everyMinutes > 0 ? LocalDateTime.now().plusMinutes(everyMinutes).withSecond(0).withNano(0) : null;
    }

    @Override
    public void run() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean due = false;
        if (nextInterval != null && !now.isBefore(nextInterval)) {
            due = true;
            // Measured from when it was due, so a race that ran late doesn't push the schedule back
            while (!now.isBefore(nextInterval)) {
                nextInterval = nextInterval.plusMinutes(everyMinutes);
            }
        }
        LocalDateTime minute = now.withSecond(0).withNano(0);
        if (times.contains(minute.toLocalTime()) && !minute.equals(lastTimeFired)) {
            lastTimeFired = minute;
            due = true;
        }
        if (due) {
            open();
        }
    }

    private void open() {
        if (plugin.race != null) {
            plugin.getLogger().info("Schedule: skipped a race, one is already running");
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        if (online < minOnline) {
            plugin.getLogger().info("Schedule: skipped a race, only " + online + " of " + minOnline + " players online");
            return;
        }
        String problem = PigRace.validate(plugin);
        if (problem != null) {
            plugin.getLogger().warning("Schedule: couldn't open a race: " + problem);
            return;
        }
        new PigRace(plugin);
    }

    /** When the next scheduled race opens, or null if nothing is scheduled. */
    public LocalDateTime next() {
        if (!enabled) {
            return null;
        }
        LocalDateTime best = nextInterval;
        LocalDateTime now = LocalDateTime.now();
        for (LocalTime time : times) {
            LocalDateTime at = LocalDateTime.of(LocalDate.now(), time);
            if (!at.isAfter(now)) {
                at = at.plusDays(1);
            }
            if (best == null || at.isBefore(best)) {
                best = at;
            }
        }
        return best;
    }
}
