package com.zest.knockback;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExecuteHit implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ZestPlugin.readConfig();
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GREEN + "[ZestKnockback] Config reloaded successfully!");
        } else {
            sender.sendMessage("[ZestKnockback] Config reloaded successfully!");
        }
        return true;
    }
}
