package dev.aidanbooth.pigracing;

import dev.aidanbooth.pigracing.commands.MainCommand;
import dev.aidanbooth.pigracing.listeners.Disconnect;
import dev.aidanbooth.pigracing.listeners.LapCompleteListener;
import dev.aidanbooth.pigracing.listeners.OnWorldLoad;
import dev.aidanbooth.pigracing.listeners.checkpoint.CheckPointPassEventListener;
import dev.aidanbooth.pigracing.listeners.checkpoint.CheckpointParticleHandler;
import dev.aidanbooth.pigracing.listeners.pig.PigDamageCanceller;
import dev.aidanbooth.pigracing.listeners.pig.PigDismountCanceller;
import dev.aidanbooth.pigracing.listeners.pig.PigHitGroundListener;
import dev.aidanbooth.pigracing.listeners.racers.CommandCanceller;
import dev.aidanbooth.pigracing.listeners.racers.DropCanceller;
import dev.aidanbooth.pigracing.listeners.racers.FallResistance;
import dev.aidanbooth.pigracing.listeners.racers.PickupCanceller;
import dev.aidanbooth.pigracing.race.PigRace;
import dev.aidanbooth.pigracing.listeners.racers.PlayerDisconnectListener;
import dev.aidanbooth.pigracing.race.customentity.CustomPig;
import net.minecraft.server.v1_8_R3.EntityPig;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private CheckPointPassEventListener checkPointPassEventListener;
    private CheckpointParticleHandler checkpointParticleHandler;

    public PigRace race;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new Disconnect(), this);
        getServer().getPluginManager().registerEvents(new CheckPointPassEventListener(), this);
        getServer().getPluginManager().registerEvents(new LapCompleteListener(), this);
        getServer().getPluginManager().registerEvents(new PigDamageCanceller(), this);
        getServer().getPluginManager().registerEvents(new PigDismountCanceller(), this);
        getServer().getPluginManager().registerEvents(new PlayerDisconnectListener(), this);
        getServer().getPluginManager().registerEvents(new DropCanceller(), this);
        getServer().getPluginManager().registerEvents(new PickupCanceller(), this);
        getServer().getPluginManager().registerEvents(new CommandCanceller(), this);
        getServer().getPluginManager().registerEvents(new FallResistance(), this);
        getServer().getPluginManager().registerEvents(new OnWorldLoad(), this);
        getServer().getPluginManager().registerEvents(new PigHitGroundListener(), this);

        getCommand("race").setExecutor(new MainCommand());

        CustomPig.registerEntity("Pig", 90, EntityPig.class, CustomPig.class);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
