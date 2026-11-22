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

    public static void freeze(LivingEntity entity, int ticks) {
        if (SLOWNESS != null) {
            entity.removePotionEffect(SLOWNESS);
            entity.addPotionEffect(new PotionEffect(SLOWNESS, ticks, 100));
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
