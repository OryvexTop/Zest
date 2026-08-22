package com.minestorm.rbw.MineStormRBW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public class runTick implements Listener {
    public static double cpslimit = 16;
    private final Map<UUID, List<Long>> playerClicks = new ConcurrentHashMap<>();
    
    // ZEST TAP: Track last time damaged to calculate Hit-Selection delays
    private final Map<UUID, Long> lastHitTaken = new ConcurrentHashMap<>();
    
    public static boolean customhit = true, consistantkb;
    public static int intmaxdmtick;
    public static double damage, groundy;
    public static int hitcount;
    
    public static Player victim;
    public static Player damager;
    
    public main m;
    
    public runTick(main m) {
        this.m = m;
    }

    private void recordClick(UUID uuid) {
        playerClicks.putIfAbsent(uuid, new ArrayList<>());
        playerClicks.get(uuid).add(System.currentTimeMillis());
    }

    private int getCPS(UUID uuid) {
        if (!playerClicks.containsKey(uuid)) return 0;
        long now = System.currentTimeMillis();
        List<Long> clicks = playerClicks.get(uuid);
        clicks.removeIf(timestamp -> now - timestamp > 1000);
        return clicks.size();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerClicks.remove(event.getPlayer().getUniqueId());
        lastHitTaken.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void interact(PlayerAnimationEvent e) {
        if(e.getAnimationType().equals(PlayerAnimationType.ARM_SWING)) {
            recordClick(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            victim = (Player) event.getEntity();
            damager = (Player) event.getDamager();
            UUID damagerUUID = damager.getUniqueId();
            
            // HypixelHits CPS Limiter
            if (main.shouldCheckCPS) {
                int currentCPS = getCPS(damagerUUID);
                if (currentCPS > cpslimit) {
                    event.setCancelled(true);
                    playerClicks.remove(damagerUUID);
                    return;
                }
            }
            
            if (customhit) {
                if (victim.isOnGround()) hitcount = 0;
                else hitcount++;
                if (hitcount >= 4) hitcount = 0;
                
                event.setDamage(event.getDamage() * damage);
                victim.setMaximumNoDamageTicks(intmaxdmtick); // Custom Hit Delay
                
                if (consistantkb) {
                    long now = System.currentTimeMillis();
                    long attackerLastDamage = lastHitTaken.getOrDefault(damagerUUID, 0L);
                    
                    // ZEST TAP LOGIC: Attacker got hit recently? Activate counter-burst
                    boolean isZestCounter = (now - attackerLastDamage) >= 50 && (now - attackerLastDamage) <= 480;
                    lastHitTaken.put(victim.getUniqueId(), now);

                    // ONE-TICK DELAY: This entirely fixes "NO DELAY / NO KB" bug from the old code
                    m.getServer().getScheduler().runTask(m, () -> {
                        if (victim.isOnline() && damager.isOnline()) {
                            applyZestTapKnockback(victim, damager, isZestCounter);
                        }
                    });
                }
            } else {
                victim.setMaximumNoDamageTicks(20);
            }
        }
    }

    private void applyZestTapKnockback(Player victim, Player attacker, boolean isZestCounter) {
        Vector dir = attacker.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.001) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }

        // Zest Base Forces
        double finalH = attacker.isSprinting() ? 0.440 : 0.385;
        double finalV = attacker.isSprinting() ? 0.470 : 0.345;

        // Hit-Select Counter Burst
        if (isZestCounter) {
            finalH = 0.495;  // Extremely sharp horizontal push
            finalV = 0.310;  // Flatten the vertical to lock them in a combo
        }

        // Consistent Airborne Clamp (Never fly too high)
        if (!victim.isOnGround()) {
            finalV = 0.260; 
        }

        victim.setVelocity(new Vector(dir.getX() * finalH, finalV, dir.getZ() * finalH));
    }
}
