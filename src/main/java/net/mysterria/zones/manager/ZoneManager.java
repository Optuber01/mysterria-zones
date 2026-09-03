package net.mysterria.zones.manager;

import net.mysterria.zones.MysterriaZones;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import net.mysterria.zones.audit.ZoneAuditEmitter;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;

public class ZoneManager {
    private final MysterriaZones plugin;
    private final Map<String, Zone> zones;
    private final File zonesFolder;
    private final Logger logger;

    public ZoneManager(MysterriaZones plugin) {
        this.plugin = plugin;
        this.zones = new HashMap<>();
        this.logger = plugin.getLogger();
        this.zonesFolder = new File(plugin.getDataFolder(), "zones");

        if (!zonesFolder.exists()) {
            zonesFolder.mkdirs();
        }

        loadZones();
    }

    public void loadZones() {
        zones.clear();
        File[] zoneFiles = zonesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (zoneFiles == null) {
            logger.info("No zones to load.");
            return;
        }

        for (File zoneFile : zoneFiles) {
            try {
                String zoneName = zoneFile.getName().replace(".yml", "");
                FileConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneFile);
                Map<String, Object> zoneData = new HashMap<>();
                for (String key : zoneConfig.getKeys(true)) {
                    zoneData.put(key, zoneConfig.get(key));
                }
                Zone zone = new Zone(zoneData);
                zones.put(zoneName, zone);
                logger.info("Loaded zone: " + zoneName);
            } catch (Exception e) {
                logger.warning("Failed to load zone from " + zoneFile.getName() + ": " + e.getMessage());
            }
        }
        logger.info("Loaded " + zones.size() + " zones.");
    }

    public void saveZone(Zone zone) {
        saveZone(zone, null, null, null);
    }

    /** Persists a zone and emits an optional event only after the write succeeds. */
    public boolean saveZone(Zone zone, UUID actorId, String operation, Map<String, ?> metadata) {
        File zoneFile = new File(zonesFolder, zone.getName() + ".yml");
        FileConfiguration zoneConfig = new YamlConfiguration();
        Map<String, Object> serialized = zone.serialize();
        for (Map.Entry<String, Object> entry : serialized.entrySet()) {
            zoneConfig.set(entry.getKey(), entry.getValue());
        }
        try {
            saveAtomically(zoneConfig, zoneFile);
            logger.info("Saved zone: " + zone.getName());
            if (actorId != null && operation != null) {
                audit().emit(operation, AuditOutcome.COMMITTED, actorId, null, zone, metadata);
            }
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save zone " + zone.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private void saveAtomically(FileConfiguration zoneConfig, File zoneFile) throws IOException {
        Path target = zoneFile.toPath();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, zoneFile.getName() + ".", ".tmp");

        try {
            ByteBuffer contents = StandardCharsets.UTF_8.encode(zoneConfig.saveToString());
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                while (contents.hasRemaining()) {
                    channel.write(contents);
                }
                channel.force(true);
            }

            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void createZone(String name, Location point1, Location point2) {
        createZone(name, point1, point2, null);
    }

    public boolean createZone(String name, Location point1, Location point2, UUID actorId) {
        Zone zone = new Zone(name, point1, point2);
        if (!saveZone(zone, null, null, null)) {
            return false;
        }
        zones.put(name, zone);
        if (actorId != null) {
            audit().emit("zone.created", AuditOutcome.COMMITTED, actorId, null, zone, Map.of());
        }
        return true;
    }

    public boolean deleteZone(String name) {
        return deleteZone(name, null);
    }

    public boolean deleteZone(String name, UUID actorId) {
        Zone removed = zones.get(name);
        if (removed == null) {
            return false;
        }

        File zoneFile = new File(zonesFolder, name + ".yml");
        if (zoneFile.exists() && !zoneFile.delete()) {
            return false;
        }

        zones.remove(name);
        if (actorId != null) {
            audit().emit("zone.deleted", AuditOutcome.COMMITTED, actorId, null, removed, Map.of());
        }
        return true;
    }

    public Zone getZone(String name) {
        return zones.get(name);
    }

    public Collection<Zone> getAllZones() {
        return zones.values();
    }

    public Set<String> getZoneNames() {
        return zones.keySet();
    }

    public List<Zone> getZonesAtLocation(Location location) {
        List<Zone> foundZones = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.contains(location)) {
                foundZones.add(zone);
            }
        }
        foundZones.sort((z1, z2) -> Integer.compare(z2.getPriority(), z1.getPriority()));
        return foundZones;
    }

    public Zone getHighestPriorityZone(Location location) {
        return getZonesAtLocation(location).stream()
                .findFirst()
                .orElse(null);
    }

    public boolean hasZone(String name) {
        return zones.containsKey(name);
    }

    public void updateZone(Zone zone) {
        updateZone(zone, null, null, null);
    }

    public boolean updateZone(Zone zone, UUID actorId, String operation, Map<String, ?> metadata) {
        boolean persisted = saveZone(zone, null, null, null);
        if (!persisted) {
            return false;
        }

        zones.put(zone.getName(), zone);
        if (actorId != null && operation != null) {
            audit().emit(operation, AuditOutcome.COMMITTED, actorId, null, zone, metadata);
        }
        return true;
    }

    public void banishPlayer(Zone zone, UUID playerId) {
        banishPlayer(zone, playerId, null);
    }

    public boolean banishPlayer(Zone zone, UUID playerId, UUID actorId) {
        if (zone.isBanished(playerId)) return false;
        zone.banishPlayer(playerId);
        boolean persisted = saveZone(zone, null, null, null);
        if (!persisted) {
            zone.unbanishPlayer(playerId);
            return false;
        }
        if (actorId != null) {
            audit().emit("zone.banished", AuditOutcome.COMMITTED, actorId, playerId, zone, Map.of());
        }
        return true;
    }

    public void unbanishPlayer(Zone zone, UUID playerId) {
        unbanishPlayer(zone, playerId, null);
    }

    public boolean unbanishPlayer(Zone zone, UUID playerId, UUID actorId) {
        if (!zone.isBanished(playerId)) return false;
        zone.unbanishPlayer(playerId);
        boolean persisted = saveZone(zone, null, null, null);
        if (!persisted) {
            zone.banishPlayer(playerId);
            return false;
        }
        if (actorId != null) {
            audit().emit("zone.unbanished", AuditOutcome.COMMITTED, actorId, playerId, zone, Map.of());
        }
        return true;
    }

    private ZoneAuditEmitter audit() {
        return plugin.getAuditEmitter();
    }

    public Set<UUID> getBanishedPlayers(Zone zone) {
        return zone.getBanishedPlayers();
    }
}
