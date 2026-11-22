package dev.aidanbooth.pigracing.compat;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/**
 * Entity calls whose API changed after 1.8. Newer methods are found once through reflection.
 */
public final class Entities {
    /** 1.11.2+, replaces the deprecated setPassenger. */
    private static final Method ADD_PASSENGER = find(Entity.class, "addPassenger", Entity.class);
    /** 1.13+. Lets a pig face its rider's direction without teleporting it. */
    private static final Method SET_ROTATION = find(Entity.class, "setRotation", float.class, float.class);
    /** 1.13+. Keeps race pigs out of the world save if the server stops mid-race. */
    private static final Method SET_PERSISTENT = find(Entity.class, "setPersistent", boolean.class);

    private Entities() {
    }

    public static void mount(Entity vehicle, Entity passenger) {
        if (ADD_PASSENGER != null && invoke(ADD_PASSENGER, vehicle, passenger)) {
            return;
        }
        vehicle.setPassenger(passenger);
    }

    public static void face(Entity entity, float yaw, float pitch) {
        if (SET_ROTATION != null) {
            invoke(SET_ROTATION, entity, yaw, pitch);
        }
    }

    public static void notPersistent(Entity entity) {
        if (SET_PERSISTENT != null) {
            invoke(SET_PERSISTENT, entity, false);
        }
    }

    /**
     * Moves a rider and its mount together. A ridden entity can't be teleported, so this unmounts,
     * moves both and mounts again.
     */
    public static void teleportMounted(Entity vehicle, Entity rider, Location to) {
        vehicle.eject();
        vehicle.teleport(to);
        rider.teleport(to);
        vehicle.setVelocity(new org.bukkit.util.Vector());
        mount(vehicle, rider);
    }

    private static Method find(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean invoke(Method method, Object target, Object... args) {
        try {
            method.invoke(target, args);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
