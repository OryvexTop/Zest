package com.zest.knockback;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

public class ZestPlugin extends JavaPlugin {
    public static ZestPlugin instance;
    private final Map<UUID, LinkedList<Location>> historyMap = new HashMap<>();

    public static int DELAY = 2;
    public static boolean shouldCheckCPS = true;
    public static boolean shouldThirdSprintHit = false;

    public static final String HITDELAY_DESC = "hit delay (how much delay of hurt time before each hit): ";
    public static final String DAMAGE_DESC = "damage multiplier (damage dealt multiplies by this value everytime a player combos): ";
    public static final String CPS_LIMITING_DESC = "CPS limiting (enable checking whether the comboer is clicking too much): ";
    public static final String CPS_LIMIT_DESC = "CPS limit (hypixel comobing won't work if the player is clicking beyond this value in a second): ";
    public static final String THIRD_SPRINT_HIT_DESC = "Third Sprint Hit (Enable sprint hit for the third combo hit): ";
    public static final String DELAY_MOVE_DESC = "Movement Tick Delay (Delay every player's movement by this value): ";
    public static final String CONSISTANT_KB_DESC = "Consistant KB (Combo KB feels more consistant, hit trading might be weird): ";

    public static String folderPath;

    @Override
    public void onEnable() {
        instance = this;
        folderPath = getDataFolder().getAbsolutePath() + File.separator;

        readConfig();
        getServer().getPluginManager().registerEvents(new ZestKnockbackListener(this), this);
        getCommand("zestreload").setExecutor(new ExecuteHit());

        // Sprint and Ground tracker loop
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (ZestKnockbackListener.victim != null && ZestKnockbackListener.damager != null) {
                if (ZestKnockbackListener.victim.isOnGround()) {
                    ZestKnockbackListener.groundY = ZestKnockbackListener.victim.getLocation().getY();
                    ZestKnockbackListener.hitCount = 0;
                }

                if (!shouldThirdSprintHit) {
                    if (ZestKnockbackListener.victim.getLocation().getY() > ZestKnockbackListener.groundY + 0.4D) {
                        ZestKnockbackListener.damager.setSprinting(false);
                    } else {
                        ZestKnockbackListener.damager.setSprinting(true);
                    }
                }
            }
        }, 0L, 1L);

        // ProtocolLib Packet Movement Simulation
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (DELAY > 0) {
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
                    if (DELAY > 0) {
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
            } catch (Exception ignored) {
            }
        }
    }

    public static void readConfig() {
        File configFile = new File(folderPath + "config.txt");
        if (!configFile.exists()) {
            try {
                Files.createDirectories(Paths.get(folderPath));
                BufferedWriter bf = new BufferedWriter(new FileWriter(configFile));
                bf.write("enabled: true"); bf.newLine();
                bf.write(HITDELAY_DESC + "17"); bf.newLine();
                bf.write(DAMAGE_DESC + "0.7"); bf.newLine();
                bf.write(CPS_LIMITING_DESC + "true"); bf.newLine();
                bf.write(CPS_LIMIT_DESC + "20"); bf.newLine();
                bf.write(THIRD_SPRINT_HIT_DESC + "false"); bf.newLine();
                bf.write(DELAY_MOVE_DESC + "2"); bf.newLine();
                bf.write(CONSISTANT_KB_DESC + "true"); bf.newLine();
                bf.close();
            } catch (IOException ignored) {
            }
        }

        try (BufferedReader bfr = new BufferedReader(new FileReader(configFile))) {
            ZestKnockbackListener.customHit = Boolean.parseBoolean(bfr.readLine().replace("enabled: ", ""));
            ZestKnockbackListener.maxDmTick = Integer.parseInt(bfr.readLine().replace(HITDELAY_DESC, ""));
            ZestKnockbackListener.damageMult = Double.parseDouble(bfr.readLine().replace(DAMAGE_DESC, ""));
            shouldCheckCPS = Boolean.parseBoolean(bfr.readLine().replace(CPS_LIMITING_DESC, ""));
            ZestKnockbackListener.cpsLimit = Double.parseDouble(bfr.readLine().replace(CPS_LIMIT_DESC, ""));
            shouldThirdSprintHit = Boolean.parseBoolean(bfr.readLine().replace(THIRD_SPRINT_HIT_DESC, ""));
            DELAY = Integer.parseInt(bfr.readLine().replace(DELAY_MOVE_DESC, ""));
            ZestKnockbackListener.consistantKB = Boolean.parseBoolean(bfr.readLine().replace(CONSISTANT_KB_DESC, ""));
        } catch (Exception ignored) {
        }
    }
}
