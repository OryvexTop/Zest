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
    public static double cpslimit = 16.0;
    // THREAD SAFE: Prevents crashing when accessing player clicks during packet events
    private final Map<UUID, List<Long>> playerClicks = new ConcurrentHashMap<>();
    
    public static boolean customhit = true, consistantkb = true;
    public static int intmaxdmtick = 17;
    public static double damage = 0.7, groundy;
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
            
            // Block spam hits if victim is still in NoDamageTicks window!
            if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0F) {
                return;
            }
            
            if (main.shouldCheckCPS) {
                int currentCPS = getCPS(damagerUUID);
                if (currentCPS > cpslimit) {
                    event.setCancelled(true);
                    playerClicks.remove(damagerUUID);
                    return;
                }
            }
            
            if(customhit) {
                if(victim.isOnGround()) hitcount = 0;
                else hitcount++;
                if(hitcount >= 4) hitcount = 0;
                
                event.setDamage(event.getDamage() * damage);
                
                // Properly enforce the Hit Delay
                victim.setMaximumNoDamageTicks(intmaxdmtick);
                victim.setNoDamageTicks(intmaxdmtick);
                
                if(consistantkb) {
                    // Apply 1-tick delay knockback with proper horizontal math!
                    m.getServer().getScheduler().runTask(m, () -> {
                        if (!victim.isOnline() || !damager.isOnline()) return;

                        Vector direction = damager.getLocation().getDirection().setY(0).normalize();
                        double horizontal = damager.isSprinting() ? 0.45 : 0.38;
                        double vertical = 0.34; // Base jump height

                        if(hitcount >= 1 && !victim.isOnGround()) {
                            if(damager.getLocation().distance(victim.getLocation()) > 2.5) {
                                // Hypixel consistent KB: dampen the Y axis to stay in combo, BUT KEEP X/Z
                                if(hitcount == 1) vertical = -0.10;
                                if(hitcount == 2) vertical = -0.30;
                            }
                        }
                        
                        victim.setVelocity(new Vector(direction.getX() * horizontal, vertical, direction.getZ() * horizontal));
                    });
                }
            } else {
                victim.setMaximumNoDamageTicks(20);
            }
        }
    }
}
