package net.mysterria.zones.manager;

import net.mysterria.zones.MysterriaZones;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
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
    /** Audit reason and metadata value for a failed YAML write or delete. */
    private static final String PERSIST_FAILED_REASON = "persist_failed";
    /** Mirrors the audit client's per-value budget for the deleted-zone ban snapshot. */
    private static final int MAX_BANISHED_SNAPSHOT_CHARS = 1_024;

    /** Result of a staff banish request. */
    public enum BanishResult { BANISHED, ALREADY_BANISHED, PERSIST_FAILED }

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
            logger.fine("No zones to load.");
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
                logger.fine("Loaded zone: " + zoneName);
            } catch (Exception e) {
                logger.warning("Failed to load zone from " + zoneFile.getName() + ": " + e.getMessage());
            }
        }
        logger.fine("Loaded " + zones.size() + " zones.");
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
            logger.fine("Saved zone: " + zone.getName());
            if (actorId != null && operation != null) {
                audit().emit(operation, AuditOutcome.COMMITTED, actorId, null, zone, metadata);
            }
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save zone " + zone.getName() + ": " + e.getMessage());
            if (actorId != null && operation != null) {
                emitPersistFailed(operation, AuditPrivacy.STAFF_RESTRICTED, actorId, null, zone, metadata);
            }
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
        Map<String, Object> positions = actorId == null ? Map.of() : selectedPositions(point1, point2);
        if (!saveZone(zone, null, null, null)) {
            if (actorId != null) {
                emitPersistFailed("zone.created", AuditPrivacy.STAFF_RESTRICTED, actorId, null, zone, positions);
            }
            return false;
        }
        zones.put(name, zone);
        if (actorId != null) {
            audit().emit("zone.created", AuditOutcome.COMMITTED, actorId, null, zone, positions);
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
        Map<String, Object> snapshot = actorId == null ? Map.of() : banishedSnapshot(removed);

        File zoneFile = new File(zonesFolder, name + ".yml");
        if (zoneFile.exists() && !zoneFile.delete()) {
            if (actorId != null) {
                emitPersistFailed("zone.deleted", AuditPrivacy.STAFF_RESTRICTED, actorId, null, removed, snapshot);
            }
            return false;
        }

        zones.remove(name);
        if (actorId != null) {
            audit().emit("zone.deleted", AuditOutcome.COMMITTED, actorId, null, removed, snapshot);
        }
        return true;
    }

    /** Selected corner positions: {@code world}/{@code x}/{@code y}/{@code z} for pos1, {@code pos2_*} for pos2. */
    private static Map<String, Object> selectedPositions(Location point1, Location point2) {
        Map<String, Object> positions = new LinkedHashMap<>();
        putPosition(positions, "", point1);
        putPosition(positions, "pos2_", point2);
        return positions;
    }

    private static void putPosition(Map<String, Object> target, String prefix, Location location) {
        if (location == null) return;
        if (location.getWorld() != null) {
            target.put(prefix + "world", location.getWorld().getName());
        }
        target.put(prefix + "x", location.getX());
        target.put(prefix + "y", location.getY());
        target.put(prefix + "z", location.getZ());
    }

    /** Captures the ban list of a zone about to be removed, cut at whole-UUID boundaries. */
    private Map<String, Object> banishedSnapshot(Zone zone) {
        List<String> banished = zone.getBanishedPlayers().stream().map(UUID::toString).sorted().toList();
        StringBuilder joined = new StringBuilder();
        int included = 0;
        for (String id : banished) {
            int needed = joined.isEmpty() ? id.length() : id.length() + 1;
            if (joined.length() + needed > MAX_BANISHED_SNAPSHOT_CHARS) break;
            if (!joined.isEmpty()) joined.append(',');
            joined.append(id);
            included++;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("banished_count", banished.size());
        snapshot.put("banished_players", joined.toString());
        snapshot.put("banished_players_truncated", included < banished.size());
        return snapshot;
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
        return updateZone(zone, actorId, operation, AuditPrivacy.STAFF_RESTRICTED, metadata);
    }

    /** Persists and registers a zone edit, then emits {@code operation} with the given privacy class. */
    public boolean updateZone(Zone zone, UUID actorId, String operation, AuditPrivacy privacy,
                              Map<String, ?> metadata) {
        boolean persisted = saveZone(zone, null, null, null);
        if (!persisted) {
            if (actorId != null && operation != null) {
                emitPersistFailed(operation, privacy, actorId, null, zone, metadata);
            }
            return false;
        }

        zones.put(zone.getName(), zone);
        if (actorId != null && operation != null) {
            audit().emit(operation, AuditOutcome.COMMITTED, AuditRisk.NORMAL, privacy,
                    actorId, null, zone, null, metadata);
        }
        return true;
    }

    public void banishPlayer(Zone zone, UUID playerId) {
        banishPlayer(zone, playerId, null);
    }

    public BanishResult banishPlayer(Zone zone, UUID playerId, UUID actorId) {
        if (zone.isBanished(playerId)) {
            if (actorId != null) {
                audit().emit("zone.banished", AuditOutcome.DENIED, AuditRisk.NORMAL,
                        AuditPrivacy.STAFF_RESTRICTED, actorId, playerId, zone, "already_banished",
                        Map.of("reason", "already_banished"));
            }
            return BanishResult.ALREADY_BANISHED;
        }
        zone.banishPlayer(playerId);
        boolean persisted = saveZone(zone, null, null, null);
        if (!persisted) {
            zone.unbanishPlayer(playerId);
            if (actorId != null) {
                emitPersistFailed("zone.banished", AuditPrivacy.STAFF_RESTRICTED, actorId, playerId, zone, Map.of());
            }
            return BanishResult.PERSIST_FAILED;
        }
        if (actorId != null) {
            audit().emit("zone.banished", AuditOutcome.COMMITTED, actorId, playerId, zone, Map.of());
        }
        return BanishResult.BANISHED;
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
            if (actorId != null) {
                emitPersistFailed("zone.unbanished", AuditPrivacy.STAFF_RESTRICTED, actorId, playerId, zone, Map.of());
            }
            return false;
        }
        if (actorId != null) {
            audit().emit("zone.unbanished", AuditOutcome.COMMITTED, actorId, playerId, zone, Map.of());
        }
        return true;
    }

    /** Emits {@code operation} as {@code FAILED} after the YAML write or delete it depended on failed. */
    private void emitPersistFailed(String operation, AuditPrivacy privacy, UUID actorId, UUID targetId,
                                   Zone zone, Map<String, ?> metadata) {
        Map<String, Object> failure = new LinkedHashMap<>();
        if (metadata != null) {
            failure.putAll(metadata);
        }
        failure.put("reason", PERSIST_FAILED_REASON);
        audit().emit(operation, AuditOutcome.FAILED, AuditRisk.NORMAL, privacy,
                actorId, targetId, zone, PERSIST_FAILED_REASON, failure);
    }

    private ZoneAuditEmitter audit() {
        return plugin.getAuditEmitter();
    }

    public Set<UUID> getBanishedPlayers(Zone zone) {
        return zone.getBanishedPlayers();
    }
}
