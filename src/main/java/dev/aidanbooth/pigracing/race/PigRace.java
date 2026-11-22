package dev.aidanbooth.pigracing.race;

import com.cryptomorin.xseries.messages.ActionBar;
import com.cryptomorin.xseries.messages.Titles;
import dev.aidanbooth.pigracing.Main;
import dev.aidanbooth.pigracing.compat.Entities;
import dev.aidanbooth.pigracing.compat.Potions;
import dev.aidanbooth.pigracing.compat.Sounds;
import dev.aidanbooth.pigracing.listeners.checkpoint.CheckpointPassEvent;
import dev.aidanbooth.pigracing.utils.LocationSerialiser;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public class PigRace {
    public static final String PIG_TAG = "pigrace";

    private final Main plugin;
    private final FileConfiguration config;
    private final Checkpoints checkpoints;

    private final List<Racer> racers = new ArrayList<>();
    private final List<Racer> completedRacers = new ArrayList<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    /** Players this plugin is moving off their pig on purpose, so the dismount blocker lets it through. */
    private final Set<UUID> allowedDismounts = new HashSet<>();

    private final Location waitLocation;
    private final Location startLocation;
    private final int laps;
    /** Optional box racers must stay inside, or null to use the distance from the start. */
    private final Region boundary;
    private RaceState raceState = RaceState.WAITING;
    private BukkitTask gathering;
    /** Seconds until joining closes, while waiting. */
    private int secondsLeft;

    /** Checks a race can run here. Returns a reason it can't, or null if it can. */
    public static String validate(Main plugin) {
        FileConfiguration config = plugin.getConfig();
        if (LocationSerialiser.parse(config.getString("Data.WaitingLocation")) == null) {
            return "The waiting location isn't set, or its world isn't loaded. Use /race setwaitingloc.";
        }
        if (LocationSerialiser.parse(config.getString("Data.StartLocation")) == null) {
            return "The start location isn't set, or its world isn't loaded. Use /race setstartloc.";
        }
        if (plugin.getCheckpoints().size() == 0) {
            return "The track has no checkpoints. Use /race addcheckpoint <size>.";
        }
        return null;
    }

    public PigRace(Main plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.checkpoints = plugin.getCheckpoints();
        this.waitLocation = LocationSerialiser.parse(config.getString("Data.WaitingLocation"));
        this.startLocation = LocationSerialiser.parse(config.getString("Data.StartLocation"));
        this.laps = Math.max(1, config.getInt("Settings.Laps", 3));
        this.boundary = Region.parse(config.getString("Data.Boundary.Pos1"), config.getString("Data.Boundary.Pos2"));

        plugin.race = this;
        startGathering();
        repeat(this::updateActionbars, 20, 20);
    }

    public RaceState getGameState() {
        return raceState;
    }

    public List<Racer> getRacers() {
        return racers;
    }

    public int getLaps() {
        return laps;
    }

    public Racer getRacer(Player player) {
        for (Racer r : racers) {
            if (r.is(player)) {
                return r;
            }
        }
        return null;
    }

    public boolean isRacer(Player player) {
        return getRacer(player) != null;
    }

    public boolean isDismountAllowed(Player player) {
        Racer racer = getRacer(player);
        return racer == null || !racer.inGame() || allowedDismounts.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------ lifecycle

    private void startGathering() {
        int joinTime = Math.max(1, config.getInt("Settings.JoinTime", 60));
        List<Integer> announceAt = config.getIntegerList("Settings.AnnounceAt");
        secondsLeft = joinTime;
        gathering = new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    cancel();
                    startRace(false);
                    return;
                }
                if (secondsLeft == joinTime || announceAt.contains(secondsLeft)) {
                    announceJoin(secondsLeft);
                }
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0, 20);
        tasks.add(gathering);
    }

    public int getMinPlayers() {
        return Math.max(1, config.getInt("Settings.MinPlayers", 2));
    }

    /**
     * Closes joining now and starts with whoever has joined, ignoring the minimum. Returns false if
     * the race is past joining already.
     */
    public boolean forceStart() {
        if (raceState != RaceState.WAITING) {
            return false;
        }
        if (gathering != null) {
            gathering.cancel();
        }
        startRace(true);
        return true;
    }

    private void announceJoin(int seconds) {
        String hover = plugin.message("Messages.JoinHover");
        for (String line : plugin.messages("Messages.JoinRace")) {
            BaseComponent[] text = TextComponent.fromLegacyText(line.replace("{time}", String.valueOf(seconds)));
            TextComponent component = new TextComponent(text);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/race join"));
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(hover)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.spigot().sendMessage(component);
            }
        }
    }

    private void startRace(boolean forced) {
        if (racers.isEmpty()) {
            Bukkit.broadcastMessage(plugin.message("Messages.RaceVoided"));
            stop();
            return;
        }
        int minPlayers = getMinPlayers();
        if (!forced && racers.size() < minPlayers) {
            for (Racer r : racers) {
                r.getPlayer().sendMessage(plugin.message("Messages.NotEnoughPlayers").replace("{min}", String.valueOf(minPlayers)));
            }
            Bukkit.broadcastMessage(plugin.message("Messages.RaceCancelled").replace("{min}", String.valueOf(minPlayers)));
            stop();
            return;
        }
        raceState = RaceState.STARTING;

        for (Racer r : racers) {
            Player player = r.getPlayer();
            player.teleport(startLocation);

            Pig pig = startLocation.getWorld().spawn(startLocation, Pig.class);
            pig.setSaddle(true);
            pig.setRemoveWhenFarAway(false);
            pig.setMetadata(PIG_TAG, new FixedMetadataValue(plugin, true));
            Entities.notPersistent(pig);
            // The pig never walks by itself: it only moves when flown, so it stays put until GO and
            // can't wander off while it's on the ground
            Potions.freeze(pig, Integer.MAX_VALUE);
            Entities.face(pig, startLocation.getYaw(), 0);
            Entities.mount(pig, player);

            r.setPig(pig);
            r.setInGame(true);
        }

        startCountdown();
    }

    private TreeMap<Integer, String> countdownTitles() {
        TreeMap<Integer, String> titles = new TreeMap<>();
        ConfigurationSection section = config.getConfigurationSection("Titles.Countdown");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    titles.put(Integer.parseInt(key), Main.color(section.getString(key)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return titles;
    }

    private int countdownLength() {
        TreeMap<Integer, String> titles = countdownTitles();
        return titles.isEmpty() ? 0 : titles.lastKey();
    }

    private void startCountdown() {
        TreeMap<Integer, String> titles = countdownTitles();
        tasks.add(new BukkitRunnable() {
            int counter = titles.isEmpty() ? 0 : titles.lastKey();

            @Override
            public void run() {
                if (counter > 0) {
                    String title = titles.get(counter);
                    for (Racer r : racers) {
                        if (title != null) {
                            Titles.sendTitle(r.getPlayer(), 5, 10, 5, title, "");
                        }
                        Sounds.play(r.getPlayer(), config, "Sounds.Countdown");
                    }
                    counter--;
                    return;
                }
                cancel();
                String go = plugin.message("Titles.Go");
                for (Racer r : racers) {
                    Titles.sendTitle(r.getPlayer(), 5, 10, 5, go, "");
                    Sounds.play(r.getPlayer(), config, "Sounds.Go");
                }
                raceState = RaceState.INPROGRESS;
                repeat(PigRace.this::flyPigs, 1, 1);
                repeat(PigRace.this::checkTooFar, 5, 5);
            }
        }.runTaskTimer(plugin, 20, 20));
    }

    /** Ends the race: cancels its tasks, removes pigs and sends racers back to the waiting area. */
    public void stop() {
        raceState = RaceState.ENDED;
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (Racer r : new ArrayList<>(racers)) {
            releaseRacer(r, true);
        }
        racers.clear();
        if (plugin.race == this) {
            plugin.race = null;
        }
    }

    private void endWhenEveryoneFinished() {
        if (raceState == RaceState.ENDED) {
            return;
        }
        for (Racer r : racers) {
            if (r.inGame()) {
                return;
            }
        }
        if (completedRacers.isEmpty()) {
            // Everyone left before anyone finished: nothing to celebrate
            Bukkit.broadcastMessage(plugin.message("Messages.RaceVoided"));
            stop();
            return;
        }
        raceState = RaceState.ENDED;
        Bukkit.broadcastMessage(plugin.message("Messages.RaceOver"));
        Bukkit.getScheduler().runTaskLater(plugin, this::stop, 20L * Math.max(0, config.getInt("Settings.EndDelay", 5)));
    }

    // ------------------------------------------------------------------ ticking

    private void repeat(Runnable action, long delay, long period) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, action, delay, period));
    }

    private void flyPigs() {
        // Copy: finishing a lap can take a racer out of the list
        for (Racer r : new ArrayList<>(racers)) {
            if (!r.inGame() || r.getPig() == null || !r.getPig().isValid()) {
                continue;
            }
            checkProgress(r);
            if (r.inGame()) {
                plugin.getFlight().tick(r);
            }
        }
    }

    /** Checkpoints and crashes, from where the pig moved since last tick. */
    private void checkProgress(Racer r) {
        Location now = r.getPig().getLocation();
        Location before = r.getLastPigLocation();
        r.setLastPigLocation(now);

        Checkpoint next = checkpoints.get(nextCheckpoint(r));
        if (next != null && next.crossedBy(bodyCentre(before), bodyCentre(now))) {
            passedCheckpoint(r);
            return;
        }

        // Sitting at the start or a respawn point is fine for a moment; after that, or once the pig
        // has flown, touching anything but safe ground is a crash
        if (!r.getPig().isOnGround()) {
            r.setAirborne(true);
            return;
        }
        int grace = config.getInt("Settings.GroundGraceTicks", 60);
        if ((r.isAirborne() || r.addGroundTick() > grace) && !isSafeGround(now)) {
            crashed(r);
        }
    }

    /** The middle of a pig's body, from where it stands. Pigs are 0.9 blocks tall. */
    private static Location bodyCentre(Location feet) {
        return feet == null ? null : feet.clone().add(0, 0.45, 0);
    }

    /**
     * The ring a racer has to fly through next. Ring 1 is the start/finish line: they start behind it,
     * and after the last ring it's ring 1 again, which completes the lap.
     */
    public int nextCheckpoint(Racer racer) {
        int passed = racer.getCheckpoint();
        return passed >= checkpoints.size() ? 1 : passed + 1;
    }

    /** Blocks a pig may land on without crashing (carpet by default), matched by name with * wildcards. */
    private boolean isSafeGround(Location at) {
        List<String> safe = config.getStringList("Settings.SafeGroundBlocks");
        String feet = at.getBlock().getType().name();
        String below = at.clone().subtract(0, 0.1, 0).getBlock().getType().name();
        for (String pattern : safe) {
            String regex = pattern.toUpperCase(java.util.Locale.ROOT).replace("*", ".*");
            if (feet.matches(regex) || below.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private void updateActionbars() {
        String waiting = plugin.message("Actionbar.Waiting");
        String inProgress = plugin.message("Actionbar.InProgress");
        String completed = plugin.message("Actionbar.RaceCompleted");
        for (Racer r : racers) {
            String text;
            if (raceState == RaceState.WAITING) {
                text = waiting.replace("{players}", String.valueOf(racers.size()));
            } else if (!r.inGame() && completedRacers.contains(r)) {
                text = completed.replace("{position}", String.valueOf(getRacerPosition(r)));
            } else {
                text = inProgress.replace("{laps}", String.valueOf(r.getLap())).replace("{total}", String.valueOf(laps));
            }
            ActionBar.sendActionBar(r.getPlayer(), text);
        }
    }

    /**
     * Racers who leave the track go back to their last ring. With a boundary set that's the boundary
     * box; without one, a distance from the start (0 turns it off).
     */
    private void checkTooFar() {
        double max = config.getDouble("Settings.MaxDistanceFromStart", 150);
        for (Racer r : racers) {
            if (!r.inGame() || r.getPig() == null) {
                continue;
            }
            Player player = r.getPlayer();
            Location at = r.getPig().getLocation();
            boolean outside;
            if (boundary != null) {
                outside = !boundary.contains(at);
            } else {
                outside = at.getWorld() != startLocation.getWorld()
                        || (max > 0 && at.distanceSquared(startLocation) >= max * max);
            }
            if (outside) {
                sendBack(r, lastSafePlace(r));
                player.sendMessage(plugin.message("Messages.OutOfBounds"));
                Sounds.play(player, config, "Sounds.Crash");
            }
        }
    }

    // ------------------------------------------------------------------ racing

    /** The pig touched the ground: back to the last ring passed, and lose all speed. */
    public void crashed(Racer racer) {
        sendBack(racer, lastSafePlace(racer));
        Sounds.play(racer.getPlayer(), config, "Sounds.Crash");
    }

    /** Just past the last ring the racer flew through, facing on, or the start line before any. */
    private Location lastSafePlace(Racer racer) {
        Checkpoint last = checkpoints.get(racer.getCheckpoint());
        Location to = last == null ? null : last.respawn(1.5, 0.45);
        return to == null ? startLocation.clone() : to;
    }

    private void sendBack(Racer racer, Location to) {
        Player player = racer.getPlayer();
        allowedDismounts.add(player.getUniqueId());
        try {
            Entities.teleportMounted(racer.getPig(), player, to);
        } finally {
            allowedDismounts.remove(player.getUniqueId());
        }
        racer.resetSpeed(plugin.getFlight().getRespawnHoverTicks());
    }

    /** A racer flew through their next checkpoint. */
    public void passedCheckpoint(Racer racer) {
        boolean lapDone = racer.getCheckpoint() >= checkpoints.size();
        racer.nextCheckpoint();
        Bukkit.getPluginManager().callEvent(new CheckpointPassEvent(racer, this));
        if (!lapDone) {
            Sounds.play(racer.getPlayer(), config, "Sounds.Checkpoint");
            return;
        }
        // Back through ring 1: it counts as the first ring of the next lap
        racer.setCheckpoint(1);
        racer.addLap();
        Sounds.play(racer.getPlayer(), config, "Sounds.Lap");
        racer.getPlayer().sendMessage(plugin.message("Messages.LapComplete")
                .replace("{lap}", String.valueOf(racer.getLap())).replace("{total}", String.valueOf(laps)));
        if (racer.getLap() < laps) {
            return;
        }

        completedRacers.add(racer);
        releaseRacer(racer, true);
        Bukkit.broadcastMessage(plugin.message("Messages.CompleteBroadcast")
                .replace("{player}", racer.getPlayer().getName())
                .replace("{position}", String.valueOf(getRacerPosition(racer))));
        endWhenEveryoneFinished();
    }

    /** Takes a racer out of play: removes their pig and, if asked, sends them to the waiting area. */
    private void releaseRacer(Racer racer, boolean teleport) {
        Player player = racer.getPlayer();
        racer.setInGame(false);
        allowedDismounts.add(player.getUniqueId());
        try {
            Entity pig = racer.getPig();
            if (pig != null) {
                pig.eject();
                pig.remove();
                racer.setPig(null);
            }
        } finally {
            allowedDismounts.remove(player.getUniqueId());
        }
        plugin.getFlight().getInput().forget(player);
        if (teleport && player.isOnline() && waitLocation != null && raceState != RaceState.WAITING) {
            player.teleport(waitLocation);
        }
    }

    public void addPlayer(Player player) {
        if (isRacer(player)) {
            player.sendMessage(plugin.message("Messages.AlreadyInRace"));
            return;
        }
        if (raceState != RaceState.WAITING) {
            player.sendMessage(plugin.message("Messages.AlreadyStarted"));
            return;
        }
        racers.add(new Racer(player));
        player.teleport(waitLocation);
        for (String s : plugin.messages("Messages.Joined")) {
            player.sendMessage(s);
        }
        Bukkit.broadcastMessage(plugin.message("Messages.JoinedBroadcast")
                .replace("{player}", player.getName()).replace("{amount}", String.valueOf(racers.size())));
    }

    public void removePlayer(Player player, boolean messages) {
        Iterator<Racer> it = racers.iterator();
        while (it.hasNext()) {
            Racer r = it.next();
            if (r.is(player)) {
                releaseRacer(r, player.isOnline());
                it.remove();
                if (messages) {
                    player.sendMessage(plugin.message("Messages.Leave"));
                }
                if (raceState == RaceState.STARTING || raceState == RaceState.INPROGRESS) {
                    endWhenEveryoneFinished();
                }
                return;
            }
        }
        if (messages) {
            player.sendMessage(plugin.message("Messages.NotInRace"));
        }
    }

    public int getRacerPosition(Racer r) {
        return completedRacers.indexOf(r) + 1;
    }
}
