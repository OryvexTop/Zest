package com.zest.knockback;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class ZestKnockbackListener implements Listener {

    private final ZestPlugin plugin;
    public static double cpsLimit = 20.0D;
    private final Map<UUID, List<Long>> playerClicks = new HashMap<>();

    public static boolean customHit = true;
    public static boolean consistantKB = true;
    public static int maxDmTick = 17;
    public static double damageMult = 0.7D;
    public static double groundY;
    public static int hitCount = 0;

    public static Player victim;
    public static Player damager;

    public ZestKnockbackListener(ZestPlugin plugin) {
        this.plugin = plugin;
    }

    private void recordClick(UUID uuid) {
        playerClicks.putIfAbsent(uuid, new ArrayList<>());
        playerClicks.get(uuid).add(System.currentTimeMillis());
    }

    private int getCPS(UUID uuid) {
        if (!playerClicks.containsKey(uuid)) return 0;
        long now = System.currentTimeMillis();
        List<Long> clicks = playerClicks.get(uuid);
        clicks.removeIf(timestamp -> (now - timestamp > 1000L));
        return clicks.size();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerClicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType().equals(PlayerAnimationType.ARM_SWING)) {
            recordClick(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            victim = (Player) event.getEntity();
            damager = (Player) event.getDamager();
            UUID damagerUUID = damager.getUniqueId();

            if (ZestPlugin.shouldCheckCPS) {
                int currentCPS = getCPS(damagerUUID);
                if (currentCPS > cpsLimit) {
                    event.setCancelled(true);
                    playerClicks.remove(damagerUUID);
                    return;
                }
            }

            if (customHit) {
                if (victim.isOnGround()) {
                    hitCount = 0;
                } else {
                    hitCount++;
                }

                if (hitCount >= 4) {
                    hitCount = 0;
                }

                event.setDamage(event.getDamage() * damageMult);
                victim.setMaximumNoDamageTicks(maxDmTick);

                if (consistantKB && hitCount >= 1 && !victim.isOnGround()) {
                    if (damager.getLocation().distance(victim.getLocation()) > 2.5D) {
                        Vector kb = new Vector(0, 0, 0);
                        if (hitCount == 1) kb.setY(-0.3D);
                        if (hitCount == 2) kb.setY(-0.7D);
                        victim.setVelocity(kb);
                    }
                }
            } else {
                victim.setMaximumNoDamageTicks(20);
            }
        }
    }
}
