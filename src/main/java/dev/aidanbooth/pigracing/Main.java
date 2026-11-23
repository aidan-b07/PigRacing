package dev.aidanbooth.pigracing;

import dev.aidanbooth.pigracing.commands.MainCommand;
import dev.aidanbooth.pigracing.input.RiderInput;
import dev.aidanbooth.pigracing.input.RiderInputs;
import dev.aidanbooth.pigracing.listeners.RaceListener;
import dev.aidanbooth.pigracing.race.Checkpoints;
import dev.aidanbooth.pigracing.race.PigFlight;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.race.RaceScheduler;
import dev.aidanbooth.pigracing.race.RingRenderer;
import org.bukkit.ChatColor;
import dev.aidanbooth.pigracing.utils.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class Main extends JavaPlugin {
    public PigRace race;

    private final Checkpoints checkpoints = new Checkpoints();
    private PigFlight flight;
    private RingRenderer rings;
    private RaceScheduler scheduler;
    private boolean inputLoadFailed;

    private final ConfigFile messagesFile = new ConfigFile(this, "messages.yml", null);
    private final ConfigFile rewardsFile = new ConfigFile(this, "rewards.yml", null);
    private final ConfigFile trackFile = new ConfigFile(this, "track.yml",
            "PigRacing track. Written by the setup commands (/race setlobby, setstart, ring, boundary),\n"
                    + "so edit it with those rather than by hand. Locations are world~x~y~z~yaw~pitch.\n"
                    + "Rings: Location is the ring's centre and the way it faces, Size the opening it fits.");

    @Override
    public void onLoad() {
        try {
            RiderInputs.load(this);
        } catch (Throwable t) {
            inputLoadFailed = true;
            getLogger().log(Level.SEVERE, "Could not start PacketEvents, so pigs can't read the jump key on this server", t);
        }
    }

    @Override
    public void onEnable() {
        // Missing keys fall back to the defaults inside the jar, so no saveConfig() here: on older
        // versions that would strip every comment from the file
        saveDefaultConfig();
        migrateSingleFile();
        loadFiles();

        if (inputLoadFailed) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RiderInput input;
        try {
            input = RiderInputs.start();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Could not start reading rider input", t);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Reading the jump key with " + input.name());

        flight = new PigFlight(input, getConfig().getConfigurationSection("Flight"));
        checkpoints.load(track(), getLogger());
        rings = new RingRenderer(this);
        rings.reload(ringsConfig());
        getServer().getScheduler().runTaskTimer(this, rings, 1, Math.max(1, getConfig().getInt("Rings.Interval", 2)));
        scheduler = new RaceScheduler(this);
        scheduler.reload(section("Schedule"));
        getServer().getScheduler().runTaskTimer(this, scheduler, 20, 20);

        getServer().getPluginManager().registerEvents(new RaceListener(this), this);
        RaceListener.registerPickupBlocker(this);

        MainCommand command = new MainCommand(this);
        PluginCommand race = getCommand("race");
        race.setExecutor(command);
        race.setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        if (race != null) {
            race.stop();
        }
        if (flight != null) {
            flight.getInput().shutdown();
        }
    }

    public void reload() {
        reloadConfig();
        loadFiles();
        checkpoints.load(track(), getLogger());
        flight.reload(getConfig().getConfigurationSection("Flight"));
        rings.reload(ringsConfig());
        scheduler.reload(section("Schedule"));
    }

    private ConfigurationSection section(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        return section != null ? section : getConfig().createSection(path);
    }

    public RaceScheduler getScheduler() {
        return scheduler;
    }

    private void loadFiles() {
        messagesFile.load();
        rewardsFile.load();
        trackFile.load();
    }

    /** Chat messages, titles and action bars. */
    public FileConfiguration messagesConfig() {
        return messagesFile.get();
    }

    /** Reward commands for finishers. */
    public FileConfiguration rewards() {
        return rewardsFile.get();
    }

    /** The lobby, start, rings and boundary, written by the setup commands. */
    public FileConfiguration track() {
        return trackFile.get();
    }

    public void saveTrack() {
        trackFile.save();
    }

    /**
     * Until the split everything lived in config.yml. On the first start after it, the track and the
     * rewards are moved into their own files; nothing in config.yml is changed or deleted, since
     * saving it would strip its comments on older servers.
     */
    private void migrateSingleFile() {
        FileConfiguration old = getConfig();
        if (!trackFile.exists() && old.isConfigurationSection("Data")) {
            trackFile.load();
            FileConfiguration track = trackFile.get();
            copy(old, "Data.WaitingLocation", track, "Lobby");
            copy(old, "Data.StartLocation", track, "Start");
            copy(old, "Data.Checkpoints", track, Checkpoints.PATH);
            copy(old, "Data.Boundary", track, "Boundary");
            trackFile.save();
            getLogger().info("Moved the track (lobby, start, rings, boundary) from config.yml into track.yml");
        }
        ConfigurationSection oldPlaces = old.getConfigurationSection("Rewards.Places");
        boolean hadRewards = (oldPlaces != null && !oldPlaces.getKeys(false).isEmpty())
                || !old.getStringList("Rewards.AllFinishers").isEmpty();
        if (!rewardsFile.exists() && hadRewards) {
            rewardsFile.load();
            copy(old, "Rewards.Places", rewardsFile.get(), "Places");
            copy(old, "Rewards.AllFinishers", rewardsFile.get(), "AllFinishers");
            rewardsFile.save();
            getLogger().info("Moved the rewards from config.yml into rewards.yml");
        }
        boolean leftovers = false;
        for (String section : new String[]{"Data", "Rewards", "Messages", "Titles", "Actionbar"}) {
            leftovers |= old.isConfigurationSection(section);
        }
        if (leftovers) {
            getLogger().info("config.yml still has sections from before it was split (Data, Rewards, Messages, Titles,"
                    + " Actionbar). They aren't read any more: messages are in messages.yml now. Delete them, or"
                    + " delete config.yml to get a fresh one.");
        }
    }

    /** Copies one value, or a whole section, to another file under a new path. */
    private static void copy(ConfigurationSection from, String fromPath, ConfigurationSection to, String toPath) {
        if (!from.isSet(fromPath)) {
            return;
        }
        if (!from.isConfigurationSection(fromPath)) {
            to.set(toPath, from.get(fromPath));
            return;
        }
        ConfigurationSection section = from.getConfigurationSection(fromPath);
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                to.set(toPath + "." + key, section.get(key));
            }
        }
    }

    private ConfigurationSection ringsConfig() {
        ConfigurationSection section = getConfig().getConfigurationSection("Rings");
        return section != null ? section : getConfig().createSection("Rings");
    }

    public Checkpoints getCheckpoints() {
        return checkpoints;
    }

    public RingRenderer getRings() {
        return rings;
    }

    public PigFlight getFlight() {
        return flight;
    }

    /**
     * A message from config with {prefix} filled in and colour codes applied, or the path itself if
     * it's missing. Lists come back unchanged in order; lines starting {center} are centred by
     * {@link #center} once their placeholders are filled.
     */
    public String message(String path) {
        return color(withPrefix(messagesConfig().getString(path, path)));
    }

    public List<String> messages(String path) {
        List<String> lines = new ArrayList<>();
        for (String line : messagesConfig().getStringList(path)) {
            lines.add(color(withPrefix(line)));
        }
        return lines;
    }

    private String withPrefix(String text) {
        return text == null ? null : text.replace("{prefix}", messagesConfig().getString("Prefix", ""));
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Half the width of the chat box, in the default font's pixels. */
    private static final int CHAT_CENTRE_PX = 154;

    /** Centres a line in chat if it starts with {center}; otherwise returns it as it is. */
    public static String center(String line) {
        if (line == null || !line.startsWith("{center}")) {
            return line;
        }
        String text = line.substring("{center}".length());
        int width = 0;
        boolean code = false;
        boolean bold = false;
        for (char c : text.toCharArray()) {
            if (c == ChatColor.COLOR_CHAR) {
                code = true;
                continue;
            }
            if (code) {
                code = false;
                char lower = Character.toLowerCase(c);
                if (lower == 'l') {
                    bold = true;
                } else if (lower == 'r' || (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')) {
                    // Colours reset formatting
                    bold = false;
                }
                continue;
            }
            width += charWidth(c) + (bold && c != ' ' ? 2 : 1);
        }
        int padding = CHAT_CENTRE_PX - width / 2;
        StringBuilder spaces = new StringBuilder();
        for (int px = 0; px + 4 <= padding; px += 4) {
            spaces.append(' ');
        }
        return spaces + text;
    }

    /** Pixel width of a character in Minecraft's default font, without the gap after it. */
    private static int charWidth(char c) {
        switch (c) {
            case 'i': case '!': case ',': case '.': case ':': case ';': case '|': case '\'':
                return 1;
            case 'l': case '`':
                return 2;
            case 'I': case 't': case '[': case ']': case ' ':
                return 3;
            case 'f': case 'k': case '"': case '(': case ')': case '*': case '<': case '>': case '{': case '}':
                return 4;
            case '@': case '~':
                return 6;
            default:
                return 5;
        }
    }
}
