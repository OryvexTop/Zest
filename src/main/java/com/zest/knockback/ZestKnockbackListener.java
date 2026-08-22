package com.zest.knockback;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class ZestKnockbackListener implements Listener {

    private final ZestPlugin plugin;

    public ZestKnockbackListener(ZestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // Check if victim is currently in damage immunity window
        if (victim.getNoDamageTicks() > 10) {
            return;
        }

        applyZestVelocity(victim, attacker);
    }

    private void applyZestVelocity(Player victim, Player attacker) {
        FileConfiguration config = plugin.getConfig();

        double horizontalBase = config.getDouble("knockback.horizontal", 0.385);
        double sprintHorizontal = config.getDouble("knockback.sprint-horizontal", 0.440);
        double verticalBase = config.getDouble("knockback.vertical", 0.345);
        double sprintVertical = config.getDouble("knockback.sprint-vertical", 0.125);
        double maxVerticalLimit = config.getDouble("knockback.max-vertical-limit", 0.400);
        double frictionFactor = config.getDouble("knockback.friction-factor", 0.960);

        double deltaX = victim.getLocation().getX() - attacker.getLocation().getX();
        double deltaZ = victim.getLocation().getZ() - attacker.getLocation().getZ();

        double distance = Math.hypot(deltaX, deltaZ);
        if (distance <= 0.001) {
            deltaX = 0.01;
            deltaZ = 0.01;
            distance = 0.014;
        }

        double dirX = deltaX / distance;
        double dirZ = deltaZ / distance;

        double horizontalPush = attacker.isSprinting() ? sprintHorizontal : horizontalBase;
        double verticalPush = attacker.isSprinting() ? (verticalBase + sprintVertical) : verticalBase;

        if (!victim.isOnGround()) {
            verticalPush *= 0.85;
        }

        if (verticalPush > maxVerticalLimit) {
            verticalPush = maxVerticalLimit;
        }

        Vector currentVel = victim.getVelocity();
        double finalVelX = (currentVel.getX() * (1.0 - frictionFactor)) + (dirX * horizontalPush);
        double finalVelZ = (currentVel.getZ() * (1.0 - frictionFactor)) + (dirZ * horizontalPush);

        victim.setVelocity(new Vector(finalVelX, verticalPush, finalVelZ));
    }
}
