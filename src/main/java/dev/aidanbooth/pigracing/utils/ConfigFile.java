package dev.aidanbooth.pigracing.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * One of the plugin's YAML files besides config.yml. Files shipped in the jar are copied out on first
 * run, with their comments, and the jar's copy backs up any key missing from the server's.
 */
public final class ConfigFile {
    private final JavaPlugin plugin;
    private final String name;
    private final File file;
    private final String header;
    private YamlConfiguration yaml = new YamlConfiguration();

    /** @param header written at the top of files the plugin saves itself, or null for shipped files */
    public ConfigFile(JavaPlugin plugin, String name, String header) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
        this.header = header;
    }

    public boolean exists() {
        return file.exists();
    }

    public void load() {
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource(name);
        if (bundled != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
    }

    public YamlConfiguration get() {
        return yaml;
    }

    /** Only for files the plugin writes itself: saving drops every comment but the header. */
    public void save() {
        if (header != null) {
            yaml.options().header(header);
            yaml.options().copyHeader(true);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, e);
        }
    }
}
