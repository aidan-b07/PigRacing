package dev.aidanbooth.pigracing;

import dev.aidanbooth.pigracing.commands.MainCommand;
import dev.aidanbooth.pigracing.input.RiderInput;
import dev.aidanbooth.pigracing.input.RiderInputs;
import dev.aidanbooth.pigracing.listeners.RaceListener;
import dev.aidanbooth.pigracing.race.Checkpoints;
import dev.aidanbooth.pigracing.race.PigFlight;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.race.RingRenderer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
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
    private boolean inputLoadFailed;

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
        checkpoints.load(getConfig(), getLogger());
        rings = new RingRenderer(this);
        rings.reload(ringsConfig());
        getServer().getScheduler().runTaskTimer(this, rings, 1, Math.max(1, getConfig().getInt("Rings.Interval", 2)));

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
        checkpoints.load(getConfig(), getLogger());
        flight.reload(getConfig().getConfigurationSection("Flight"));
        rings.reload(ringsConfig());
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
        return color(withPrefix(getConfig().getString(path, path)));
    }

    public List<String> messages(String path) {
        List<String> lines = new ArrayList<>();
        for (String line : getConfig().getStringList(path)) {
            lines.add(color(withPrefix(line)));
        }
        return lines;
    }

    private String withPrefix(String text) {
        return text == null ? null : text.replace("{prefix}", getConfig().getString("Messages.Prefix", ""));
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
