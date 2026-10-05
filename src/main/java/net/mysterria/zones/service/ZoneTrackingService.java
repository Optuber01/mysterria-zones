package net.mysterria.zones.service;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import dev.ua.ikeepcalm.mysterria.audit.client.api.AuditOutcome;
import net.mysterria.zones.MysterriaZones;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ZoneTrackingService {
    // One ejection row per player and zone in this window; the tracking task runs every second.
    private static final long EJECT_AUDIT_WINDOW_MILLIS = 60_000L;

    private final MysterriaZones plugin;
    private final Map<UUID, Zone> playerCurrentZones;
    private final Map<UUID, Location> lastSafeLocations;
    private final Map<UUID, EjectAudit> lastEjectAudits = new HashMap<>();
    private BukkitRunnable trackingTask;

    public ZoneTrackingService(MysterriaZones plugin) {
        this.plugin = plugin;
        this.playerCurrentZones = new HashMap<>();
        this.lastSafeLocations = new HashMap<>();
    }

    public void startTracking() {
        if (trackingTask != null && !trackingTask.isCancelled()) {
            trackingTask.cancel();
        }

        trackingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkPlayerZoneChange(player);
                }
            }
        };

        trackingTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopTracking() {
        if (trackingTask != null && !trackingTask.isCancelled()) {
            trackingTask.cancel();
        }
        playerCurrentZones.clear();
        lastSafeLocations.clear();
        lastEjectAudits.clear();
    }

    private void checkPlayerZoneChange(Player player) {
        Zone currentZone = plugin.getZoneManager().getHighestPriorityZone(player.getLocation());
        Zone previousZone = playerCurrentZones.get(player.getUniqueId());

        // Check if player is trying to enter a zone they're banished from
        if (currentZone != null && currentZone.isBanished(player.getUniqueId())) {
            handleBanishAttempt(player, currentZone, "tracking");
            return;
        }

        // Track safe location when player is outside any banished zones
        if (shouldTrackAsSafeLocation(player)) {
            lastSafeLocations.put(player.getUniqueId(), player.getLocation().clone());
        }

        if (currentZone != previousZone) {
            if (previousZone != null) {
                onZoneExit(player, previousZone);
            }

            if (currentZone != null) {
                onZoneEnter(player, currentZone);
                playerCurrentZones.put(player.getUniqueId(), currentZone);
            } else {
                playerCurrentZones.remove(player.getUniqueId());
            }
        }
    }

    private void onZoneEnter(Player player, Zone zone) {
        Component displayName = zone.getDisplayNameComponent();

        // Show title with only display name, no subtitle
        player.showTitle(Title.title(
                displayName,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(1000), Duration.ofSeconds(3), Duration.ofMillis(1000))
        ));

        // Play dramatic entrance sound
        player.playSound(Sound.sound(
                org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT,
                Sound.Source.MASTER,
                1.0f,
                1.0f
        ));
    }

    private void onZoneExit(Player player, Zone zone) {
        Component displayName = zone.getDisplayNameComponent();

        // Show title with only display name, no subtitle
        player.showTitle(Title.title(
                displayName,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
        ));

        // Play dramatic exit sound
        player.playSound(Sound.sound(
                org.bukkit.Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                Sound.Source.MASTER,
                0.8f,
                1.0f
        ));
    }

    public void removePlayer(Player player) {
        playerCurrentZones.remove(player.getUniqueId());
        lastSafeLocations.remove(player.getUniqueId());
        lastEjectAudits.remove(player.getUniqueId());
    }

    public Zone getCurrentZone(Player player) {
        return playerCurrentZones.get(player.getUniqueId());
    }

    public Location getLastSafeLocation(UUID playerId) {
        return lastSafeLocations.get(playerId);
    }

    private boolean shouldTrackAsSafeLocation(Player player) {
        Location loc = player.getLocation();
        for (Zone zone : plugin.getZoneManager().getAllZones()) {
            if (zone.isBanished(player.getUniqueId()) && zone.contains(loc)) {
                return false;
            }
        }
        return true;
    }

    private void handleBanishAttempt(Player player, Zone zone, String trigger) {
        Location safeLocation = lastSafeLocations.get(player.getUniqueId());
        Location destination = safeLocation != null ? safeLocation : player.getWorld().getSpawnLocation();
        Location from = claimEjectionAudit(player, zone, trigger) ? player.getLocation() : null;

        player.teleport(destination);
        if (from != null) {
            auditEjection(player, zone, trigger, from, destination);
        }

        Component warning = Component.text("You are banished from ", NamedTextColor.RED)
                .append(zone.getDisplayNameComponent())
                .append(Component.text("!", NamedTextColor.RED));
        player.sendMessage(warning);

        player.playSound(Sound.sound(
                org.bukkit.Sound.ENTITY_VILLAGER_NO,
                Sound.Source.MASTER,
                1.0f,
                0.7f
        ));
    }

    public void ejectBanishedPlayer(Player player, Zone zone) {
        if (!zone.contains(player.getLocation())) {
            return;
        }

        handleBanishAttempt(player, zone, "banish_command");
    }

    private boolean claimEjectionAudit(Player player, Zone zone, String trigger) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        EjectAudit previous = lastEjectAudits.get(playerId);
        if ("tracking".equals(trigger) && previous != null && previous.zoneName().equals(zone.getName())
                && now - previous.millis() < EJECT_AUDIT_WINDOW_MILLIS) {
            return false;
        }
        lastEjectAudits.put(playerId, new EjectAudit(zone.getName(), now));
        return true;
    }

    // The emit call is thread-safe and queues off-thread.
    private void auditEjection(Player player, Zone zone, String trigger, Location from, Location destination) {
        UUID playerId = player.getUniqueId();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("trigger", trigger);
        if (from.getWorld() != null) {
            metadata.put("world", from.getWorld().getName());
        }
        metadata.put("x", from.getX());
        metadata.put("y", from.getY());
        metadata.put("z", from.getZ());
        if (destination.getWorld() != null) {
            metadata.put("to_world", destination.getWorld().getName());
        }
        metadata.put("to_x", destination.getX());
        metadata.put("to_y", destination.getY());
        metadata.put("to_z", destination.getZ());
        plugin.getAuditEmitter().emit("zone.banish_ejected", AuditOutcome.OBSERVED, playerId, playerId,
                zone, metadata);
    }

    private record EjectAudit(String zoneName, long millis) {
    }
}