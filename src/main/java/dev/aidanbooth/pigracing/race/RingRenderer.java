package dev.aidanbooth.pigracing.race;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.compat.DustParticles;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the checkpoint rings in particles, to each player separately:
 * <ul>
 *     <li>A racer sees every ring, with the one they need next drawn brighter, denser and with
 *     sparkles circling it, so it's obvious where to go.</li>
 *     <li>Anyone else sees the plain rings while a race is on.</li>
 *     <li>Admins previewing the track ({@code /race preview}) see them any time.</li>
 * </ul>
 */
public final class RingRenderer implements Runnable {
    private final Main plugin;
    private final Set<UUID> previewing = new HashSet<>();
    private double spin;

    private boolean enabled;
    private double viewDistance;
    private double spacing;
    private double nextSpacing;
    private Color ringColor;
    private Color nextColor;
    private Color sparkleColor;
    private int sparkles;
    private double spinSpeed;

    public RingRenderer(Main plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigurationSection config) {
        enabled = config.getBoolean("Enabled", true);
        viewDistance = config.getDouble("ViewDistance", 48);
        spacing = Math.max(0.15, config.getDouble("Spacing", 0.5));
        nextSpacing = Math.max(0.1, config.getDouble("NextSpacing", 0.25));
        ringColor = parseColor(config.getString("RingColor"), Color.WHITE);
        nextColor = parseColor(config.getString("NextRingColor"), Color.fromRGB(0xFF55FF));
        sparkleColor = parseColor(config.getString("SparkleColor"), Color.WHITE);
        sparkles = Math.max(0, config.getInt("Sparkles", 4));
        spinSpeed = config.getDouble("SpinSpeed", 0.15);
    }

    /** Turns the track preview on or off for a player. Returns whether it's now on. */
    public boolean togglePreview(Player player) {
        if (previewing.remove(player.getUniqueId())) {
            return false;
        }
        previewing.add(player.getUniqueId());
        return true;
    }

    public void forget(Player player) {
        previewing.remove(player.getUniqueId());
    }

    @Override
    public void run() {
        Checkpoints track = plugin.getCheckpoints();
        if (!enabled || track.size() == 0) {
            return;
        }
        spin += spinSpeed;
        PigRace race = plugin.race;
        boolean raceOn = race != null
                && (race.getGameState() == RaceState.STARTING || race.getGameState() == RaceState.INPROGRESS);

        for (Player player : Bukkit.getOnlinePlayers()) {
            Checkpoint next = null;
            Racer racer = race == null ? null : race.getRacer(player);
            if (racer != null && racer.inGame()) {
                next = track.get(race.nextCheckpoint(racer));
            } else if (!raceOn && !previewing.contains(player.getUniqueId())) {
                continue;
            }
            Location eye = player.getLocation();
            for (Checkpoint ring : track.all()) {
                if (ring.distanceSquared(eye) > viewDistance * viewDistance) {
                    continue;
                }
                if (ring == next) {
                    drawNext(player, ring);
                } else {
                    drawRing(player, ring, ringColor, spacing, 0, 1f);
                }
            }
        }
    }

    private void drawNext(Player player, Checkpoint ring) {
        drawRing(player, ring, nextColor, nextSpacing, spin * 0.25, 1.4f);
        World world = player.getWorld();
        double r = ring.getRadius();
        for (int i = 0; i < sparkles; i++) {
            double angle = spin + i * 2 * Math.PI / sparkles;
            // Just inside the rim, so they read as part of the ring and not a second one
            Vector at = ring.pointOnFace(Math.cos(angle) * (r - 0.25), Math.sin(angle) * (r - 0.25));
            DustParticles.show(player, at.toLocation(world), sparkleColor, 1.6f);
        }
    }

    private void drawRing(Player player, Checkpoint ring, Color color, double gap, double offset, float size) {
        World world = player.getWorld();
        double r = ring.getRadius();
        int points = Math.max(8, (int) Math.round(2 * Math.PI * r / gap));
        for (int i = 0; i < points; i++) {
            double angle = offset + i * 2 * Math.PI / points;
            Vector at = ring.pointOnFace(Math.cos(angle) * r, Math.sin(angle) * r);
            DustParticles.show(player, at.toLocation(world), color, size);
        }
    }

    private static Color parseColor(String hex, Color fallback) {
        if (hex == null) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
