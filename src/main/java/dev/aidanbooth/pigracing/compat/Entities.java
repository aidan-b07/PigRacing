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
            return;
        }
        LegacyRotation.set(entity, yaw, pitch);
    }

    /**
     * Before 1.13 there's no API to turn an entity that has a rider (teleporting it fails), so the
     * server entity's rotation fields are set directly. Body, head and facing all have to move, or the
     * client snaps the pig back. Names are Spigot's for 1.8 to 1.12; anything missing is skipped.
     */
    private static final class LegacyRotation {
        private static boolean broken;
        private static Method getHandle;
        private static java.lang.reflect.Field yaw, pitch, bodyYaw, headYaw;

        static void set(Entity entity, float newYaw, float newPitch) {
            if (broken) {
                return;
            }
            try {
                if (getHandle == null) {
                    getHandle = entity.getClass().getMethod("getHandle");
                    Class<?> nms = getHandle.invoke(entity).getClass();
                    yaw = field(nms, "yaw");
                    pitch = field(nms, "pitch");
                    // Obfuscated: renderYawOffset and rotationYawHead on 1.8 (v1_8_R3)
                    if (nms.getName().contains("v1_8_R3")) {
                        bodyYaw = field(nms, "aI");
                        headYaw = field(nms, "aK");
                    }
                    if (yaw == null) {
                        broken = true;
                        return;
                    }
                }
                Object handle = getHandle.invoke(entity);
                yaw.setFloat(handle, newYaw);
                if (pitch != null) {
                    pitch.setFloat(handle, newPitch);
                }
                if (bodyYaw != null) {
                    bodyYaw.setFloat(handle, newYaw);
                }
                if (headYaw != null) {
                    headYaw.setFloat(handle, newYaw);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                broken = true;
            }
        }

        private static java.lang.reflect.Field field(Class<?> type, String name) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    if (f.getType() == float.class) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            return null;
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
