package dev.aidanbooth.pigracing.input;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Before 1.21.3: reads the keys from the packet the client sends every tick while riding
 * (STEER_VEHICLE, or PLAYER_INPUT on 1.21.2), using the bundled PacketEvents.
 *
 * Only touch this class when {@link NativeRiderInput} is unavailable, so PacketEvents never starts on
 * servers that don't need it.
 */
final class PacketRiderInput extends PacketListenerAbstract implements RiderInput {
    private static final int JUMP = 1;
    private static final int FORWARD = 2;

    /** Packets arrive on network threads, the race reads on the main thread. */
    private final Map<UUID, Integer> keys = new ConcurrentHashMap<>();

    private PacketRiderInput() {
        super(PacketListenerPriority.MONITOR);
    }

    /** Called from onLoad: PacketEvents has to load before the server finishes starting. */
    static void load(Plugin plugin) {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .bStats(false)
                .debug(false);
        PacketEvents.getAPI().load();
    }

    /** Called from onEnable. */
    static PacketRiderInput start() {
        PacketRiderInput input = new PacketRiderInput();
        PacketEvents.getAPI().getEventManager().registerListener(input);
        PacketEvents.getAPI().init();
        return input;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        int pressed;
        if (type == PacketType.Play.Client.STEER_VEHICLE) {
            WrapperPlayClientSteerVehicle packet = new WrapperPlayClientSteerVehicle(event);
            pressed = (packet.isJump() ? JUMP : 0) | (packet.getForward() > 0 ? FORWARD : 0);
        } else if (type == PacketType.Play.Client.PLAYER_INPUT) {
            WrapperPlayClientPlayerInput packet = new WrapperPlayClientPlayerInput(event);
            pressed = (packet.isJump() ? JUMP : 0) | (packet.isForward() ? FORWARD : 0);
        } else {
            return;
        }
        UUID id = event.getUser().getUUID();
        if (id != null) {
            keys.put(id, pressed);
        }
    }

    @Override
    public boolean isJumping(Player player) {
        return (keys.getOrDefault(player.getUniqueId(), 0) & JUMP) != 0;
    }

    @Override
    public boolean isForward(Player player) {
        return (keys.getOrDefault(player.getUniqueId(), 0) & FORWARD) != 0;
    }

    @Override
    public void forget(Player player) {
        keys.remove(player.getUniqueId());
    }

    @Override
    public String name() {
        return "PacketEvents";
    }

    @Override
    public void shutdown() {
        keys.clear();
        PacketEvents.getAPI().terminate();
    }
}
