package dev.aidanbooth.pigracing.compat;

import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Coloured redstone dust shown to one player, on every version:
 * <ul>
 *     <li>1.13+: {@code Player#spawnParticle} with {@code DustOptions} (particle REDSTONE, renamed DUST in 1.20.5)</li>
 *     <li>1.9-1.12: {@code Player#spawnParticle} with the colour packed into the offsets</li>
 *     <li>1.8: {@code Player.Spigot#playEffect} with {@code COLOURED_DUST}, the same offset trick</li>
 * </ul>
 */
public final class DustParticles {
    private static final Object DUST;
    private static final Method SPAWN_WITH_DATA;
    private static final Method SPAWN_LEGACY;
    private static final Constructor<?> DUST_OPTIONS;

    static {
        Object dust = null;
        Method withData = null;
        Method legacy = null;
        Constructor<?> options = null;
        try {
            Class<?> particle = Class.forName("org.bukkit.Particle");
            dust = enumConstant(particle, "DUST");
            if (dust == null) {
                dust = enumConstant(particle, "REDSTONE");
            }
            try {
                Class<?> dustOptions = Class.forName("org.bukkit.Particle$DustOptions");
                options = dustOptions.getConstructor(Color.class, float.class);
                withData = Player.class.getMethod("spawnParticle", particle, Location.class, int.class,
                        double.class, double.class, double.class, double.class, Object.class);
            } catch (ClassNotFoundException e) {
                legacy = Player.class.getMethod("spawnParticle", particle, Location.class, int.class,
                        double.class, double.class, double.class, double.class);
            }
        } catch (ReflectiveOperationException e) {
            // 1.8: no Particle API, playEffect is used instead
        }
        DUST = dust;
        SPAWN_WITH_DATA = withData;
        SPAWN_LEGACY = legacy;
        DUST_OPTIONS = options;
    }

    private DustParticles() {
    }

    /** One dust particle. {@code size} only applies on 1.13+, older clients have one size. */
    public static void show(Player player, Location at, Color color, float size) {
        try {
            if (SPAWN_WITH_DATA != null) {
                SPAWN_WITH_DATA.invoke(player, DUST, at, 1, 0d, 0d, 0d, 0d, DUST_OPTIONS.newInstance(color, size));
                return;
            }
            // Count 0 makes the offsets a colour; red can't be exactly 0 or the client picks red
            double r = Math.max(color.getRed() / 255d, 0.001);
            double g = color.getGreen() / 255d;
            double b = color.getBlue() / 255d;
            if (SPAWN_LEGACY != null) {
                SPAWN_LEGACY.invoke(player, DUST, at, 0, r, g, b, 1d);
                return;
            }
            player.spigot().playEffect(at, Effect.COLOURED_DUST, 0, 0, (float) r, (float) g, (float) b, 1f, 0, 64);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A cosmetic particle is never worth breaking the race for
        }
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }
}
