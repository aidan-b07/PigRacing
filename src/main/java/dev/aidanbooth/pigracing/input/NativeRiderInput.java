package dev.aidanbooth.pigracing.input;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 1.21.3+: polls {@code Player#getCurrentInput()} through reflection, since the plugin compiles against
 * the 1.8.8 API.
 */
final class NativeRiderInput implements RiderInput {
    private final Method getCurrentInput;
    private final Method isJump;
    private final Method isForward;
    private final java.util.Map<java.util.UUID, Boolean> jumpDown = new java.util.HashMap<>();

    private NativeRiderInput(Method getCurrentInput, Method isJump, Method isForward) {
        this.getCurrentInput = getCurrentInput;
        this.isJump = isJump;
        this.isForward = isForward;
    }

    /** Returns null when this server has no input API. */
    static NativeRiderInput tryCreate() {
        try {
            Method getCurrentInput = Player.class.getMethod("getCurrentInput");
            Class<?> input = getCurrentInput.getReturnType();
            return new NativeRiderInput(getCurrentInput, input.getMethod("isJump"), input.getMethod("isForward"));
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public boolean isJumping(Player player) {
        return read(player, isJump);
    }

    /** Polled once a tick, so a press counts when the key goes from up to down between polls. */
    @Override
    public boolean consumeJumpPress(Player player) {
        boolean down = isJumping(player);
        Boolean before = jumpDown.put(player.getUniqueId(), down);
        return down && (before == null || !before);
    }

    @Override
    public boolean isForward(Player player) {
        return read(player, isForward);
    }

    private boolean read(Player player, Method key) {
        try {
            return (Boolean) key.invoke(getCurrentInput.invoke(player));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public void forget(Player player) {
        jumpDown.remove(player.getUniqueId());
    }

    @Override
    public String name() {
        return "Bukkit input API";
    }

    @Override
    public void shutdown() {
    }
}
