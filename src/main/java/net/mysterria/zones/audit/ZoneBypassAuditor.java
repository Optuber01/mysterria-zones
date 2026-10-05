package net.mysterria.zones.audit;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.zones.model.Zone;
import net.mysterria.zones.service.ZoneTrackingService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// Main-thread only: lastEmitted is not synchronized.
public final class ZoneBypassAuditor {
    public static final String BYPASS_PERMISSION = "myzones.bypass";
    public static final long WINDOW_MILLIS = 5L * 60L * 1_000L;
    static final int MAX_ENTRIES = 1024;

    private final ZoneAuditEmitter emitter;
    private final ZoneTrackingService tracking;
    // Insertion-ordered by last emission (re-put after remove), so the eldest entry is the oldest emission.
    private final Map<Key, Long> lastEmitted = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public ZoneBypassAuditor(ZoneAuditEmitter emitter, ZoneTrackingService tracking) {
        this.emitter = emitter;
        this.tracking = tracking;
    }

    public boolean bypasses(Player player, String action, Block block) {
        return bypasses(player, action, block::getLocation);
    }

    public boolean bypasses(Player player, String action) {
        return bypasses(player, action, player::getLocation);
    }

    // Checked before the listener's zone lookup, as before auditing; the row uses the zone the tracking task already holds.
    private boolean bypasses(Player player, String action, Supplier<Location> location) {
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }
        Zone zone = tracking.getCurrentZone(player);
        if (zone != null && zone.isProtection()) {
            recordBypass(player, zone, action, location);
        }
        return true;
    }

    private void recordBypass(Player player, Zone zone, String action, Supplier<Location> location) {
        long now = System.currentTimeMillis();
        Key key = new Key(player.getUniqueId(), zone.getName());
        Long previous = lastEmitted.get(key);
        if (previous != null && now - previous < WINDOW_MILLIS) {
            return;
        }
        lastEmitted.remove(key);
        lastEmitted.put(key, now);

        emitter.emit("zone.bypass_used", AuditOutcome.OBSERVED, AuditRisk.HIGH,
                AuditPrivacy.STAFF_RESTRICTED, player.getUniqueId(), null, zone, null,
                metadata(action, location.get()));
    }

    private static Map<String, Object> metadata(String action, Location location) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("action", action);
        if (location.getWorld() != null) {
            metadata.put("world", location.getWorld().getName());
        }
        metadata.put("x", location.getX());
        metadata.put("y", location.getY());
        metadata.put("z", location.getZ());
        return metadata;
    }

    private record Key(UUID playerId, String zoneName) {
    }
}
