package net.mysterria.zones.audit;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditProducer;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.zones.model.Zone;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZoneAuditEmitter implements AutoCloseable {
    private static final int MAX_TEXT = 256;
    // Snapshot keys that may use the client's full per-value budget.
    private static final int MAX_LONG_TEXT = 1_024;
    private static final Set<String> LONG_TEXT_KEYS = Set.of("banished_players");
    // Event-specific position keys override the zone-context world when supplied.
    private static final Set<String> LOCATION_KEYS = Set.of("world", "x", "y", "z");

    // Null when the audit client failed to initialise; every call is then a no-op.
    private final AuditProducer producer;

    public ZoneAuditEmitter(JavaPlugin plugin) {
        this.producer = createProducer(plugin);
    }

    private static AuditProducer createProducer(JavaPlugin plugin) {
        try {
            return AuditProducer.create(plugin.getDataFolder().toPath().toAbsolutePath().getParent()
                            .resolve("mysterria-audit-spool"),
                    "mysterria-zones", plugin.getPluginMeta().getVersion());
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().warning("Audit client unavailable; zone audit events are disabled: " + failure);
            return null;
        }
    }

    public void emit(String operation, AuditOutcome outcome, UUID actorId, UUID targetId,
                     Zone zone, Map<String, ?> metadata) {
        emit(operation, outcome, AuditRisk.NORMAL, AuditPrivacy.STAFF_RESTRICTED,
                actorId, targetId, zone, null, metadata);
    }

    public void emit(String operation, AuditOutcome outcome, AuditRisk risk, AuditPrivacy privacy,
                     UUID actorId, UUID targetId, Zone zone, String reason, Map<String, ?> metadata) {
        if (producer == null || operation == null || operation.isBlank() || zone == null || actorId == null) {
            return;
        }

        try {
            Map<String, Object> bounded = boundedMetadata(zone, metadata);
            producer.emit("mysterria-zones." + operation, outcome, risk, privacy,
                    UUID.randomUUID(), zone.getName(), actorId, null, targetId,
                    reason == null ? null : bounded(reason, MAX_TEXT), bounded);
        } catch (RuntimeException | LinkageError failure) {
            // Audit delivery is best effort and must never gate gameplay or persistence.
            recordFailure();
        }
    }

    private void recordFailure() {
        try {
            producer.recordFailure();
        } catch (RuntimeException | LinkageError ignored) {
            // Failure accounting is itself best effort.
        }
    }

    private Map<String, Object> boundedMetadata(Zone zone, Map<String, ?> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zone", bounded(zone.getName()));
        result.put("world", bounded(zone.getWorldName()));
        result.put("min_x", zone.getMinX());
        result.put("min_y", zone.getMinY());
        result.put("min_z", zone.getMinZ());
        result.put("max_x", zone.getMaxX());
        result.put("max_y", zone.getMaxY());
        result.put("max_z", zone.getMaxZ());
        result.put("protection", zone.isProtection());
        result.put("priority", zone.getPriority());
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank() && result.size() < 32 && value != null) {
                    String boundedKey = bounded(key, MAX_TEXT);
                    Object boundedValue = boundedValue(boundedKey, value);
                    if (LOCATION_KEYS.contains(boundedKey)) {
                        result.put(boundedKey, boundedValue);
                    } else {
                        result.putIfAbsent(boundedKey, boundedValue);
                    }
                }
            });
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private Object boundedValue(String key, Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        int limit = LONG_TEXT_KEYS.contains(key) ? MAX_LONG_TEXT : MAX_TEXT;
        return bounded(value instanceof String text ? text : String.valueOf(value), limit);
    }

    private String bounded(String value) {
        return bounded(value, MAX_TEXT);
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        if (value.codePointCount(0, value.length()) <= limit) return value;
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }

    @Override
    public void close() {
        if (producer == null) {
            return;
        }
        try {
            producer.close();
        } catch (RuntimeException | LinkageError ignored) {
            // Shutdown must continue even if the audit client cannot flush.
        }
    }
}
