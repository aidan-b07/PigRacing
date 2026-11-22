package dev.aidanbooth.pigracing.listeners;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.race.Racer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.plugin.EventExecutor;

import java.util.List;
import java.util.Locale;

/**
 * Keeps racers in the race: no dismounting, dropping, picking up, damage or commands, and cleans up
 * when they quit. Checkpoints and crashes are checked by the race's own tick, from the pig's position.
 */
public class RaceListener implements Listener {
    private final Main plugin;

    public RaceListener(Main plugin) {
        this.plugin = plugin;
    }

    private Racer activeRacer(Player player) {
        PigRace race = plugin.race;
        return race == null ? null : race.getRacer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getRings().forget(e.getPlayer());
        if (plugin.race != null) {
            plugin.race.removePlayer(e.getPlayer(), false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDismount(VehicleExitEvent e) {
        if (!(e.getExited() instanceof Player) || !e.getVehicle().hasMetadata(PigRace.PIG_TAG)) {
            return;
        }
        Player player = (Player) e.getExited();
        PigRace race = plugin.race;
        if (race != null && !race.isDismountAllowed(player)) {
            e.setCancelled(true);
            player.sendMessage(plugin.message("Chat.DismountMessage"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity entity = e.getEntity();
        if (entity.hasMetadata(PigRace.PIG_TAG)) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent) {
                knockPig((EntityDamageByEntityEvent) e);
            }
            return;
        }
        if (entity instanceof Player && plugin.getConfig().getBoolean("Settings.ProtectRacers", true)) {
            Racer racer = activeRacer((Player) entity);
            if (racer != null && racer.inGame()) {
                e.setCancelled(true);
            }
        }
    }

    /** Racers can punch each other's pigs off course. */
    private void knockPig(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) e.getDamager();
        Racer attackerRacer = activeRacer(attacker);
        if (attackerRacer == null || !attackerRacer.inGame() || plugin.race == null) {
            return;
        }
        for (Racer target : plugin.race.getRacers()) {
            if (target.getPig() != null && target.getPig().getUniqueId().equals(e.getEntity().getUniqueId())) {
                if (target == attackerRacer) {
                    // Your own pig: riders click it all the time by accident
                    return;
                }
                double strength = plugin.getConfig().getDouble("Flight.KnockbackStrength", 1.0);
                target.addKnockback(attacker.getLocation().getDirection().setY(0).normalize().multiply(strength));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (activeRacer(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Racer racer = activeRacer(e.getPlayer());
        if (racer == null) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        // The plugin's own command always works, so racers can always leave
        PluginCommand own = plugin.getCommand("race");
        if (own != null && (own.getName().equalsIgnoreCase(label) || containsIgnoreCase(own.getAliases(), label))) {
            return;
        }
        // Racers on a pig follow the racing rules; everyone else in the race (waiting, or finished)
        // follows the waiting area's
        String path = racer.inGame() ? "Commands.Racing" : "Commands.Waiting";
        boolean whitelist = !"blacklist".equalsIgnoreCase(plugin.getConfig().getString(path + ".Mode", "whitelist"));
        boolean listed = containsIgnoreCase(plugin.getConfig().getStringList(path + ".List"), label);
        if (listed == whitelist) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().sendMessage(plugin.message("Chat.CommandBlocked"));
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Item pickups moved from PlayerPickupItemEvent to EntityPickupItemEvent in 1.12, and the old one
     * may be gone on newer servers, so whichever exists is registered by name.
     */
    @SuppressWarnings("unchecked")
    public static void registerPickupBlocker(Main plugin) {
        Class<? extends Event> type = null;
        for (String name : new String[]{"org.bukkit.event.entity.EntityPickupItemEvent", "org.bukkit.event.player.PlayerPickupItemEvent"}) {
            try {
                type = (Class<? extends Event>) Class.forName(name);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (type == null) {
            return;
        }
        Class<? extends Event> eventType = type;
        EventExecutor executor = (listener, event) -> {
            if (!eventType.isInstance(event) || plugin.race == null) {
                return;
            }
            Entity who = event instanceof PlayerEvent ? ((PlayerEvent) event).getPlayer()
                    : event instanceof EntityEvent ? ((EntityEvent) event).getEntity() : null;
            if (who instanceof Player && plugin.race.isRacer((Player) who)) {
                ((Cancellable) event).setCancelled(true);
            }
        };
        plugin.getServer().getPluginManager().registerEvent(type, new Listener() {
        }, EventPriority.NORMAL, executor, plugin, true);
    }
}
