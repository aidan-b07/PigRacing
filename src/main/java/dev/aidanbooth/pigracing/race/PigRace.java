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
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
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
    /** When GO was shown, for race times. */
    private long goMillis;
    /** Seconds racers have from GO, or 0 for no limit. */
    private final int timeLimit;
    private final List<Integer> timeWarnings;

    /** Checks a race can run here. Returns a reason it can't, or null if it can. */
    public static String validate(Main plugin) {
        if (LocationSerialiser.parse(plugin.track().getString("Lobby")) == null) {
            return "The lobby isn't set, or its world isn't loaded. Use /race setlobby.";
        }
        if (LocationSerialiser.parse(plugin.track().getString("Start")) == null) {
            return "The start isn't set, or its world isn't loaded. Use /race setstart.";
        }
        if (plugin.getCheckpoints().size() == 0) {
            return "The track has no rings. Use /race ring add <size>.";
        }
        return null;
    }

    public PigRace(Main plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.checkpoints = plugin.getCheckpoints();
        this.waitLocation = LocationSerialiser.parse(plugin.track().getString("Lobby"));
        this.startLocation = LocationSerialiser.parse(plugin.track().getString("Start"));
        this.laps = Math.max(1, config.getInt("Settings.Laps", 3));
        this.timeLimit = Math.max(0, config.getInt("Settings.TimeLimit", 300));
        this.timeWarnings = config.getIntegerList("Settings.TimeWarnings");
        this.boundary = Region.parse(plugin.track().getString("Boundary.Pos1"), plugin.track().getString("Boundary.Pos2"));

        plugin.race = this;
        startGathering();
        repeat(this::updateActionbars, 5, 5);
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
        String hover = plugin.message("Chat.JoinHover");
        for (String line : plugin.messages("Chat.JoinRace")) {
            line = Main.center(line.replace("{time}", String.valueOf(seconds))
                    .replace("{seconds}", seconds == 1 ? "second" : "seconds")
                    .replace("{players}", String.valueOf(racers.size()))
                    .replace("{laps}", String.valueOf(laps)));
            BaseComponent[] text = TextComponent.fromLegacyText(line);
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
            Bukkit.broadcastMessage(plugin.message("Chat.RaceVoided"));
            stop();
            return;
        }
        int minPlayers = getMinPlayers();
        if (!forced && racers.size() < minPlayers) {
            for (Racer r : racers) {
                r.getPlayer().sendMessage(plugin.message("Chat.NotEnoughPlayers").replace("{min}", String.valueOf(minPlayers)));
            }
            Bukkit.broadcastMessage(plugin.message("Chat.RaceCancelled").replace("{min}", String.valueOf(minPlayers)));
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

        repeat(this::unstickRacers, 1, 1);
        startCountdown();
    }

    private TreeMap<Integer, String> countdownTitles() {
        TreeMap<Integer, String> titles = new TreeMap<>();
        ConfigurationSection section = plugin.messagesConfig().getConfigurationSection("Titles.Countdown");
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
                    String subtitle = plugin.message("Titles.CountdownSubtitle");
                    for (Racer r : racers) {
                        if (title != null) {
                            Titles.sendTitle(r.getPlayer(), 0, 20, 5, title, subtitle);
                        }
                        Sounds.play(r.getPlayer(), config, "Sounds.Countdown");
                    }
                    counter--;
                    return;
                }
                cancel();
                String go = plugin.message("Titles.Go");
                String goSubtitle = plugin.message("Titles.GoSubtitle");
                for (Racer r : racers) {
                    Titles.sendTitle(r.getPlayer(), 0, 25, 10, go, goSubtitle);
                    Sounds.play(r.getPlayer(), config, "Sounds.Go");
                }
                raceState = RaceState.INPROGRESS;
                goMillis = System.currentTimeMillis();
                if (timeLimit > 0) {
                    repeat(PigRace.this::checkTimeLimit, 20, 20);
                }
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
            Bukkit.broadcastMessage(plugin.message("Chat.RaceVoided"));
            stop();
            return;
        }
        raceState = RaceState.ENDED;
        broadcastResults();
        Bukkit.getScheduler().runTaskLater(plugin, this::stop, 20L * Math.max(0, config.getInt("Settings.EndDelay", 5)));
    }

    /** Seconds left before the time limit, never below 0. */
    private long secondsRemaining() {
        long elapsed = (System.currentTimeMillis() - goMillis) / 1000;
        return Math.max(0, timeLimit - elapsed);
    }

    /** Once a second while racing: warnings as the limit nears, and the end when it's reached. */
    private void checkTimeLimit() {
        if (raceState != RaceState.INPROGRESS) {
            return;
        }
        long left = secondsRemaining();
        if (left <= 0) {
            timeUp();
            return;
        }
        if (timeWarnings.contains((int) left)) {
            String warning = plugin.message("Chat.TimeWarning")
                    .replace("{time}", formatClock(left)).replace("{seconds}", String.valueOf(left));
            for (Racer r : racers) {
                if (r.inGame()) {
                    r.getPlayer().sendMessage(warning);
                    Sounds.play(r.getPlayer(), config, "Sounds.TimeWarning");
                }
            }
        }
    }

    /** The time limit ran out: whoever finished keeps their place, everyone still flying is out. */
    private void timeUp() {
        raceState = RaceState.ENDED;
        for (Racer r : racers) {
            if (r.inGame()) {
                Titles.sendTitle(r.getPlayer(), 0, 50, 15, plugin.message("Titles.TimeUp"), plugin.message("Titles.TimeUpSubtitle"));
                Sounds.play(r.getPlayer(), config, "Sounds.Crash");
            }
        }
        Bukkit.broadcastMessage(plugin.message("Chat.TimeUp"));
        if (completedRacers.isEmpty()) {
            Bukkit.broadcastMessage(plugin.message("Chat.NobodyFinished"));
        } else {
            broadcastResults();
        }
        stop();
    }

    /** 125 seconds as "2:05". */
    static String formatClock(long seconds) {
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    /** The results board: the RaceOver lines, with a line per finisher where {results} is. */
    private void broadcastResults() {
        List<String> board = new ArrayList<>();
        String resultLine = plugin.message("Chat.ResultLine");
        for (String line : plugin.messages("Chat.RaceOver")) {
            if (!line.contains("{results}")) {
                board.add(Main.center(line));
                continue;
            }
            for (int i = 0; i < completedRacers.size(); i++) {
                Racer r = completedRacers.get(i);
                board.add(Main.center(resultLine
                        .replace("{position}", ordinal(i + 1))
                        .replace("{player}", r.getPlayer().getName())
                        .replace("{time}", formatTime(r.getFinishMillis() - goMillis))));
            }
            // Only after the time limit: racers still flying didn't finish
            String dnfLine = plugin.message("Chat.DidNotFinishLine");
            for (Racer r : racers) {
                if (r.inGame()) {
                    board.add(Main.center(dnfLine.replace("{player}", r.getPlayer().getName())));
                }
            }
        }
        for (String line : board) {
            Bukkit.broadcastMessage(line);
        }
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
        for (Racer r : racers) {
            String text;
            if (raceState == RaceState.WAITING) {
                text = plugin.message("ActionBar.Waiting")
                        .replace("{players}", String.valueOf(racers.size()))
                        .replace("{min}", String.valueOf(getMinPlayers()))
                        .replace("{time}", String.valueOf(Math.max(0, secondsLeft)));
            } else if (raceState == RaceState.STARTING) {
                text = plugin.message("ActionBar.Starting");
            } else if (completedRacers.contains(r)) {
                text = plugin.message("ActionBar.Finished")
                        .replace("{position}", ordinal(getRacerPosition(r)))
                        .replace("{time}", formatTime(r.getFinishMillis() - goMillis));
            } else if (raceState == RaceState.INPROGRESS) {
                text = plugin.message(timeLimit > 0 ? "ActionBar.Racing" : "ActionBar.RacingNoLimit")
                        .replace("{remaining}", formatClock(secondsRemaining()))
                        .replace("{lap}", String.valueOf(Math.min(laps, r.getLap() + 1)))
                        .replace("{total}", String.valueOf(laps))
                        .replace("{ring}", String.valueOf(Math.min(r.getCheckpoint(), checkpoints.size())))
                        .replace("{rings}", String.valueOf(checkpoints.size()))
                        .replace("{speed}", String.format(java.util.Locale.ROOT, "%.1f", plugin.getFlight().topSpeed(r) * 20))
                        .replace("{time}", formatTime(System.currentTimeMillis() - goMillis));
            } else {
                continue;
            }
            ActionBar.sendActionBar(r.getPlayer(), text);
        }
    }

    /** 83450 ms as "1:23.4". */
    static String formatTime(long millis) {
        long tenths = Math.max(0, millis) / 100;
        return (tenths / 600) + ":" + String.format(java.util.Locale.ROOT, "%02d", (tenths / 10) % 60) + "." + (tenths % 10);
    }

    /** 1 as "1st", 12 as "12th", 22 as "22nd". */
    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        switch (n % 10) {
            case 1:
                return n + "st";
            case 2:
                return n + "nd";
            case 3:
                return n + "rd";
            default:
                return n + "th";
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
                player.sendMessage(plugin.message("Chat.OutOfBounds"));
                Sounds.play(player, config, "Sounds.Crash");
            }
        }
    }

    // ------------------------------------------------------------------ racing

    /** The pig touched the ground: back to the last ring passed, and lose all speed. */
    public void crashed(Racer racer) {
        sendBack(racer, lastSafePlace(racer));
        Sounds.play(racer.getPlayer(), config, "Sounds.Crash");
        Titles.sendTitle(racer.getPlayer(), 0, 20, 10, plugin.message("Titles.Crashed"), plugin.message("Titles.CrashedSubtitle"));
    }

    /** Just past the last ring the racer flew through, facing on, or the start line before any. */
    private Location lastSafePlace(Racer racer) {
        Checkpoint last = checkpoints.get(racer.getCheckpoint());
        Location to = last == null ? null : last.respawn(1.5, 0.45);
        return to == null ? startLocation.clone() : to;
    }

    private void sendBack(Racer racer, Location to) {
        Player player = racer.getPlayer();
        LivingEntity pig = racer.getPig();
        allowedDismounts.add(player.getUniqueId());
        try {
            Entities.teleportApart(pig, player, to);
        } finally {
            allowedDismounts.remove(player.getUniqueId());
        }
        racer.resetSpeed(plugin.getFlight().getRespawnHoverTicks());
        // Mounting in the same tick as the teleport can leave a 1.8 client unable to steer, so the
        // rider gets back on next tick, with the pig put back where it was sent in case it slipped
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!racer.inGame() || pig == null || !pig.isValid() || !player.isOnline()) {
                return;
            }
            pig.teleport(to);
            pig.setVelocity(new Vector());
            Entities.face(pig, to.getYaw(), 0);
            Entities.mount(pig, player);
            racer.setLastPigLocation(null);
        }, 1));
    }

    /**
     * The safety net for the 1.8 teleport problem in {@link Entities#stuckAfterTeleport}: a racer on
     * their pig whose client hasn't confirmed a teleport for half a second never will, so stop waiting.
     */
    private void unstickRacers() {
        for (Racer r : racers) {
            if (!r.inGame() || r.getPig() == null) {
                continue;
            }
            Player player = r.getPlayer();
            boolean riding = player.getVehicle() != null && player.getVehicle().getUniqueId().equals(r.getPig().getUniqueId());
            if (riding && Entities.stuckAfterTeleport(player)) {
                if (r.addStuckTick() >= 10) {
                    Entities.unstick(player);
                    r.resetStuckTicks();
                }
            } else {
                r.resetStuckTicks();
            }
        }
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
        Player player = racer.getPlayer();
        String time = formatTime(System.currentTimeMillis() - goMillis);
        if (racer.getLap() < laps) {
            String lap = String.valueOf(racer.getLap());
            String total = String.valueOf(laps);
            player.sendMessage(plugin.message("Chat.LapComplete")
                    .replace("{lap}", lap).replace("{total}", total).replace("{time}", time));
            boolean finalLap = racer.getLap() == laps - 1;
            Titles.sendTitle(player, 0, 30, 10,
                    plugin.message(finalLap ? "Titles.FinalLap" : "Titles.Lap").replace("{lap}", lap).replace("{total}", total),
                    plugin.message(finalLap ? "Titles.FinalLapSubtitle" : "Titles.LapSubtitle").replace("{lap}", lap).replace("{total}", total).replace("{time}", time));
            return;
        }

        racer.setFinishMillis(System.currentTimeMillis());
        completedRacers.add(racer);
        String position = ordinal(getRacerPosition(racer));
        Titles.sendTitle(player, 0, 50, 15, plugin.message("Titles.Finished").replace("{position}", position),
                plugin.message("Titles.FinishedSubtitle").replace("{position}", position).replace("{time}", time));
        releaseRacer(racer, true);
        Bukkit.broadcastMessage(plugin.message("Chat.CompleteBroadcast")
                .replace("{player}", player.getName())
                .replace("{position}", position)
                .replace("{time}", time));
        // After the teleport back, so any items land in the waiting area
        giveRewards(racer, getRacerPosition(racer), time);
        endWhenEveryoneFinished();
    }

    /**
     * Runs the admin's reward commands from the console for a racer who finished: their place's
     * commands from Places in rewards.yml, then AllFinishers. One bad command doesn't stop the rest.
     */
    private void giveRewards(Racer racer, int position, String time) {
        List<String> commands = new ArrayList<>();
        ConfigurationSection places = plugin.rewards().getConfigurationSection("Places");
        if (places != null) {
            commands.addAll(places.getStringList(String.valueOf(position)));
        }
        commands.addAll(plugin.rewards().getStringList("AllFinishers"));

        Player player = racer.getPlayer();
        for (String raw : commands) {
            String command = raw == null ? "" : raw.trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isEmpty()) {
                continue;
            }
            command = command.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{position}", String.valueOf(position))
                    .replace("{place}", ordinal(position))
                    .replace("{time}", time)
                    .replace("{racers}", String.valueOf(racers.size()));
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    plugin.getLogger().warning("Reward command for " + player.getName() + " isn't a known command: /" + command);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Reward command for " + player.getName() + " failed: /" + command, e);
            }
        }
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
            player.sendMessage(plugin.message("Chat.AlreadyInRace"));
            return;
        }
        if (raceState != RaceState.WAITING) {
            player.sendMessage(plugin.message("Chat.AlreadyStarted"));
            return;
        }
        racers.add(new Racer(player));
        player.teleport(waitLocation);
        for (String s : plugin.messages("Chat.Joined")) {
            player.sendMessage(Main.center(s.replace("{min}", String.valueOf(getMinPlayers()))));
        }
        Bukkit.broadcastMessage(plugin.message("Chat.JoinedBroadcast")
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
                    player.sendMessage(plugin.message("Chat.Leave"));
                }
                if (raceState == RaceState.WAITING) {
                    Bukkit.broadcastMessage(plugin.message("Chat.LeftBroadcast")
                            .replace("{player}", player.getName()).replace("{amount}", String.valueOf(racers.size())));
                }
                if (raceState == RaceState.STARTING || raceState == RaceState.INPROGRESS) {
                    endWhenEveryoneFinished();
                }
                return;
            }
        }
        if (messages) {
            player.sendMessage(plugin.message("Chat.NotInRace"));
        }
    }

    public int getRacerPosition(Racer r) {
        return completedRacers.indexOf(r) + 1;
    }
}
