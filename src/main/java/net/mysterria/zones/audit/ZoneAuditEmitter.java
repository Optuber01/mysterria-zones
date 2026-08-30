package net.mysterria.zones.audit;

import dev.ua.ikeepcalm.coi.api.audit.AuditEmission;
import dev.ua.ikeepcalm.coi.api.audit.AuditOutcome;
import dev.ua.ikeepcalm.coi.api.audit.AuditPrivacy;
import dev.ua.ikeepcalm.coi.api.audit.AuditRisk;
import dev.ua.ikeepcalm.coi.api.audit.MysterriaAudit;
import net.mysterria.zones.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Best-effort bridge to the optional shared Mysterria audit ledger. */
public final class ZoneAuditEmitter {
    private static final int MAX_TEXT = 256;
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final JavaPlugin plugin;
    private final AtomicLong lastWarningNanos = new AtomicLong();

    public ZoneAuditEmitter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Emits a staff-restricted zone event. The call only resolves the optional
     * service and delegates to its non-blocking implementation; failures never
     * affect zone persistence or command responses.
     */
    public void emit(String operation, AuditOutcome outcome, UUID actorId, UUID targetId,
                     Zone zone, Map<String, ?> metadata) {
        if (operation == null || operation.isBlank() || zone == null || actorId == null) {
            return;
        }

        try {
            RegisteredServiceProvider<MysterriaAudit> registration =
                    Bukkit.getServicesManager().getRegistration(MysterriaAudit.class);
            MysterriaAudit audit = registration == null ? null : registration.getProvider();
            if (audit == null) {
                return;
            }

            Map<String, Object> bounded = boundedMetadata(zone, metadata);
            audit.emit(new AuditEmission(
                    "mysterria-zones." + operation,
                    outcome,
                    AuditRisk.NORMAL,
                    AuditPrivacy.STAFF_RESTRICTED,
                    UUID.randomUUID(),
                    zone.getName(),
                    actorId,
                    null,
                    targetId,
                    null,
                    bounded));
            lastWarningNanos.set(0L);
        } catch (RuntimeException | LinkageError failure) {
            // The audit provider is optional and must never gate gameplay or persistence.
            Level level = shouldWarn() ? Level.WARNING : Level.FINE;
            plugin.getLogger().log(level, "Mysterria audit emission was unavailable", failure);
        }
    }

    private boolean shouldWarn() {
        long now = System.nanoTime();
        long previous = lastWarningNanos.get();
        if (previous != 0L && now - previous < WARNING_INTERVAL_NANOS) {
            return false;
        }
        return lastWarningNanos.compareAndSet(previous, now);
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
                    String boundedKey = bounded(key);
                    result.putIfAbsent(boundedKey, boundedValue(value));
                }
            });
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private Object boundedValue(Object value) {
        if (value instanceof String text) {
            return bounded(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return bounded(String.valueOf(value));
    }

    private String bounded(String value) {
        if (value == null) return "";
        if (value.codePointCount(0, value.length()) <= MAX_TEXT) return value;
        return value.substring(0, value.offsetByCodePoints(0, MAX_TEXT));
    }
}
