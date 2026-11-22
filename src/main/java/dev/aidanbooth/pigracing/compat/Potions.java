package dev.aidanbooth.pigracing.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Slowness was called SLOW until 1.20.5 and SLOWNESS after, so it's looked up by name.
 */
public final class Potions {
    private static final PotionEffectType SLOWNESS = find("SLOWNESS", "SLOW");

    private Potions() {
    }

    /**
     * Stops a mob walking on its own. Only its own legs: velocity set by the plugin still moves it,
     * so a race pig stays frozen the whole race and can't wander off when it's on the ground.
     */
    public static void freeze(LivingEntity entity, int ticks) {
        if (SLOWNESS != null) {
            entity.removePotionEffect(SLOWNESS);
            // Ambient, no particles: no potion swirls around the pig
            entity.addPotionEffect(new PotionEffect(SLOWNESS, ticks, 100, true, false));
        }
    }

    public static void unfreeze(LivingEntity entity) {
        if (SLOWNESS != null) {
            entity.removePotionEffect(SLOWNESS);
        }
    }

    @SuppressWarnings("deprecation")
    private static PotionEffectType find(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }
}
