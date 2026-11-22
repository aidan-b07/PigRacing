package dev.aidanbooth.pigracing.commands;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.Checkpoint;
import dev.aidanbooth.pigracing.race.Checkpoints;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.race.RaceState;
import dev.aidanbooth.pigracing.race.Region;
import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "pigracing.admin";
    private static final String START = "pigracing.start";
    private static final int MAX_RING_SIZE = 32;

    private static final List<String> PLAYER_COMMANDS = Arrays.asList("help", "join", "leave");
    private static final List<String> ADMIN_COMMANDS = Arrays.asList("start", "forcestart", "stop", "reload",
            "setwaitingloc", "setstartloc", "addcheckpoint", "removecheckpoint", "resetcheckpoints", "preview",
            "pos1", "pos2", "setboundary", "clearboundary");

    private final Main plugin;
    /** Each admin's two selected corners for the race boundary. */
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
            case "addcheckpoint":
                addCheckpoint(sender, args, label);
                return true;
            case "removecheckpoint":
                removeCheckpoint(sender, args, label);
                return true;
            case "resetcheckpoints":
                if (allowed(sender, ADMIN) && trackEditable(sender)) {
                    plugin.getCheckpoints().clear();
                    saveTrack();
                    sender.sendMessage(plugin.message("Messages.Admin.CheckpointsReset"));
                }
                return true;
            case "preview":
                preview(sender);
                return true;
            case "pos1":
                setCorner(sender, 0);
                return true;
            case "pos2":
                setCorner(sender, 1);
                return true;
            case "setboundary":
                setBoundary(sender);
                return true;
            case "clearboundary":
                if (allowed(sender, ADMIN)) {
                    plugin.getConfig().set("Data.Boundary", null);
                    plugin.saveConfig();
                    sender.sendMessage(plugin.message("Messages.Admin.BoundaryCleared"));
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

    /** Changing the rings under a race that's flying would mix up everyone's progress. */
    private boolean trackEditable(CommandSender sender) {
        PigRace race = plugin.race;
        if (race != null && race.getGameState() != RaceState.WAITING) {
            sender.sendMessage(plugin.message("Messages.Admin.TrackInUse"));
            return false;
        }
        return true;
    }

    private void saveTrack() {
        plugin.getCheckpoints().save(plugin.getConfig());
        plugin.saveConfig();
    }

    private void help(CommandSender sender, String label) {
        List<String> lines = new ArrayList<>(plugin.messages("Messages.Help.Header"));
        lines.addAll(plugin.messages("Messages.Help.Player"));
        if (sender.hasPermission(ADMIN)) {
            lines.addAll(plugin.messages("Messages.Help.Admin"));
        }
        lines.addAll(plugin.messages("Messages.Help.Footer"));
        for (String line : lines) {
            sender.sendMessage(Main.center(line.replace("{label}", label)));
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

    /**
     * /race addcheckpoint [size]: a ring where the admin stands, facing where they look. Standing at the
     * bottom-middle of an opening and looking through it makes a ring that fits it exactly.
     */
    private void addCheckpoint(CommandSender sender, String[] args, String label) {
        Player player = adminPlayer(sender);
        if (player == null || !trackEditable(sender)) {
            return;
        }
        int size = plugin.getConfig().getInt("Rings.DefaultSize", 3);
        if (args.length > 1) {
            size = parseInt(args[1], -1);
            if (size < 1 || size > MAX_RING_SIZE) {
                player.sendMessage(plugin.message("Messages.Admin.BadRingSize")
                        .replace("{max}", String.valueOf(MAX_RING_SIZE)).replace("{label}", label));
                return;
            }
        }
        Checkpoint ring = Checkpoint.placedBy(player.getLocation(), size);
        Checkpoints track = plugin.getCheckpoints();
        track.add(ring);
        saveTrack();
        player.sendMessage(plugin.message("Messages.Admin.CheckpointAdded")
                .replace("{number}", String.valueOf(track.size()))
                .replace("{size}", String.valueOf(size))
                .replace("{direction}", direction(ring)));
    }

    private void removeCheckpoint(CommandSender sender, String[] args, String label) {
        if (!allowed(sender, ADMIN) || !trackEditable(sender)) {
            return;
        }
        Checkpoints track = plugin.getCheckpoints();
        int number = args.length > 1 ? parseInt(args[1], -1) : track.size();
        if (!track.remove(number)) {
            sender.sendMessage(plugin.message("Messages.Admin.NoSuchCheckpoint")
                    .replace("{count}", String.valueOf(track.size())).replace("{label}", label));
            return;
        }
        saveTrack();
        sender.sendMessage(plugin.message("Messages.Admin.CheckpointRemoved")
                .replace("{number}", String.valueOf(number)).replace("{count}", String.valueOf(track.size())));
    }

    private void preview(CommandSender sender) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        boolean on = plugin.getRings().togglePreview(player);
        player.sendMessage(plugin.message(on ? "Messages.Admin.PreviewOn" : "Messages.Admin.PreviewOff")
                .replace("{count}", String.valueOf(plugin.getCheckpoints().size())));
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

    /** The boundary is optional: racers leaving it are sent back to their last ring. */
    private void setBoundary(CommandSender sender) {
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
        plugin.getConfig().set("Data.Boundary.Pos1", LocationSerialiser.getLiteStringFromLocation(selection[0]));
        plugin.getConfig().set("Data.Boundary.Pos2", LocationSerialiser.getLiteStringFromLocation(selection[1]));
        plugin.saveConfig();
        selections.remove(player.getUniqueId());
        Region box = Region.between(selection[0], selection[1]);
        player.sendMessage(plugin.message("Messages.Admin.BoundarySet")
                .replace("{size}", box.sizeX() + " x " + box.sizeY() + " x " + box.sizeZ()));
        Location start = LocationSerialiser.parse(plugin.getConfig().getString("Data.StartLocation"));
        if (start != null && !box.contains(start)) {
            player.sendMessage(plugin.message("Messages.Admin.StartOutsideBoundary"));
        }
    }

    /** The compass direction a ring faces, for the confirmation message. */
    private static String direction(Checkpoint ring) {
        if (ring.getPitch() <= -60) {
            return "up";
        }
        if (ring.getPitch() >= 60) {
            return "down";
        }
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int index = (int) Math.round(ring.getYaw() / 45.0) & 7;
        String facing = names[index];
        if (ring.getPitch() < 0) {
            return facing + ", tilted up";
        }
        if (ring.getPitch() > 0) {
            return facing + ", tilted down";
        }
        return facing;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> options;
        if (args.length == 1) {
            options = new ArrayList<>(PLAYER_COMMANDS);
            if (sender.hasPermission(ADMIN)) {
                options.addAll(ADMIN_COMMANDS);
            } else if (sender.hasPermission(START)) {
                options.add("start");
            }
        } else if (args.length == 2 && sender.hasPermission(ADMIN) && args[0].equalsIgnoreCase("addcheckpoint")) {
            options = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8");
        } else if (args.length == 2 && sender.hasPermission(ADMIN) && args[0].equalsIgnoreCase("removecheckpoint")) {
            options = new ArrayList<>();
            for (int i = 1; i <= plugin.getCheckpoints().size(); i++) {
                options.add(String.valueOf(i));
            }
        } else {
            return Collections.emptyList();
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(typed)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
