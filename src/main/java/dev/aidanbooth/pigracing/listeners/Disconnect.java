package dev.aidanbooth.pigracing.listeners;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.PigRace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class Disconnect implements Listener {
    private Main plugin = Main.getPlugin(Main.class);

    public class InWaiting implements Listener {
        @EventHandler
        private void onDisconnect(PlayerQuitEvent e) {
            PigRace race = plugin.race;
            if(race != null) {
                race.removePlayer(e.getPlayer(), false);
            }
        }
    }
    public class InRace {

    }
}
