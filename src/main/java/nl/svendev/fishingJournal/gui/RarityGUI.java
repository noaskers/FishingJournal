package nl.svendev.fishingJournal.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.svendev.fishingJournal.FishingJournal;
import nl.svendev.fishingJournal.database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

public class RarityGUI {
    private final FishingJournal plugin;
    private final DatabaseManager databaseManager;
    private final FishLoader fishLoader;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public RarityGUI(FishingJournal plugin, DatabaseManager databaseManager, FishLoader fishLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.fishLoader = fishLoader;
    }

    public void openRarityMenu(Player player, String rarity) {
        databaseManager.getFishStatsByRarity(player.getUniqueId(), rarity).thenAccept(fishStats -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ConfigurationSection config = plugin.getConfig().getConfigurationSection("rarity-gui");
                if (config == null) {
                    player.sendMessage(miniMessage.deserialize("<red>GUI configuration is missing!"));
                    return;
                }

                List<String> allFish = fishLoader.getFishNamesByRarity(rarity);
                if (allFish.isEmpty()) {
                    player.sendMessage(miniMessage.deserialize("<red>No fish found for rarity: " + rarity));
                    return;
                }

                int rows = config.getInt("rows", 6);

// Get the title string from config, replace {rarity} with actual rarity
                String titleString = config.getString("title", "<gradient:gold:yellow>Fish Stats - {rarity}</gradient>");
                titleString = titleString.replace("{rarity}", rarity);

// Now deserialize it properly
                Component title = miniMessage.deserialize(titleString);

// Create the GUI
                Gui gui = Gui.gui()
                        .title(title)
                        .rows(rows)
                        .disableAllInteractions()
                        .create();

                Set<Integer> occupiedSlots = new HashSet<>();

                // Process custom items
                for (String key : config.getKeys(false)) {
                    if (key.equals("title") || key.equals("rows") ||
                            key.equals("default-fish-material") || key.equals("fish-item")) {
                        continue;
                    }
                    if (key.equals("back-button")) continue;

                    ConfigurationSection itemConfig = config.getConfigurationSection(key);
                    if (itemConfig != null && itemConfig.contains("slot") && itemConfig.contains("material")) {
                        processCustomItem(itemConfig, gui, rows, occupiedSlots, key);
                    }
                }

                // Place fish items
                int slot = 0;
                for (String fishName : allFish) {
                    Map<String, Object> stats = fishStats.get(fishName);
                    boolean discovered = stats != null || player.hasPermission("fishingjournal.viewall");

                    ItemStack item = discovered
                            ? fishLoader.getFishItem(fishName, rarity, true)
                            : new ItemStack(Material.valueOf(config.getString("default-fish-material", "TROPICAL_FISH")));

                    // Process name with MiniMessage and remove italics
                    String nameTemplate = config.getString("fish-item.name", "<aqua>{fish_name}");
                    Component name = miniMessage.deserialize(
                                    nameTemplate.replace("{fish_name}", fishName))
                            .decoration(TextDecoration.ITALIC, false);

                    // Process lore with MiniMessage and remove italics
                    List<Component> lore = new ArrayList<>();
                    for (String line : config.getStringList("fish-item.lore")) {
                        lore.add(miniMessage.deserialize(
                                        applyPlaceholders(line, fishName, stats))
                                .decoration(TextDecoration.ITALIC, false));
                    }

                    item = ItemBuilder.from(item)
                            .name(name)
                            .lore(lore)
                            .build();

                    while (occupiedSlots.contains(slot)) slot++;
                    if (slot >= rows * 9) break;

                    gui.setItem(slot, new GuiItem(item, event -> event.setCancelled(true)));
                    occupiedSlots.add(slot);
                    slot++;
                }

                // Back button
                ConfigurationSection backButtonConfig = config.getConfigurationSection("back-button");
                if (backButtonConfig != null) {
                    int backButtonSlot = backButtonConfig.getInt("slot", rows * 9 - 1);
                    if (!occupiedSlots.contains(backButtonSlot)) {
                        ItemStack backButton = ItemBuilder
                                .from(Material.valueOf(backButtonConfig.getString("material", "ARROW")))
                                .name(miniMessage.deserialize(
                                                backButtonConfig.getString("display-name", "<red>Back"))
                                        .decoration(TextDecoration.ITALIC, false))
                                .lore(backButtonConfig.getStringList("lore").stream()
                                        .map(line -> miniMessage.deserialize(
                                                        applyPlaceholders(line, "", null))
                                                .decoration(TextDecoration.ITALIC, false))
                                        .toList())
                                .build();

                        gui.setItem(backButtonSlot, new GuiItem(backButton, event -> {
                            event.setCancelled(true);
                            new FishGUI(plugin, databaseManager, fishLoader).openMainMenu(player);
                        }));
                    }
                }

                gui.open(player);
            });
        });
    }

    private void processCustomItem(ConfigurationSection itemConfig, Gui gui, int rows,
                                   Set<Integer> occupiedSlots, String configKey) {
        try {
            int slot = itemConfig.getInt("slot");
            String materialName = itemConfig.getString("material", "STONE");
            String name = itemConfig.getString("display-name", "<white>Custom Item");
            List<String> loreListRaw = itemConfig.getStringList("lore");

            List<Component> loreList = loreListRaw.stream()
                    .map(line -> miniMessage.deserialize(line)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList();

            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Invalid material in " + configKey + ": " + materialName);
                return;
            }

            ItemStack item = ItemBuilder.from(material)
                    .name(miniMessage.deserialize(name)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(loreList)
                    .build();

            if (slot >= 0 && slot < rows * 9 && !occupiedSlots.contains(slot)) {
                gui.setItem(slot, new GuiItem(item, event -> event.setCancelled(true)));
                occupiedSlots.add(slot);
            } else {
                plugin.getLogger().warning("Invalid or occupied slot in " + configKey + ": " + slot);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading custom item " + configKey + ": " + e.getMessage());
        }
    }

    private String applyPlaceholders(String text, String fishName, Map<String, Object> stats) {
        if (text == null) return "";

        text = text.replace("{fish_name}", fishName);

        if (stats == null) {
            return text.replace("{personal_largest}", "N/A")
                    .replace("{personal_smallest}", "N/A")
                    .replace("{times_caught}", "N/A")
                    .replace("{first_caught}", "N/A")
                    .replace("{global_largest}", "N/A")
                    .replace("{global_largest_owner}", "N/A")
                    .replace("{global_smallest}", "N/A")
                    .replace("{global_smallest_owner}", "N/A")
                    .replace("{global_most_caught}", "N/A")
                    .replace("{global_most_caught_owner}", "N/A");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
        return text.replace("{personal_largest}", formatDouble(stats.get("personal_largest")))
                .replace("{personal_smallest}", formatDouble(stats.get("personal_smallest")))
                .replace("{times_caught}", String.valueOf(stats.get("times_caught")))
                .replace("{first_caught}", sdf.format(stats.get("first_caught")))
                .replace("{global_largest}", formatDouble(stats.get("global_largest")))
                .replace("{global_largest_owner}", String.valueOf(stats.get("global_largest_owner")))
                .replace("{global_smallest}", formatDouble(stats.get("global_smallest")))
                .replace("{global_smallest_owner}", String.valueOf(stats.get("global_smallest_owner")))
                .replace("{global_most_caught}", String.valueOf(stats.get("global_most_caught")))
                .replace("{global_most_caught_owner}", String.valueOf(stats.get("global_most_caught_owner")));
    }

    private String formatDouble(Object obj) {
        return obj instanceof Number ? String.format("%.2f", ((Number) obj).doubleValue()) : "N/A";
    }
}