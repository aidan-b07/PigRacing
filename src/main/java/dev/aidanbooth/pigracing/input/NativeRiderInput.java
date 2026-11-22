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
    }

    @Override
    public String name() {
        return "Bukkit input API";
    }

    @Override
    public void shutdown() {
    }
}
