package nl.svendev.fishingJournal.commands;

import nl.svendev.fishingJournal.gui.FishLoader;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadFishCommand implements CommandExecutor {
    private final FishLoader fishLoader;

    public ReloadFishCommand(FishLoader fishLoader) {
        this.fishLoader = fishLoader;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        fishLoader.reload();
        sender.sendMessage(ChatColor.GREEN + "Reloaded fish data from EvenMoreFish");
        return true;
    }
}