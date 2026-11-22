package dev.aidanbooth.pigracing.listeners.racers;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.utils.RacerUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public class DropCanceller implements Listener {
    private Main plugin = Main.getPlugin(Main.class);

    @EventHandler
    private void onPickup(PlayerDropItemEvent e) {
        if(plugin.race != null) {
            PigRace race = plugin.race;
            if (RacerUtils.playerInRace(e.getPlayer(), race.getRacers())) {
                e.setCancelled(true);
            }
        }
    }
}
