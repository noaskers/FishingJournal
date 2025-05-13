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

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FishGUI {

    private final FishingJournal plugin;
    private final DatabaseManager databaseManager;
    private final FishLoader fishLoader;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public FishGUI(FishingJournal plugin, DatabaseManager databaseManager, FishLoader fishLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.fishLoader = fishLoader;
    }

    public void openMainMenu(Player player) {
        Set<String> allRarities = new HashSet<>(fishLoader.getAllRarities());

        databaseManager.getPlayerFishRarities(player.getUniqueId()).thenAccept(discoveredRarities -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int rows = plugin.getConfig().getInt("fish-gui.rows", 6);
                String title = plugin.getConfig().getString("fish-gui.title", "Fish Journal");

                Gui gui = Gui.gui()
                        .title(mm.deserialize(title))
                        .rows(rows)
                        .disableAllInteractions()
                        .create();

                // Load rarities from config
                ConfigurationSection raritySection = plugin.getConfig().getConfigurationSection("fish-gui.rarity");
                Set<String> configuredRarities = new HashSet<>();

                if (raritySection != null) {
                    for (String rarityKey : raritySection.getKeys(false)) {
                        configuredRarities.add(rarityKey);

                        Material material = Material.matchMaterial(raritySection.getString(rarityKey + ".material", "TROPICAL_FISH"));
                        int slot = raritySection.getInt(rarityKey + ".slot", -1);
                        String displayName = raritySection.getString(rarityKey + ".display-name", "<gray>Unknown</gray>");
                        List<String> loreList = raritySection.getStringList(rarityKey + ".lore");

                        boolean discovered = discoveredRarities.stream().anyMatch(r -> r.equalsIgnoreCase(rarityKey)) || player.hasPermission("fishingjournal.viewall");

                        Material itemMaterial = discovered ? material : Material.matchMaterial(plugin.getConfig().getString("fish-gui.default-uncaught-rarity-material", "IRON_BARS"));

                        String itemName = discovered ? displayName : plugin.getConfig().getString("fish-gui.uncaught-rarity.name", "<red>Niet gevonden</red>");
                        List<String> itemLore = discovered ? loreList : plugin.getConfig().getStringList("fish-gui.uncaught-rarity.lore");

                        ItemStack item = ItemBuilder.from(itemMaterial != null ? itemMaterial : Material.TROPICAL_FISH)
                                .name(noItalic(mm.deserialize(itemName)))
                                .lore(itemLore.stream().map(line -> noItalic(mm.deserialize(line))).toList())
                                .build();

                        GuiItem guiItem = new GuiItem(item, event -> {
                            event.setCancelled(true);
                            if (discovered) {
                                openRarityMenu(player, rarityKey);
                            }
                        });

                        if (slot >= 0) {
                            gui.setItem(slot, guiItem);
                        } else {
                            gui.addItem(guiItem);
                        }
                    }
                }

                // Handle rarities not in config (put in first empty slot)
                for (String rarity : allRarities) {
                    if (!configuredRarities.contains(rarity)) {
                        int emptySlot = findEmptySlot(gui);

                        ItemStack item = ItemBuilder.from(Material.matchMaterial(plugin.getConfig().getString("fish-gui.default-rarity-item", "TROPICAL_FISH")))
                                .name(noItalic(mm.deserialize("<gray>" + rarity + "</gray>")))
                                .lore(Collections.singletonList(noItalic(mm.deserialize("<gray>No info available</gray>"))))
                                .build();

                        GuiItem guiItem = new GuiItem(item, event -> {
                            event.setCancelled(true);
                            openRarityMenu(player, rarity);
                        });

                        gui.setItem(emptySlot, guiItem);
                    }
                }

                // Setup filler items
                if (plugin.getConfig().isConfigurationSection("fish-gui.filler")) {
                    Material fillerMaterial = Material.matchMaterial(plugin.getConfig().getString("fish-gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
                    List<Integer> slots = parseSlotList(plugin.getConfig().getString("fish-gui.filler.slot", ""));
                    String fillerName = plugin.getConfig().getString("fish-gui.filler.display-name", "<gray> </gray>");
                    List<String> fillerLore = plugin.getConfig().getStringList("fish-gui.filler.lore");

                    ItemStack fillerItem = ItemBuilder.from(fillerMaterial != null ? fillerMaterial : Material.GRAY_STAINED_GLASS_PANE)
                            .name(noItalic(mm.deserialize(fillerName)))
                            .lore(fillerLore.stream().map(line -> noItalic(mm.deserialize(line))).toList())
                            .build();

                    GuiItem fillerGuiItem = new GuiItem(fillerItem);

                    for (int slot : slots) {
                        gui.setItem(slot, fillerGuiItem);
                    }
                }

// Setup custom items
                for (String key : plugin.getConfig().getConfigurationSection("fish-gui").getKeys(false)) {
                    if (key.startsWith("custom-item")) {
                        String path = "fish-gui." + key;
                        int slot = plugin.getConfig().getInt(path + ".slot", -1);
                        String name = plugin.getConfig().getString(path + ".name", "<gray>Custom</gray>");
                        List<String> loreList = plugin.getConfig().getStringList(path + ".lore");
                        String materialName = plugin.getConfig().getString(path + ".material", "IRON_BARS");

                        Material material = Material.matchMaterial(materialName);
                        if (material == null) {
                            plugin.getLogger().warning("Invalid material for " + key + ": " + materialName);
                            material = Material.IRON_BARS; // Fallback material
                        }

                        if (slot >= 0) {
                            ItemStack customItem = ItemBuilder.from(material)
                                    .name(noItalic(mm.deserialize(name)))
                                    .lore(loreList.stream().map(line -> noItalic(mm.deserialize(line))).toList())
                                    .build();

                            gui.setItem(slot, new GuiItem(customItem));
                        }
                    }
                }

                // Setup close button
                if (plugin.getConfig().isConfigurationSection("fish-gui.close-button")) {
                    int slot = plugin.getConfig().getInt("fish-gui.close-button.slot", 49);
                    Material closeMaterial = Material.matchMaterial(plugin.getConfig().getString("fish-gui.close-button.material", "BARRIER"));
                    String name = plugin.getConfig().getString("fish-gui.close-button.display-name", "<red>Close</red>");
                    List<String> loreList = plugin.getConfig().getStringList("fish-gui.close-button.lore");

                    ItemStack closeItem = ItemBuilder.from(closeMaterial != null ? closeMaterial : Material.BARRIER)
                            .name(noItalic(mm.deserialize(name)))
                            .lore(loreList.stream().map(line -> noItalic(mm.deserialize(line))).toList())
                            .build();

                    gui.setItem(slot, new GuiItem(closeItem, event -> {
                        event.setCancelled(true);
                        player.closeInventory();
                    }));
                }

                gui.open(player);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load Fish GUI: " + ex.getMessage());
            return null;
        });
    }

    private int findEmptySlot(Gui gui) {
        for (int i = 0; i < gui.getRows() * 9; i++) {
            if (gui.getInventory().getItem(i) == null) {
                return i;
            }
        }
        return 0; // fallback
    }

    private List<Integer> parseSlotList(String slotString) {
        List<Integer> slots = new ArrayList<>();
        if (slotString == null || slotString.isEmpty()) return slots;
        for (String part : slotString.split(",")) {
            try {
                slots.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public void openRarityMenu(Player player, String rarity) {
        RarityGUI rarityGUI = new RarityGUI(plugin, databaseManager, fishLoader);
        rarityGUI.openRarityMenu(player, rarity);
    }
}
