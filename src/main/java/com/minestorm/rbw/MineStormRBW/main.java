package com.minestorm.rbw.MineStormRBW;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

public class main extends JavaPlugin {
    public static main instance;
    
    private final Map<UUID, LinkedList<Location>> historyMap = new HashMap<>();
    public static int DELAY;
    public static boolean shouldCheckCPS, shouldThirdSprintHit;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Setup Native standard config.yml
        saveDefaultConfig();
        loadConfigValues();
        
        getServer().getPluginManager().registerEvents(new runTick(this), this);
        getCommand("reloadhit").setExecutor(new ExecuteHit());
        
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if(runTick.damager != null && runTick.victim != null) {
                    if(runTick.victim.isOnGround()) {
                        runTick.groundy = runTick.victim.getLocation().getY();
                        runTick.hitcount = 0;
                    }
                    if(!shouldThirdSprintHit) {
                        if(runTick.victim != null && runTick.damager != null) {
                            if(runTick.victim.getLocation().getY() > runTick.groundy + 0.4) {
                                runTick.damager.setSprinting(false);
                            } else runTick.damager.setSprinting(true);
                        }
                    }
                }
            }
        }, 0, 0);
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if(DELAY > 0) {
                for (Player subject : Bukkit.getOnlinePlayers()) {
                    UUID uuid = subject.getUniqueId();
                    historyMap.putIfAbsent(uuid, new LinkedList<>());
                    LinkedList<Location> history = historyMap.get(uuid);
    
                    history.addLast(subject.getLocation().clone());
                    if (!history.isEmpty()) {
                        Location delayedLoc = (history.size() > DELAY) ? history.removeFirst() : history.getFirst();
                        broadcastDelayedPosition(subject, delayedLoc);
                    }
                }
            }
        }, 0L, 1L);

        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this,
                    ListenerPriority.HIGHEST,
                    PacketType.Play.Server.ENTITY_TELEPORT,
                    PacketType.Play.Server.REL_ENTITY_MOVE,
                    PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                    PacketType.Play.Server.ENTITY_LOOK,
                    PacketType.Play.Server.ENTITY_HEAD_ROTATION) {

                @Override
                public void onPacketSending(PacketEvent event) {
                    if(DELAY > 0) {
                        PacketContainer packet = event.getPacket();
                        int entityId = packet.getIntegers().read(0);
                        Player subject = null;
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getEntityId() == entityId) {
                                subject = p;
                                break;
                            }
                        }
                        if (subject != null) {
                            if (event.getPlayer().getUniqueId().equals(subject.getUniqueId())) return;
                            event.setCancelled(true);
                        }
                    }
                }
            });
        }
    }
    
    public void loadConfigValues() {
        reloadConfig();
        runTick.customhit = getConfig().getBoolean("enabled", true);
        runTick.intmaxdmtick = getConfig().getInt("hit-delay", 17);
        runTick.damage = getConfig().getDouble("damage-multiplier", 0.7);
        shouldCheckCPS = getConfig().getBoolean("cps-limiting.enabled", true);
        runTick.cpslimit = getConfig().getDouble("cps-limiting.limit", 20.0);
        shouldThirdSprintHit = getConfig().getBoolean("third-sprint-hit", false);
        DELAY = getConfig().getInt("movement-tick-delay", 2);
        runTick.consistantkb = getConfig().getBoolean("consistent-kb", true);
    }
    
    private void broadcastDelayedPosition(Player subject, Location loc) {
        PacketContainer teleport = new PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT);
        teleport.getIntegers().write(0, subject.getEntityId());
        teleport.getIntegers().write(1, (int) Math.floor(loc.getX() * 32.0D));
        teleport.getIntegers().write(2, (int) Math.floor(loc.getY() * 32.0D));
        teleport.getIntegers().write(3, (int) Math.floor(loc.getZ() * 32.0D));
        teleport.getBytes().write(0, (byte) (loc.getYaw() * 256.0F / 360.0F));
        teleport.getBytes().write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
        teleport.getBooleans().write(0, true);

        PacketContainer headLook = new PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        headLook.getIntegers().write(0, subject.getEntityId());
        headLook.getBytes().write(0, (byte) (loc.getYaw() * 256.0F / 360.0F));

        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.getUniqueId().equals(subject.getUniqueId())) continue;
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, teleport, false);
                ProtocolLibrary.getProtocolManager().sendServerPacket(observer, headLook, false);
            } catch (Exception e) {}
        }
    }
}
