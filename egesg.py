import os

PROJECT_FILES = {
    # 1. Maven Configuration with PaperSpigot & ProtocolLib 1.8.8
    "pom.xml": """<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.zest.knockback</groupId>
    <artifactId>ZestKnockback</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>ZestKnockback</name>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>papermc-repo</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
        <repository>
            <id>dmulloy2-repo</id>
            <url>https://repo.dmulloy2.net/repository/public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.github.paperspigot</groupId>
            <artifactId>paperspigot-api</artifactId>
            <version>1.8.8-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.comphenix.protocol</groupId>
            <artifactId>ProtocolLib</artifactId>
            <version>4.8.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <defaultGoal>clean package</defaultGoal>
        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <filtering>true</filtering>
            </resource>
        </resources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
""",

    # 2. Plugin Manifest
    "src/main/resources/plugin.yml": """name: ZestKnockback
version: 1.0.0
main: com.zest.knockback.ZestPlugin
author: Muvixo
api-version: 1.8
softdepend: [ProtocolLib]
commands:
  zestreload:
    description: Reloads the knockback and hit config
    permission: zest.admin
    aliases: [reloadhit]
""",

    # 3. Main Plugin Class (Packet movement simulation & Sprint handler)
    "src/main/java/com/zest/knockback/ZestPlugin.java": """package com.zest.knockback;

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
""",

    # 4. Listener Class (CPS Tracker & Consistent Y Knockback)
    "src/main/java/com/zest/knockback/ZestKnockbackListener.java": """package com.zest.knockback;

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
""",

    # 5. Reload Command Executor
    "src/main/java/com/zest/knockback/ExecuteHit.java": """package com.zest.knockback;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ExecuteHit implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        ZestPlugin.readConfig();
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GREEN + "[ZestKnockback] Config reloaded successfully!");
        } else {
            sender.sendMessage("[ZestKnockback] Config reloaded successfully!");
        }
        return true;
    }
}
""",

    # 6. GitHub Actions CI Build Workflow
    ".github/workflows/build.yml": """name: Build & Release Plugin

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Setup Java JDK 8
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: 'maven'

      - name: Build with Maven
        run: mvn clean package -B

      - name: Upload JAR Artifact
        uses: actions/upload-artifact@v4
        with:
          name: ZestKnockback-1.0.0
          path: target/*.jar
          if-no-files-found: error
          retention-days: 7
""",

    # 7. Git Ignore
    ".gitignore": """target/
*.jar
.idea/
*.iml
.settings/
.project
.classpath
"""
}

def create_project():
    print("[*] Generating HypixelHits-powered ZestKnockback project structure...")
    for filepath, content in PROJECT_FILES.items():
        dir_name = os.path.dirname(filepath)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
            
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f" [+] Created: {filepath}")

    print("\n[✔] Project generated successfully!")
    print("Run 'python pusher.py' to commit and push changes to GitHub.")

if __name__ == "__main__":
    create_project()