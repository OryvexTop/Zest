package com.minestorm.rbw.MineStormRBW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final Map<UUID, List<Long>> playerClicks = new HashMap<>();
    
    public static boolean customhit = true, consistantkb;
    public static int intmaxdmtick;
    public static double damage, groundy;
    public static int hitcount;
    
    public static Player victim;
    public static Player damager;
    public static net.minecraft.server.v1_8_R3.EntityPlayer nmsPlayer;
    public static net.minecraft.server.v1_8_R3.EntityPlayer nmsdPlayer;
    
    Map<UUID, Integer> hitCount = new HashMap<>();
    
    public static int hitcombo;
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
            UUID uuid = e.getPlayer().getUniqueId();
            recordClick(uuid);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            victim = (Player) event.getEntity();
            damager = (Player) event.getDamager();
            nmsPlayer = ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) damager).getHandle();
            nmsdPlayer = ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) victim).getHandle();
            UUID damagerUUID = damager.getUniqueId();
            
            int currentCPS = getCPS(damagerUUID);
            if (currentCPS > cpslimit) {
                event.setCancelled(true);
                playerClicks.remove(damagerUUID);
                return;
            }
            
            if(customhit) {
                if(victim.isOnGround()) hitcount = 0;
                else hitcount++;
                if(hitcount >= 4) hitcount = 0;
                
                event.setDamage(event.getDamage() * damage);
                victim.setMaximumNoDamageTicks(intmaxdmtick);
                
                if(consistantkb) {
                    if(hitcount >= 1 && !victim.isOnGround()) {
                        if(damager.getLocation().distance(victim.getLocation()) > 2.5) {
                            if(nmsdPlayer.hurtTicks > 0) {
                                Vector kb = new Vector(0, 0, 0);
                                if(hitcount == 1) kb.setY(-0.3);
                                if(hitcount == 2) kb.setY(-0.7);
                                victim.setVelocity(kb);
                            }
                        }
                    }
                }
            } else {
                victim.setMaximumNoDamageTicks(20);
                event.setDamage(event.getDamage());
            }
        }
    }

}
