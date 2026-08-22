import os

PROJECT_FILES = {
    # 1. Maven Configuration
    "pom.xml": """<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.minestorm.rbw</groupId>
    <artifactId>MineStormRBW</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>MineStormRBW</name>

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
            <id>codemc-nms</id>
            <url>https://repo.codemc.io/repository/nms/</url>
        </repository>
        <repository>
            <id>dmulloy2-repo</id>
            <url>https://repo.dmulloy2.net/repository/public/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot</artifactId>
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
    "src/main/resources/plugin.yml": """name: MineStormRBW
version: 1.0.0
main: com.minestorm.rbw.MineStormRBW.main
author: Muvixo
api-version: 1.8
depend: [ProtocolLib]
commands:
  reloadhit:
    description: Reloads the MineStormRBW config
""",

    # 3. ExecuteHit.java
    "src/main/java/com/minestorm/rbw/MineStormRBW/ExecuteHit.java": """package com.minestorm.rbw.MineStormRBW;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public class ExecuteHit implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender arg0, Command arg1, String arg2, String[] arg3) {
        main.read();
        if(arg0 instanceof Player) {
            Player player = (Player) arg0;
            player.sendMessage(ChatColor.GREEN + "MineStormRBW Config Reloaded!");
        }
        return false;
    }
}
""",

    # 4. runTick.java (Zest Tap Engine injected into HypixelHits framework)
    "src/main/java/com/minestorm/rbw/MineStormRBW/runTick.java": """package com.minestorm.rbw.MineStormRBW;

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
""",

    # 5. main.java
    "src/main/java/com/minestorm/rbw/MineStormRBW/main.java": """package com.minestorm.rbw.MineStormRBW;

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
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

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
    public static String consistantkbdesc = "Consistant KB (Activate Zest Tap Engine): ";
    
    public static String folderPath = Paths.get("").toAbsolutePath().toString() + File.separator + "plugins" + File.separator + "MineStormRBW" + File.separator;
    
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
                        if(runTick.victim.getLocation().getY() > runTick.groundy + 0.4) {
                            runTick.damager.setSprinting(false);
                        } else {
                            runTick.damager.setSprinting(true);
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
            } catch (Exception e) {}
        }
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
            } catch (IOException e) {}
        } catch (FileNotFoundException e) {
            try {
                Files.createDirectories(Paths.get(folderPath));
                try {
                    BufferedWriter bf = new BufferedWriter(new FileWriter(folderPath + "config.txt"));
                    bf.write("enabled: true"); bf.newLine();
                    bf.write(hitdelaydesc + "17"); bf.newLine();
                    bf.write(damagedesc + "0.7"); bf.newLine();
                    bf.write(cpslimitingdesc + "true"); bf.newLine();
                    bf.write(cpslimitdesc + "20"); bf.newLine();
                    bf.write(thirdsprinthitdesc + "false"); bf.newLine();
                    bf.write(delaymovedesc + "2"); bf.newLine();
                    bf.write(consistantkbdesc + "true"); bf.newLine();
                    bf.close();
                } catch (IOException e1) {}
            } catch (IOException e1) {}
        }
    }
}
""",

    # 6. GitHub Actions Build File with Manual ProtocolLib Installation
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

      - name: Manually Install ProtocolLib
        run: |
          wget https://github.com/dmulloy2/ProtocolLib/releases/download/4.8.0/ProtocolLib.jar -O ProtocolLib.jar
          mvn install:install-file -Dfile=ProtocolLib.jar -DgroupId=com.comphenix.protocol -DartifactId=ProtocolLib -Dversion=4.8.0 -Dpackaging=jar

      - name: Build with Maven
        run: mvn clean package -B

      - name: Upload JAR Artifact
        uses: actions/upload-artifact@v4
        with:
          name: MineStormRBW-1.0.0
          path: target/*.jar
          if-no-files-found: error
          retention-days: 7
""",

    # 7. .gitignore
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
    print("[*] Generating MineStormRBW with Zest Tap Engine...")
    for filepath, content in PROJECT_FILES.items():
        dir_name = os.path.dirname(filepath)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
            
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f" [+] Created: {filepath}")

    print("\\n[✔] Project generated successfully!")
    print("Run 'python pusher.py' to commit and push changes to GitHub.")

if __name__ == "__main__":
    create_project()