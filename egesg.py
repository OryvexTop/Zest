import os
import stat

PROJECT_FILES = {
    # 1. Maven POM Configuration
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
            <id>spigot-repo</id>
            <url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url>
        </repository>
        <repository>
            <id>sonatype-snapshots</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.8.8-R0.1-SNAPSHOT</version>
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
description: High-performance competitive Zest-style W-Tap knockback engine for Spigot 1.8.8
""",

    # 3. Default Configuration File
    "src/main/resources/config.yml": """# Zest Knockback Engine Settings
knockback:
  horizontal: 0.385
  vertical: 0.345
  sprint-horizontal: 0.440
  sprint-vertical: 0.125
  max-vertical-limit: 0.400
  friction-factor: 0.960
""",

    # 4. Main Plugin Entrypoint
    "src/main/java/com/zest/knockback/ZestPlugin.java": """package com.zest.knockback;

import org.bukkit.plugin.java.JavaPlugin;

public class ZestPlugin extends JavaPlugin {

    private static ZestPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new ZestKnockbackListener(this), this);
        getLogger().info("ZestKnockback has been successfully activated!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ZestKnockback has been disabled.");
    }

    public static ZestPlugin getInstance() {
        return instance;
    }
}
""",

    # 5. Knockback Listener
    "src/main/java/com/zest/knockback/ZestKnockbackListener.java": """package com.zest.knockback;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class ZestKnockbackListener implements Listener {

    private final ZestPlugin plugin;

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

        // Check if victim is currently in damage immunity window
        if (victim.getNoDamageTicks() > 10) {
            return;
        }

        applyZestVelocity(victim, attacker);
    }

    private void applyZestVelocity(Player victim, Player attacker) {
        FileConfiguration config = plugin.getConfig();

        double horizontalBase = config.getDouble("knockback.horizontal", 0.385);
        double sprintHorizontal = config.getDouble("knockback.sprint-horizontal", 0.440);
        double verticalBase = config.getDouble("knockback.vertical", 0.345);
        double sprintVertical = config.getDouble("knockback.sprint-vertical", 0.125);
        double maxVerticalLimit = config.getDouble("knockback.max-vertical-limit", 0.400);
        double frictionFactor = config.getDouble("knockback.friction-factor", 0.960);

        double deltaX = victim.getLocation().getX() - attacker.getLocation().getX();
        double deltaZ = victim.getLocation().getZ() - attacker.getLocation().getZ();

        double distance = Math.hypot(deltaX, deltaZ);
        if (distance <= 0.001) {
            deltaX = 0.01;
            deltaZ = 0.01;
            distance = 0.014;
        }

        double dirX = deltaX / distance;
        double dirZ = deltaZ / distance;

        double horizontalPush = attacker.isSprinting() ? sprintHorizontal : horizontalBase;
        double verticalPush = attacker.isSprinting() ? (verticalBase + sprintVertical) : verticalBase;

        if (!victim.isOnGround()) {
            verticalPush *= 0.85;
        }

        if (verticalPush > maxVerticalLimit) {
            verticalPush = maxVerticalLimit;
        }

        Vector currentVel = victim.getVelocity();
        double finalVelX = (currentVel.getX() * (1.0 - frictionFactor)) + (dirX * horizontalPush);
        double finalVelZ = (currentVel.getZ() * (1.0 - frictionFactor)) + (dirZ * horizontalPush);

        victim.setVelocity(new Vector(finalVelX, verticalPush, finalVelZ));
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
          name: ZestKnockback-Jar
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
    print("[*] Generating ZestKnockback project structure...")
    for filepath, content in PROJECT_FILES.items():
        # Ensure directories exist
        dir_name = os.path.dirname(filepath)
        if dir_name:
            os.makedirs(dir_name, exist_ok=True)
            
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f" [+] Created: {filepath}")

    print("\n[✔] Project generated successfully!")
    print("\nNext steps to compile with GitHub Actions:")
    print(" 1. git init")
    print(" 2. git add .")
    print(" 3. git commit -m 'Initial commit: Zest Knockback Engine'")
    print(" 4. git branch -M main")
    print(" 5. git remote add origin <YOUR_GITHUB_REPO_URL>")
    print(" 6. git push -u origin main")

if __name__ == "__main__":
    create_project()