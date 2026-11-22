package dev.aidanbooth.pigracing.commands;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "pigracing.admin";
    private static final String START = "pigracing.start";

    private static final List<String> PLAYER_COMMANDS = Arrays.asList("help", "join", "leave");
    private static final List<String> ADMIN_COMMANDS = Arrays.asList("start", "forcestart", "stop", "reload",
            "setwaitingloc", "setstartloc", "pos1", "pos2", "addcheckpoint", "resetcheckpoints");

    private final Main plugin;
    /** Each admin's two selected corners for the next checkpoint. */
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public MainCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join":
                join(sender);
                return true;
            case "leave":
                leave(sender);
                return true;
            case "start":
                if (allowed(sender, START)) {
                    start(sender);
                }
                return true;
            case "forcestart":
                if (allowed(sender, ADMIN)) {
                    forceStart(sender);
                }
                return true;
            case "stop":
                if (allowed(sender, ADMIN)) {
                    stop(sender);
                }
                return true;
            case "reload":
                if (allowed(sender, ADMIN)) {
                    plugin.reload();
                    sender.sendMessage(plugin.message("Messages.Admin.Reloaded"));
                }
                return true;
            case "setwaitingloc":
                setLocation(sender, "Data.WaitingLocation");
                return true;
            case "setstartloc":
                setLocation(sender, "Data.StartLocation");
                return true;
            case "pos1":
                setCorner(sender, 0);
                return true;
            case "pos2":
                setCorner(sender, 1);
                return true;
            case "addcheckpoint":
                addCheckpoint(sender);
                return true;
            case "resetcheckpoints":
                if (allowed(sender, ADMIN)) {
                    plugin.getConfig().set("Data.Checkpoints", null);
                    plugin.saveConfig();
                    plugin.getCheckpoints().load(plugin.getConfig());
                    sender.sendMessage(plugin.message("Messages.Admin.CheckpointsReset"));
                }
                return true;
            default:
                help(sender, label);
                return true;
        }
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(plugin.message("Messages.NoPermission"));
        return false;
    }

    private Player adminPlayer(CommandSender sender) {
        if (!allowed(sender, ADMIN)) {
            return null;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Messages.PlayersOnly"));
            return null;
        }
        return (Player) sender;
    }

    private void help(CommandSender sender, String label) {
        for (String line : plugin.messages("Messages.Help.Player")) {
            sender.sendMessage(line.replace("{label}", label));
        }
        if (sender.hasPermission(ADMIN)) {
            for (String line : plugin.messages("Messages.Help.Admin")) {
                sender.sendMessage(line.replace("{label}", label));
            }
        }
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Messages.PlayersOnly"));
            return;
        }
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Messages.NoCurrentRace"));
            return;
        }
        plugin.race.addPlayer((Player) sender);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Messages.PlayersOnly"));
            return;
        }
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Messages.NoCurrentRace"));
            return;
        }
        plugin.race.removePlayer((Player) sender, true);
    }

    private void start(CommandSender sender) {
        if (plugin.race != null) {
            sender.sendMessage(plugin.message("Messages.Admin.RaceAlreadyRunning"));
            return;
        }
        String problem = PigRace.validate(plugin);
        if (problem != null) {
            sender.sendMessage(plugin.message("Messages.Admin.CannotStart").replace("{reason}", problem));
            return;
        }
        new PigRace(plugin);
    }

    private void forceStart(CommandSender sender) {
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Messages.Admin.NothingToForceStart"));
            return;
        }
        int racers = plugin.race.getRacers().size();
        if (!plugin.race.forceStart()) {
            sender.sendMessage(plugin.message("Messages.Admin.AlreadyUnderway"));
            return;
        }
        sender.sendMessage(plugin.message(racers == 0 ? "Messages.Admin.ForceStartedEmpty" : "Messages.Admin.ForceStarted")
                .replace("{players}", String.valueOf(racers)));
    }

    private void stop(CommandSender sender) {
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Messages.NoCurrentRace"));
            return;
        }
        plugin.race.stop();
        sender.sendMessage(plugin.message("Messages.Admin.RaceStopped"));
    }

    private void setLocation(CommandSender sender, String path) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        plugin.getConfig().set(path, LocationSerialiser.getStringFromLocation(player.getLocation()));
        plugin.saveConfig();
        player.sendMessage(plugin.message("Messages.Admin.LocationSet"));
    }

    private void setCorner(CommandSender sender, int corner) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        Location[] selection = selections.computeIfAbsent(player.getUniqueId(), id -> new Location[2]);
        selection[corner] = player.getLocation().getBlock().getLocation();
        player.sendMessage(plugin.message("Messages.Admin.CornerSet")
                .replace("{corner}", String.valueOf(corner + 1))
                .replace("{position}", selection[corner].getBlockX() + ", " + selection[corner].getBlockY() + ", " + selection[corner].getBlockZ()));
    }

    private void addCheckpoint(CommandSender sender) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        Location[] selection = selections.get(player.getUniqueId());
        if (selection == null || selection[0] == null || selection[1] == null) {
            player.sendMessage(plugin.message("Messages.Admin.NeedSelection"));
            return;
        }
        if (selection[0].getWorld() != selection[1].getWorld()) {
            player.sendMessage(plugin.message("Messages.Admin.SelectionWorlds"));
            return;
        }
        ConfigurationSection existing = plugin.getConfig().getConfigurationSection("Data.Checkpoints");
        int number = existing == null ? 1 : existing.getKeys(false).size() + 1;
        plugin.getConfig().set("Data.Checkpoints." + number + ".Loc1", LocationSerialiser.getLiteStringFromLocation(selection[0]));
        plugin.getConfig().set("Data.Checkpoints." + number + ".Loc2", LocationSerialiser.getLiteStringFromLocation(selection[1]));
        plugin.saveConfig();
        plugin.getCheckpoints().load(plugin.getConfig());
        selections.remove(player.getUniqueId());
        player.sendMessage(plugin.message("Messages.Admin.CheckpointAdded").replace("{number}", String.valueOf(number)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> matches = new ArrayList<>();
        if (args.length != 1) {
            return matches;
        }
        List<String> options = new ArrayList<>(PLAYER_COMMANDS);
        if (sender.hasPermission(ADMIN)) {
            options.addAll(ADMIN_COMMANDS);
        } else if (sender.hasPermission(START)) {
            options.add("start");
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(typed)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
