package nl.svendev.fishingJournal;

import nl.svendev.fishingJournal.commands.FishingJournalCommand;
import nl.svendev.fishingJournal.commands.ReloadFishCommand;
import nl.svendev.fishingJournal.gui.RarityGUI;
import nl.svendev.fishingJournal.listeners.FishCaughtListener;
import org.bukkit.plugin.java.JavaPlugin;
import nl.svendev.fishingJournal.database.DatabaseManager;
import nl.svendev.fishingJournal.database.HikariCPManager;
import nl.svendev.fishingJournal.gui.FishGUI;
import nl.svendev.fishingJournal.gui.FishLoader;

public class FishingJournal extends JavaPlugin {
    private HikariCPManager hikariManager;
    private DatabaseManager databaseManager;
    private FishLoader fishLoader; // Add FishLoader field
    private FishGUI fishGUI;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Initialize HikariCP
        this.hikariManager = new HikariCPManager(this);
        this.databaseManager = new DatabaseManager(hikariManager, this);

        FishLoader fishLoader = new FishLoader(this);
        RarityGUI rarityGUI = new RarityGUI(this, databaseManager, fishLoader);

        // Initialize GUI system
        this.fishGUI = new FishGUI(this, databaseManager, fishLoader);

        // Register listeners
        getServer().getPluginManager().registerEvents(new FishCaughtListener(databaseManager), this);

        // Register commands
        if (getCommand("fishingjournal") != null) {
            getCommand("fishingjournal").setExecutor(new FishingJournalCommand(fishGUI));
        } else {
            getLogger().severe("Command 'fishingjournal' is not defined in plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
        }
        this.getCommand("fishreload").setExecutor(new ReloadFishCommand(fishLoader));
    }

    @Override
    public void onDisable() {
        if (hikariManager != null) {
            hikariManager.close();
        }
    }
}