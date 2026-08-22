package com.zest.knockback;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZestKnockbackListener implements Listener {

    private final ZestPlugin plugin;
    private final Map<UUID, Long> lastDamageTaken = new HashMap<>();

    public ZestKnockbackListener(ZestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        long now = System.currentTimeMillis();
        long attackerLastHit = lastDamageTaken.getOrDefault(attacker.getUniqueId(), 0L);
        boolean isCounter = (now - attackerLastHit) <= 450;

        lastDamageTaken.put(victim.getUniqueId(), now);

        // Apply knockback without relying on unvalidated tick states
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            applyKnockback(victim, attacker, isCounter);
        });
    }

    private void applyKnockback(Player victim, Player attacker, boolean isCounter) {
        FileConfiguration config = plugin.getConfig();

        double baseH = config.getDouble("knockback.horizontal", 0.40);
        double counterH = config.getDouble("knockback.hitselect-counter-horizontal", 0.48);
        double baseV = config.getDouble("knockback.vertical", 0.35);

        // Calculate vector from difference between coordinates to avoid NaN
        double dX = victim.getLocation().getX() - attacker.getLocation().getX();
        double dZ = victim.getLocation().getZ() - attacker.getLocation().getZ();

        double distance = Math.sqrt(dX * dX + dZ * dZ);
        if (distance <= 0.0001) {
            // Fallback to attacker yaw if standing on the exact same pixel
            double yaw = Math.toRadians(attacker.getLocation().getYaw() + 90.0);
            dX = Math.cos(yaw);
            dZ = Math.sin(yaw);
            distance = 1.0;
        }

        double dirX = dX / distance;
        double dirZ = dZ / distance;

        double horizontal = isCounter ? counterH : baseH;
        double vertical = baseV;

        if (attacker.isSprinting()) {
            horizontal += 0.075;
            vertical += 0.02;
        }

        if (!victim.isOnGround()) {
            vertical = Math.min(vertical * 0.85, 0.32);
        }

        victim.setVelocity(new Vector(dirX * horizontal, vertical, dirZ * horizontal));
    }
}