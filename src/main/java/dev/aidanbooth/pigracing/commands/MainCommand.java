package dev.aidanbooth.pigracing.commands;

import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.race.Checkpoint;
import dev.aidanbooth.pigracing.race.Checkpoints;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.race.RaceState;
import dev.aidanbooth.pigracing.race.Region;
import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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

/**
 * /race and its subcommands:
 * <pre>
 * join, leave, help                        everyone
 * start                                    pigracing.start
 * forcestart, stop, reload                 pigracing.admin
 * setlobby, setstart                       pigracing.admin, where you stand
 * ring add [size] | remove [n] | list | tp &lt;n&gt; | clear
 * preview                                  show the rings with no race on
 * boundary pos1 | pos2 | set | clear       optional box racers must stay in
 * </pre>
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "pigracing.admin";
    private static final String START = "pigracing.start";
    private static final int MAX_RING_SIZE = 32;

    private static final List<String> PLAYER_COMMANDS = Arrays.asList("help", "join", "leave");
    private static final List<String> ADMIN_COMMANDS = Arrays.asList("start", "forcestart", "stop", "reload",
            "setlobby", "setstart", "ring", "preview", "boundary");
    private static final List<String> RING_COMMANDS = Arrays.asList("add", "remove", "list", "tp", "clear");
    private static final List<String> BOUNDARY_COMMANDS = Arrays.asList("pos1", "pos2", "set", "clear");

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
                    sender.sendMessage(plugin.message("Chat.Admin.Reloaded"));
                }
                return true;
            case "setlobby":
                setLocation(sender, "Lobby", "Chat.Admin.LobbySet");
                return true;
            case "setstart":
                setLocation(sender, "Start", "Chat.Admin.StartSet");
                return true;
            case "ring":
                ring(sender, args, label);
                return true;
            case "preview":
                preview(sender);
                return true;
            case "boundary":
                boundary(sender, args, label);
                return true;
            default:
                help(sender, label);
                return true;
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(plugin.message("Chat.NoPermission"));
        return false;
    }

    private Player adminPlayer(CommandSender sender) {
        if (!allowed(sender, ADMIN)) {
            return null;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Chat.PlayersOnly"));
            return null;
        }
        return (Player) sender;
    }

    private void usage(CommandSender sender, String label, String usage) {
        sender.sendMessage(plugin.message("Chat.Admin.Usage").replace("{usage}", "/" + label + " " + usage));
    }

    /** Changing the rings under a race that's flying would mix up everyone's progress. */
    private boolean trackEditable(CommandSender sender) {
        PigRace race = plugin.race;
        if (race != null && race.getGameState() != RaceState.WAITING) {
            sender.sendMessage(plugin.message("Chat.Admin.TrackInUse"));
            return false;
        }
        return true;
    }

    private void saveRings() {
        plugin.getCheckpoints().save(plugin.track());
        plugin.saveTrack();
    }

    private void help(CommandSender sender, String label) {
        List<String> lines = new ArrayList<>(plugin.messages("Chat.Help.Header"));
        lines.addAll(plugin.messages("Chat.Help.Player"));
        if (sender.hasPermission(ADMIN)) {
            lines.addAll(plugin.messages("Chat.Help.Admin"));
        }
        lines.addAll(plugin.messages("Chat.Help.Footer"));
        for (String line : lines) {
            sender.sendMessage(Main.center(line.replace("{label}", label)));
        }
    }

    // ------------------------------------------------------------------ playing

    private void join(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Chat.PlayersOnly"));
            return;
        }
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Chat.NoCurrentRace"));
            return;
        }
        plugin.race.addPlayer((Player) sender);
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.message("Chat.PlayersOnly"));
            return;
        }
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Chat.NoCurrentRace"));
            return;
        }
        plugin.race.removePlayer((Player) sender, true);
    }

    // ------------------------------------------------------------------ running races

    private void start(CommandSender sender) {
        if (plugin.race != null) {
            sender.sendMessage(plugin.message("Chat.Admin.RaceAlreadyRunning"));
            return;
        }
        String problem = PigRace.validate(plugin);
        if (problem != null) {
            sender.sendMessage(plugin.message("Chat.Admin.CannotStart").replace("{reason}", problem));
            return;
        }
        new PigRace(plugin);
    }

    private void forceStart(CommandSender sender) {
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Chat.Admin.NothingToForceStart"));
            return;
        }
        int racers = plugin.race.getRacers().size();
        if (!plugin.race.forceStart()) {
            sender.sendMessage(plugin.message("Chat.Admin.AlreadyUnderway"));
            return;
        }
        sender.sendMessage(plugin.message(racers == 0 ? "Chat.Admin.ForceStartedEmpty" : "Chat.Admin.ForceStarted")
                .replace("{players}", String.valueOf(racers)));
    }

    private void stop(CommandSender sender) {
        if (plugin.race == null) {
            sender.sendMessage(plugin.message("Chat.NoCurrentRace"));
            return;
        }
        plugin.race.stop();
        sender.sendMessage(plugin.message("Chat.Admin.RaceStopped"));
    }

    // ------------------------------------------------------------------ building the track

    private void setLocation(CommandSender sender, String path, String done) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        plugin.track().set(path, LocationSerialiser.getStringFromLocation(player.getLocation()));
        plugin.saveTrack();
        player.sendMessage(plugin.message(done));
    }

    private void ring(CommandSender sender, String[] args, String label) {
        if (!allowed(sender, ADMIN)) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "add":
                addRing(sender, args, label);
                return;
            case "remove":
                removeRing(sender, args, label);
                return;
            case "list":
                listRings(sender, label);
                return;
            case "tp":
                teleportToRing(sender, args, label);
                return;
            case "clear":
                if (trackEditable(sender)) {
                    plugin.getCheckpoints().clear();
                    saveRings();
                    sender.sendMessage(plugin.message("Chat.Admin.RingsCleared"));
                }
                return;
            default:
                usage(sender, label, "ring <add [size] | remove [number] | list | tp <number> | clear>");
        }
    }

    /**
     * A ring where the admin stands, facing where they look. Standing at the bottom-middle of an
     * opening and looking through it makes a ring that fits it exactly.
     */
    private void addRing(CommandSender sender, String[] args, String label) {
        Player player = adminPlayer(sender);
        if (player == null || !trackEditable(sender)) {
            return;
        }
        int size = plugin.getConfig().getInt("Rings.DefaultSize", 3);
        if (args.length > 2) {
            size = parseInt(args[2], -1);
            if (size < 1 || size > MAX_RING_SIZE) {
                player.sendMessage(plugin.message("Chat.Admin.BadRingSize")
                        .replace("{max}", String.valueOf(MAX_RING_SIZE)).replace("{label}", label));
                return;
            }
        }
        Checkpoint ring = Checkpoint.placedBy(player.getLocation(), size);
        Checkpoints track = plugin.getCheckpoints();
        track.add(ring);
        saveRings();
        player.sendMessage(plugin.message("Chat.Admin.RingAdded")
                .replace("{number}", String.valueOf(track.size()))
                .replace("{size}", String.valueOf(size))
                .replace("{direction}", direction(ring)));
    }

    private void removeRing(CommandSender sender, String[] args, String label) {
        if (!trackEditable(sender)) {
            return;
        }
        Checkpoints track = plugin.getCheckpoints();
        int number = args.length > 2 ? parseInt(args[2], -1) : track.size();
        if (!track.remove(number)) {
            sender.sendMessage(plugin.message("Chat.Admin.NoSuchRing")
                    .replace("{count}", String.valueOf(track.size())).replace("{label}", label));
            return;
        }
        saveRings();
        sender.sendMessage(plugin.message("Chat.Admin.RingRemoved")
                .replace("{number}", String.valueOf(number)).replace("{count}", String.valueOf(track.size())));
    }

    /** One line per ring; players can click a line to go to that ring. */
    private void listRings(CommandSender sender, String label) {
        Checkpoints track = plugin.getCheckpoints();
        if (track.size() == 0) {
            sender.sendMessage(plugin.message("Chat.Admin.RingListEmpty").replace("{label}", label));
            return;
        }
        sender.sendMessage(plugin.message("Chat.Admin.RingListHeader").replace("{count}", String.valueOf(track.size())));
        String format = plugin.message("Chat.Admin.RingListLine");
        String hover = plugin.message("Chat.Admin.RingListHover");
        for (int i = 1; i <= track.size(); i++) {
            Checkpoint ring = track.get(i);
            Location c = ring.center();
            String where = c == null ? ring.getWorldName() + " (not loaded)"
                    : String.format(Locale.ROOT, "%.1f, %.1f, %.1f", c.getX(), c.getY(), c.getZ());
            String line = format.replace("{number}", String.valueOf(i))
                    .replace("{size}", String.valueOf((int) ring.getSize()))
                    .replace("{location}", where)
                    .replace("{direction}", direction(ring));
            if (sender instanceof Player) {
                TextComponent text = new TextComponent(TextComponent.fromLegacyText(line));
                text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + label + " ring tp " + i));
                text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        TextComponent.fromLegacyText(hover.replace("{number}", String.valueOf(i)))));
                ((Player) sender).spigot().sendMessage(text);
            } else {
                sender.sendMessage(line);
            }
        }
    }

    /** To where the ring would have been placed from: the bottom-middle of it, facing through. */
    private void teleportToRing(CommandSender sender, String[] args, String label) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        Checkpoint ring = args.length > 2 ? plugin.getCheckpoints().get(parseInt(args[2], -1)) : null;
        if (ring == null) {
            if (args.length > 2) {
                player.sendMessage(plugin.message("Chat.Admin.NoSuchRing")
                        .replace("{count}", String.valueOf(plugin.getCheckpoints().size())).replace("{label}", label));
            } else {
                usage(sender, label, "ring tp <number>");
            }
            return;
        }
        Location at = ring.center();
        if (at == null) {
            player.sendMessage(plugin.message("Chat.Admin.RingWorldNotLoaded"));
            return;
        }
        at.subtract(0, ring.getRadius(), 0);
        at.setPitch(0);
        player.teleport(at);
        player.sendMessage(plugin.message("Chat.Admin.RingTeleported").replace("{number}", args[2]));
    }

    private void preview(CommandSender sender) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        boolean on = plugin.getRings().togglePreview(player);
        player.sendMessage(plugin.message(on ? "Chat.Admin.PreviewOn" : "Chat.Admin.PreviewOff")
                .replace("{count}", String.valueOf(plugin.getCheckpoints().size())));
    }

    /** The boundary is optional: racers leaving it are sent back to their last ring. */
    private void boundary(CommandSender sender, String[] args, String label) {
        if (!allowed(sender, ADMIN)) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "pos1":
                setCorner(sender, 0);
                return;
            case "pos2":
                setCorner(sender, 1);
                return;
            case "set":
                setBoundary(sender);
                return;
            case "clear":
                plugin.track().set("Boundary", null);
                plugin.saveTrack();
                sender.sendMessage(plugin.message("Chat.Admin.BoundaryCleared"));
                return;
            default:
                usage(sender, label, "boundary <pos1 | pos2 | set | clear>");
        }
    }

    private void setCorner(CommandSender sender, int corner) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        Location[] selection = selections.computeIfAbsent(player.getUniqueId(), id -> new Location[2]);
        selection[corner] = player.getLocation().getBlock().getLocation();
        player.sendMessage(plugin.message("Chat.Admin.CornerSet")
                .replace("{corner}", String.valueOf(corner + 1))
                .replace("{position}", selection[corner].getBlockX() + ", " + selection[corner].getBlockY() + ", " + selection[corner].getBlockZ()));
    }

    private void setBoundary(CommandSender sender) {
        Player player = adminPlayer(sender);
        if (player == null) {
            return;
        }
        Location[] selection = selections.get(player.getUniqueId());
        if (selection == null || selection[0] == null || selection[1] == null) {
            player.sendMessage(plugin.message("Chat.Admin.NeedSelection"));
            return;
        }
        if (selection[0].getWorld() != selection[1].getWorld()) {
            player.sendMessage(plugin.message("Chat.Admin.SelectionWorlds"));
            return;
        }
        plugin.track().set("Boundary.Pos1", LocationSerialiser.getLiteStringFromLocation(selection[0]));
        plugin.track().set("Boundary.Pos2", LocationSerialiser.getLiteStringFromLocation(selection[1]));
        plugin.saveTrack();
        selections.remove(player.getUniqueId());
        Region box = Region.between(selection[0], selection[1]);
        player.sendMessage(plugin.message("Chat.Admin.BoundarySet")
                .replace("{size}", box.sizeX() + " x " + box.sizeY() + " x " + box.sizeZ()));
        Location start = LocationSerialiser.parse(plugin.track().getString("Start"));
        if (start != null && !box.contains(start)) {
            player.sendMessage(plugin.message("Chat.Admin.StartOutsideBoundary"));
        }
    }

    /** The compass direction a ring faces, for messages. */
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
        boolean admin = sender.hasPermission(ADMIN);
        List<String> options;
        if (args.length == 1) {
            options = new ArrayList<>(PLAYER_COMMANDS);
            if (admin) {
                options.addAll(ADMIN_COMMANDS);
            } else if (sender.hasPermission(START)) {
                options.add("start");
            }
        } else if (!admin) {
            return Collections.emptyList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ring")) {
            options = RING_COMMANDS;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boundary")) {
            options = BOUNDARY_COMMANDS;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ring") && args[1].equalsIgnoreCase("add")) {
            options = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ring")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("tp"))) {
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
