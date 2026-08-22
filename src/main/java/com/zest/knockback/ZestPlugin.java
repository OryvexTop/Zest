package com.zest.knockback;

import org.bukkit.plugin.java.JavaPlugin;

public class ZestPlugin extends JavaPlugin {

    private static ZestPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new ZestKnockbackListener(this), this);
        getLogger().info("ZestKnockback has been successfully activated!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ZestKnockback has been disabled.");
    }

    public static ZestPlugin getInstance() {
        return instance;
    }
}
