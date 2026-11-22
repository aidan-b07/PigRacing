package dev.aidanbooth.pigracing.listeners.racers;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.utils.RacerUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandCanceller implements Listener {
    private Main plugin = Main.getPlugin(Main.class);

    @EventHandler
    public void onCommandExecuted(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (plugin.race != null) {
            PigRace race = plugin.race;
            if (RacerUtils.playerInRace(player, race.getRacers())) {
                if (!e.getMessage().equalsIgnoreCase("/race leave")) {
                    player.sendMessage(ChatColor.RED + "You cannot run commands as a racer!");
                    player.sendMessage(ChatColor.RESET + "To leave simply type " + ChatColor.BOLD + "/race leave");
                    e.setCancelled(true);
                }
            }
        }
    }
}