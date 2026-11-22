package dev.aidanbooth.pigracing.listeners.checkpoint;

import com.github.fierioziy.particlenativeapi.api.ParticleNativeAPI;
import com.github.fierioziy.particlenativeapi.api.Particles_1_8;
import com.github.fierioziy.particlenativeapi.core.ParticleNativeCore;
import dev.aidanbooth.pigracing.Main;

public class CheckpointParticleHandler {
    private Main plugin = Main.getPlugin(Main.class);

    ParticleNativeAPI api = ParticleNativeCore.loadAPI(plugin);
    Particles_1_8 particles = api.getParticles_1_8();


}
