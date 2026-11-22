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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the checkpoint rings in animated particles, to each player separately.
 *
 * <p>Every ring has a faint outline, so its size is clear, and comets with fading tails orbiting the
 * rim in alternating pink and white. Neighbouring rings spin opposite ways.
 *
 * <p>The ring a racer needs next stands out: a dense pink outline that gently breathes, faster
 * comets with longer tails, and a white ripple that keeps shrinking from the rim to the centre,
 * pulling the eye through it.
 *
 * <p>Racers see every ring with their next one highlighted; anyone else sees the plain rings while a
 * race is on; admins previewing the track ({@code /race preview}) see them any time.
 */
public final class RingRenderer implements Runnable {
    private final Main plugin;
    private final Set<UUID> previewing = new HashSet<>();
    /** Advances every redraw; everything that moves is worked out from it. */
    private double time;

    private boolean enabled;
    private double viewDistance;
    private Color color;
    private Color secondColor;
    private double outlineSpacing;
    private int comets;
    private int cometLength;
    private double spinSpeed;
    private double nextOutlineSpacing;
    private int nextComets;
    private int nextCometLength;
    private double nextSpinSpeed;
    private boolean ripple;
    private double rippleSpeed;
    private double rippleSpacing;

    public RingRenderer(Main plugin) {
        this.plugin = plugin;
    }

    public void reload(ConfigurationSection config) {
        enabled = config.getBoolean("Enabled", true);
        viewDistance = config.getDouble("ViewDistance", 32);
        color = parseColor(config.getString("Color"), Color.fromRGB(0xFF55FF));
        secondColor = parseColor(config.getString("SecondColor"), Color.WHITE);
        outlineSpacing = config.getDouble("OutlineSpacing", 0);
        comets = Math.max(0, config.getInt("Comets", 4));
        cometLength = Math.max(1, config.getInt("CometLength", 2));
        spinSpeed = config.getDouble("SpinSpeed", 0.12);
        nextOutlineSpacing = config.getDouble("NextOutlineSpacing", 0.5);
        nextComets = Math.max(0, config.getInt("NextComets", 4));
        nextCometLength = Math.max(1, config.getInt("NextCometLength", 4));
        nextSpinSpeed = config.getDouble("NextSpinSpeed", 0.25);
        ripple = config.getBoolean("Ripple", true);
        rippleSpeed = Math.max(0.005, config.getDouble("RippleSpeed", 0.05));
        rippleSpacing = Math.max(0.2, config.getDouble("RippleSpacing", 0.7));
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
        time += 1;
        PigRace race = plugin.race;
        boolean raceOn = race != null
                && (race.getGameState() == RaceState.STARTING || race.getGameState() == RaceState.INPROGRESS);

        List<Checkpoint> rings = track.all();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Checkpoint next = null;
            Racer racer = race == null ? null : race.getRacer(player);
            if (racer != null && racer.inGame()) {
                next = track.get(race.nextCheckpoint(racer));
            } else if (!raceOn && !previewing.contains(player.getUniqueId())) {
                continue;
            }
            Location eye = player.getLocation();
            for (int i = 0; i < rings.size(); i++) {
                Checkpoint ring = rings.get(i);
                if (ring.distanceSquared(eye) > viewDistance * viewDistance) {
                    continue;
                }
                // Alternate rings spin opposite ways
                double direction = i % 2 == 0 ? 1 : -1;
                if (ring == next) {
                    drawNext(player, ring, direction);
                } else {
                    drawPlain(player, ring, direction);
                }
            }
        }
    }

    private void drawPlain(Player player, Checkpoint ring, double direction) {
        double r = ring.getRadius();
        if (outlineSpacing > 0) {
            drawCircle(player, ring, r, secondColor, outlineSpacing, 0, 0.7f);
        }
        drawComets(player, ring, r, comets, cometLength, time * spinSpeed * direction, direction);
    }

    private void drawNext(Player player, Checkpoint ring, double direction) {
        double r = ring.getRadius();
        // Breathes in and out about once every two seconds at the default redraw rate
        double breath = (Math.sin(time * 0.3) + 1) / 2;
        if (nextOutlineSpacing > 0) {
            drawCircle(player, ring, r, color, nextOutlineSpacing, time * 0.02 * direction, (float) (1.0 + 0.6 * breath));
        }
        drawComets(player, ring, r, nextComets, nextCometLength, time * nextSpinSpeed * direction, direction);
        if (ripple) {
            // Shrinks from the rim to a quarter of the way in, then starts again at the rim
            double phase = (time * rippleSpeed) % 1;
            double rippleRadius = r * (1 - 0.75 * phase);
            Color fade = mix(secondColor, color, phase);
            drawCircle(player, ring, rippleRadius, fade, rippleSpacing, 0, (float) (1.1 - 0.6 * phase));
        }
    }

    /** Comets around the rim: a bright head with a tail that shrinks and fades towards the other colour. */
    private void drawComets(Player player, Checkpoint ring, double r, int count, int length, double angle, double direction) {
        if (count == 0 || r <= 0) {
            return;
        }
        World world = player.getWorld();
        // About 0.3 blocks between tail points whatever the ring's size
        double step = 0.3 / r;
        for (int c = 0; c < count; c++) {
            Color head = c % 2 == 0 ? color : secondColor;
            Color tail = c % 2 == 0 ? secondColor : color;
            double start = angle + c * 2 * Math.PI / count;
            for (int t = 0; t < length; t++) {
                double fraction = length == 1 ? 0 : (double) t / (length - 1);
                double a = start - t * step * direction;
                Vector at = ring.pointOnFace(Math.cos(a) * r, Math.sin(a) * r);
                DustParticles.show(player, at.toLocation(world), mix(head, tail, fraction * 0.7), (float) (1.6 - 1.1 * fraction));
            }
        }
    }

    private void drawCircle(Player player, Checkpoint ring, double r, Color colour, double gap, double offset, float size) {
        if (r <= 0) {
            return;
        }
        World world = player.getWorld();
        int points = Math.max(8, (int) Math.round(2 * Math.PI * r / gap));
        for (int i = 0; i < points; i++) {
            double a = offset + i * 2 * Math.PI / points;
            Vector at = ring.pointOnFace(Math.cos(a) * r, Math.sin(a) * r);
            DustParticles.show(player, at.toLocation(world), colour, size);
        }
    }

    private static Color mix(Color from, Color to, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        return Color.fromRGB(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
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
