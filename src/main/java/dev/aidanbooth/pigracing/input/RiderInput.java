package dev.aidanbooth.pigracing.input;

import org.bukkit.entity.Player;

/**
 * Reads the movement keys of a player riding a pig.
 *
 * Bukkit only exposes these from 1.21.3 ({@code Player#getCurrentInput}). Older servers read them from
 * the vehicle-steer packet instead, see {@link PacketRiderInput}.
 */
public interface RiderInput {

    /** Whether the player is holding the jump key (space). */
    boolean isJumping(Player player);

    /** Whether the player is holding the forward key (W). */
    boolean isForward(Player player);

    /** Forgets anything stored for a player who left. */
    void forget(Player player);

    /** Short name shown in the startup log. */
    String name();

    void shutdown();
}
