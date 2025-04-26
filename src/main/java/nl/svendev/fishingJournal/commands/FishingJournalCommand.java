package nl.svendev.fishingJournal.commands;

import nl.svendev.fishingJournal.gui.FishGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FishingJournalCommand implements CommandExecutor {
    private final FishGUI fishGUI;

    public FishingJournalCommand(FishGUI fishGUI) {
        this.fishGUI = fishGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        // Open the main GUI
        if (args.length == 0) {
            fishGUI.openMainMenu(player);
            return true;
        }

        // Open a specific rarity GUI
        String rarity = String.join(" ", args);
        fishGUI.openRarityMenu(player, rarity);
        return true;
    }
}