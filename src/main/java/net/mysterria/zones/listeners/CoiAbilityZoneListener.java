package net.mysterria.zones.listeners;

import dev.ua.ikeepcalm.coi.api.event.AbilityUsageEvent;
import net.mysterria.zones.MysterriaZones;
import net.mysterria.zones.audit.ZoneBypassAuditor;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Blocks CircleOfImagination abilities in protected zones. Kept apart from
 * {@link SecureZoneListener} and registered only when CircleOfImagination is
 * loaded, because CircleOfImagination is a soft dependency.
 */
public class CoiAbilityZoneListener implements Listener {

    private final ZoneBypassAuditor bypassAuditor;

    public CoiAbilityZoneListener(ZoneBypassAuditor bypassAuditor) {
        this.bypassAuditor = bypassAuditor;
    }

    @EventHandler
    public void onAbilityUsage(AbilityUsageEvent event) {
        if (bypassAuditor.bypasses(event.getPlayer(), "ability_use")) {
            return;
        }

        Location location = event.getPlayer().getLocation();
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(location);
        if (zone != null && zone.isProtection()) {
            event.setCancelled(true);
        }
    }
}
