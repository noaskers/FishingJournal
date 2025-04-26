package nl.svendev.fishingJournal.listeners;

import com.oheers.fish.api.EMFFishEvent;
import com.oheers.fish.fishing.items.Fish;
import nl.svendev.fishingJournal.database.DatabaseManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class FishCaughtListener implements Listener {
    private final DatabaseManager databaseManager;

    public FishCaughtListener(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onFishCaught(EMFFishEvent event) {
        Fish fish = event.getFish();
        UUID playerUuid = event.getPlayer().getUniqueId();

        databaseManager.updateFishStats(
                playerUuid,
                fish.getName(),
                fish.getRarity().getId(),
                fish.getLength()
        );
    }
}
