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
    // Track timestamps of last damage taken for Hit-Selecting calculation
    private final Map<UUID, Long> lastDamageTaken = new HashMap<>();

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

        if (victim.getNoDamageTicks() > 10) {
            return;
        }

        long now = System.currentTimeMillis();
        long attackerLastHitTaken = lastDamageTaken.getOrDefault(attacker.getUniqueId(), 0L);
        boolean isCounterAttack = (now - attackerLastHitTaken) < 450; // Successful Hit-Select window (within ~400ms)

        lastDamageTaken.put(victim.getUniqueId(), now);

        // Schedule 1 tick later to override 1.8 vanilla velocity
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            applyHitSelectKnockback(victim, attacker, isCounterAttack);
        });
    }

    private void applyHitSelectKnockback(Player victim, Player attacker, boolean isCounterAttack) {
        FileConfiguration config = plugin.getConfig();

        double baseH = config.getDouble("knockback.horizontal", 0.400);
        double baseV = config.getDouble("knockback.vertical", 0.320);
        double counterBurstH = config.getDouble("knockback.hitselect-counter-horizontal", 0.490);
        double blockReduction = config.getDouble("knockback.blockhit-dampening", 0.550);

        Vector direction = attacker.getLocation().getDirection().setY(0).normalize();

        double horizontalPush = isCounterAttack ? counterBurstH : baseH;
        double verticalPush = baseV;

        // Block-hitting dampening (Reduces KB when defending/timing hits)
        if (victim.isBlocking()) {
            horizontalPush *= blockReduction;
            verticalPush *= 0.65;
        }

        // Clamp vertical launch to ensure flat trajectory
        if (!victim.isOnGround()) {
            verticalPush = Math.min(verticalPush * 0.70, 0.250);
        }

        victim.setVelocity(new Vector(
                direction.getX() * horizontalPush,
                verticalPush,
                direction.getZ() * horizontalPush
        ));
    }
}