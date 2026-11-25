package dev.aidanbooth.pigracing;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anonymous usage statistics on bStats. Server owners turn them off in plugins/bStats/config.yml.
 */
public final class Stats {
    /** This plugin's ID on bstats.org. */
    private static final int PLUGIN_ID = 35677;
    /** Has to match the chart's ID on bstats.org. */
    private static final String RACES_CHART = "races";

    /** Races started since the last report. */
    private final AtomicInteger races = new AtomicInteger();
    private Metrics metrics;

    void start(JavaPlugin plugin) {
        metrics = new Metrics(plugin, PLUGIN_ID);
        // bStats reports every 30 minutes and adds up every server, so each point is the races started
        // everywhere in that half hour
        metrics.addCustomChart(new SingleLineChart(RACES_CHART, () -> races.getAndSet(0)));
    }

    void stop() {
        if (metrics != null) {
            metrics.shutdown();
        }
    }

    /** Call when a race gets to GO. Cancelled sign-ups don't count. */
    public void raceStarted() {
        races.incrementAndGet();
    }
}
