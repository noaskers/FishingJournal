package nl.svendev.fishingJournal.gui;

import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import nl.svendev.fishingJournal.FishingJournal;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FishLoader {

    private final FishingJournal plugin;
    private final Map<String, Map<String, ItemStack>> fishItemsByRarity = new HashMap<>();

    public FishLoader(FishingJournal plugin) {
        this.plugin = plugin;
        loadAllFish();
    }

    private String normalizeKey(String key) {
        return key.trim().toLowerCase();
    }

    private void loadAllFish() {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            FishManager fishManager = FishManager.getInstance();
           // System.out.println("FishManager instance: " + fishManager);

            if (fishManager == null) {
                System.out.println("FishManager is null!");
                return;
            }

            Map<String, Rarity> rarityMap = fishManager.getRarityMap();
         //   System.out.println("RarityMap: " + rarityMap);

            if (rarityMap == null || rarityMap.isEmpty()) {
                System.out.println("RarityMap is null or empty!");
                return;
            }

            for (Map.Entry<String, Rarity> entry : rarityMap.entrySet()) {
                //System.out.println("Processing rarity key: " + entry.getKey());
                Rarity rarity = entry.getValue();
                //System.out.println("Rarity ID: " + rarity.getId() + ", Fish count: " + rarity.getFishList().size());

                Map<String, ItemStack> fishItems = new HashMap<>();
                for (Fish fish : rarity.getFishList()) {
                    //System.out.println("Adding fish: " + fish.getName());
                    ItemStack fishItem = fish.getFactory().createItem(null, -1);
                    fishItems.put(fish.getName(), fishItem); // Store original name as key
                }
                fishItemsByRarity.put(normalizeKey(rarity.getId()), fishItems);
            }

            System.out.println("Keys in fishItemsByRarity after loading: " + fishItemsByRarity.keySet());
        }, 20L); // 1 second delay
    }

    public ItemStack getFishItem(String fishName, String rarity, boolean discovered) {
        if (fishItemsByRarity.isEmpty()) {
            loadAllFish();
            return getFallbackItem(fishName, discovered);
        }

        rarity = normalizeKey(rarity);

//        System.out.println("Accessing rarity: " + rarity);
//        System.out.println("Current fishItemsByRarity keys: " + fishItemsByRarity.keySet());

        if (!discovered) {
            return getUndiscoveredItem();
        }

        Map<String, ItemStack> fishItems = fishItemsByRarity.get(rarity);
        if (fishItems != null) {
            //System.out.println("Available fish for rarity '" + rarity + "': " + fishItems.keySet());
            // Perform case-insensitive lookup
            for (Map.Entry<String, ItemStack> entry : fishItems.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(fishName)) {
                    return ItemBuilder.from(entry.getValue().clone())
                            .name(Component.text(ChatColor.GREEN + entry.getKey())) // Use original name
                            .build();
                }
            }
            System.out.println("Fish name not found: " + fishName);
        } else {
            System.out.println("Rarity not found: " + rarity);
          //  logAllRarities();
        }

        return getFallbackItem(fishName, discovered);
    }

    private ItemStack getUndiscoveredItem() {
        return ItemBuilder.from(Material.IRON_BARS)
                .name(Component.text(ChatColor.RED + "Undiscovered Fish"))
                .build();
    }

    private ItemStack getFallbackItem(String fishName, boolean discovered) {
        if (!discovered) {
            return getUndiscoveredItem();
        }
        return ItemBuilder.from(Material.COD)
                .name(Component.text(ChatColor.RED + "Unknown Fish: " + fishName))
                .build();
    }

    public List<String> getAllRarities() {
        FishManager fishManager = FishManager.getInstance();
        if (fishManager == null || fishManager.getRarityMap() == null) {
            return List.of();
        }
        return fishManager.getRarityMap().values().stream()
                .map(Rarity::getId)
                .toList();
    }

 /*   public void logAllRarities() {
        List<String> rarities = getAllRarities();
        System.out.println("Rarities from EvenMoreFish plugin: " + rarities);
    } */

    public List<String> getFishNamesByRarity(String rarity) {
        rarity = normalizeKey(rarity);
        FishManager fishManager = FishManager.getInstance();

        if (fishManager == null || fishManager.getRarityMap() == null) {
            return List.of();
        }

        Rarity rarityObject = fishManager.getRarityMap().get(rarity);

        if (rarityObject != null) {
            return rarityObject.getFishList().stream()
                    .map(Fish::getName)
                    .toList();
        }

        return List.of();
    }


    public void reload() {
        fishItemsByRarity.clear();
        loadAllFish();
    }
}