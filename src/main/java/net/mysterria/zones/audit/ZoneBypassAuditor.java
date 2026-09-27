package net.mysterria.zones.audit;

import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditPrivacy;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditRisk;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records {@code zone.bypass_used} when {@code myzones.bypass} skips a protection
 * check, at most once per player per zone per {@link #WINDOW_MILLIS}. Main-thread only.
 */
public final class ZoneBypassAuditor {
    public static final String BYPASS_PERMISSION = "myzones.bypass";
    public static final long WINDOW_MILLIS = 5L * 60L * 1_000L;
    private static final int PRUNE_THRESHOLD = 512;
    /** Hard cap on tracked player/zone pairs; the oldest emission is evicted first. */
    static final int MAX_ENTRIES = 1024;

    private final Supplier<ZoneAuditEmitter> emitter;
    /** Insertion-ordered by last emission, so the eldest entry is the oldest emission. */
    private final Map<Key, Long> lastEmitted = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public ZoneBypassAuditor(Supplier<ZoneAuditEmitter> emitter) {
        this.emitter = emitter;
    }

    /**
     * Returns true when {@code player} holds the bypass permission for a protected
     * check in {@code zone}, recording the use (rate-limited).
     */
    public boolean bypasses(Player player, Zone zone, String action, Location location) {
        if (!player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }
        recordBypass(player, zone, action, location);
        return true;
    }

    public void recordBypass(Player player, Zone zone, String action, Location location) {
        if (player == null || zone == null || action == null || location == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Key key = new Key(player.getUniqueId(), zone.getName());
        Long previous = lastEmitted.get(key);
        if (previous != null && now - previous < WINDOW_MILLIS) {
            return;
        }
        pruneIfLarge(now);
        lastEmitted.remove(key);
        lastEmitted.put(key, now);

        ZoneAuditEmitter audit = emitter.get();
        if (audit == null) {
            return;
        }
        audit.emit("zone.bypass_used", AuditOutcome.OBSERVED, AuditRisk.HIGH,
                AuditPrivacy.STAFF_RESTRICTED, player.getUniqueId(), null, zone, null,
                metadata(action, location));
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

    private void pruneIfLarge(long now) {
        if (lastEmitted.size() >= PRUNE_THRESHOLD) {
            lastEmitted.values().removeIf(at -> now - at >= WINDOW_MILLIS);
        }
    }

    private record Key(UUID playerId, String zoneName) {
    }
}
