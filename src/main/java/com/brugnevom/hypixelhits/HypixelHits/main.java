package com.brugnevom.hypixelhits.HypixelHits;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.server.v1_8_R3.EntityPlayer;

public class main extends JavaPlugin {
    public static Plugin thisplugin;
    
    private final Map<UUID, LinkedList<Location>> historyMap = new HashMap<>();
    private static int DELAY;
    
    public static boolean shouldCheckCPS = true, shouldThirdSprintHit;
    
    public static String hitdelaydesc = "hit delay (how much delay of hurt time before each hit): ";
    public static String damagedesc = "damage multiplier (damage dealt multiplies by this value everytime a player combos): ";
    public static String cpslimitingdesc = "CPS limiting (enable checking whether the comboer is clicking too much): ";
    public static String cpslimitdesc = "CPS limit (hypixel comobing won't work if the player is clicking beyond this value in a second): ";
    public static String thirdsprinthitdesc = "Third Sprint Hit (Enable sprint hit for the third combo hit): ";
    public static String delaymovedesc = "Movement Tick Delay (Delay every player's movement by this value): ";
    public static String consistantkbdesc = "Consistant KB (Combo KB feels more consistant, hit trading might be weird): ";
    
    public static String folderPath = Paths.get("").toAbsolutePath().toString() + File.separator + "plugins" + File.separator + "HypixelHit" + File.separator;
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new runTick(this), this);
        getCommand("reloadhit").setExecutor(new ExecuteHit());
        read();
        thisplugin = this;
        
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            
            @Override
            public void run() {
                if(runTick.damager != null && runTick.victim != null) {
                    if(runTick.victim.isOnGround()) {
                        runTick.groundy = runTick.victim.getLocation().getY();
                        runTick.hitcount = 0;
                    }
                    
                    if(!shouldThirdSprintHit) {
                        if(runTick.victim != null && runTick.damager != null && runTick.nmsPlayer != null && runTick.nmsdPlayer != null) {
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
            } catch (Exception e) {
            }
        }
    }
    private Player getPlayerByEntityId(int id) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getEntityId() == id) return p;
        }
        return null;
    }

    public static void read() {
        try {
            BufferedReader bfr = new BufferedReader(new FileReader(folderPath + "config.txt"));
            
            try {
                runTick.customhit = Boolean.parseBoolean(bfr.readLine().replace("enabled: ", ""));
                runTick.intmaxdmtick = Integer.parseInt(bfr.readLine().replace(hitdelaydesc, ""));
                runTick.damage = Double.parseDouble(bfr.readLine().replace(damagedesc, ""));
                shouldCheckCPS = Boolean.parseBoolean(bfr.readLine().replace(cpslimitingdesc, ""));
                runTick.cpslimit = Double.parseDouble(bfr.readLine().replace(cpslimitdesc, ""));
                shouldThirdSprintHit = Boolean.parseBoolean(bfr.readLine().replace(thirdsprinthitdesc, ""));
                DELAY = Integer.parseInt(bfr.readLine().replace(delaymovedesc, ""));
                runTick.consistantkb = Boolean.parseBoolean(bfr.readLine().replace(consistantkbdesc, ""));
                
                bfr.close();
            } catch (IOException e) {
            }
        } catch (FileNotFoundException e) {
            try {
                Files.createDirectories(Paths.get(folderPath));
                
                try {
                    BufferedWriter bf = new BufferedWriter(new FileWriter(folderPath + "config.txt"));
                    
                    bf.write("enabled: " + true); bf.newLine();
                    bf.write(hitdelaydesc + 17); bf.newLine();
                    bf.write(damagedesc + 0.7); bf.newLine();
                    bf.write(cpslimitingdesc + true); bf.newLine();
                    bf.write(cpslimitdesc + 20); bf.newLine();
                    bf.write(thirdsprinthitdesc + false); bf.newLine();
                    bf.write(delaymovedesc + 2); bf.newLine();
                    bf.write(consistantkbdesc + true); bf.newLine();
                    
                    bf.close();
                } catch (IOException e1) {
                }
            } catch (IOException e1) {
            }
        }
    }

}
