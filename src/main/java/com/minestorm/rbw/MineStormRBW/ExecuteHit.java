package com.minestorm.rbw.MineStormRBW;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatColor;

public class ExecuteHit implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender arg0, Command arg1, String arg2, String[] arg3) {
        main.instance.loadConfigValues();
        arg0.sendMessage(ChatColor.GREEN + "[MineStormRBW] config.yml reloaded successfully!");
        return true;
    }
}
