package dev.aidanbooth.pigracing;

import dev.aidanbooth.pigracing.commands.MainCommand;
import dev.aidanbooth.pigracing.input.RiderInput;
import dev.aidanbooth.pigracing.input.RiderInputs;
import dev.aidanbooth.pigracing.listeners.RaceListener;
import dev.aidanbooth.pigracing.race.Checkpoints;
import dev.aidanbooth.pigracing.race.PigFlight;
import dev.aidanbooth.pigracing.race.PigRace;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class Main extends JavaPlugin {
    public PigRace race;

    private final Checkpoints checkpoints = new Checkpoints();
    private PigFlight flight;
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
        checkpoints.load(getConfig());

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
        checkpoints.load(getConfig());
        flight.reload(getConfig().getConfigurationSection("Flight"));
    }

    public Checkpoints getCheckpoints() {
        return checkpoints;
    }

    public PigFlight getFlight() {
        return flight;
    }

    /** A message from config with colour codes applied, or the path itself if it's missing. */
    public String message(String path) {
        return color(getConfig().getString(path, path));
    }

    public List<String> messages(String path) {
        List<String> lines = new ArrayList<>();
        for (String line : getConfig().getStringList(path)) {
            lines.add(color(line));
        }
        return lines;
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
