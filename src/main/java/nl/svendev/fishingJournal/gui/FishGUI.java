package nl.svendev.fishingJournal.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import nl.svendev.fishingJournal.FishingJournal;
import nl.svendev.fishingJournal.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FishGUI {
    private final FishingJournal plugin;
    private final DatabaseManager databaseManager;
    private final FishLoader fishLoader; // Add FishLoader field

    public FishGUI(FishingJournal plugin, DatabaseManager databaseManager, FishLoader fishLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.fishLoader = fishLoader; // Initialize FishLoader
    }

    public void openMainMenu(Player player) {
        // Fetch all rarities from FishLoader
        Set<String> allRarities = Set.copyOf(fishLoader.getAllRarities());

        databaseManager.getPlayerFishRarities(player.getUniqueId()).thenAccept(discoveredRarities -> {
            // Ensure GUI operations are on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int rows = Math.min(6, (allRarities.size() - 1) / 9 + 1);
                Gui gui = Gui.gui()
                        .title(Component.text("Fish Stats - Rarities"))
                        .rows(rows)
                        .disableAllInteractions()
                        .create();

                for (String rarity : allRarities) {
                    String formattedName = ChatColor.translateAlternateColorCodes('&', rarity);
                    boolean discovered = discoveredRarities.contains(rarity) || player.hasPermission("fishingjournal.viewall");

                    ItemStack item;
                    if (discovered) {
                        // Show tropical fish for discovered rarities
                        item = ItemBuilder.from(Material.TROPICAL_FISH)
                                .name(Component.text(formattedName))
                                .build();
                    } else {
                        // Show iron bar for undiscovered rarities
                        item = ItemBuilder.from(Material.IRON_BARS)
                                .name(Component.text(ChatColor.RED + "Undiscovered Rarity"))
                                .build();
                    }

                    GuiItem guiItem = new GuiItem(item, event -> {
                        event.setCancelled(true);
                        if (discovered) {
                            openRarityMenu(player, rarity);
                        }
                    });

                    gui.addItem(guiItem);
                }

                gui.open(player);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to fetch player fish rarities: " + ex.getMessage());
            return null;
        });
    }

    public void openRarityMenu(Player player, String rarity) {
        RarityGUI rarityGUI = new RarityGUI(plugin, databaseManager, fishLoader); // Pass FishLoader
        rarityGUI.openRarityMenu(player, rarity);
    }
}