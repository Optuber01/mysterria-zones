package net.mysterria.zones.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.zones.MysterriaZones;
import net.mysterria.zones.model.Zone;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "zone")
@Permission("myzones.zone")
public class ZoneConfigCommands {

    private final MysterriaZones plugin;

    public ZoneConfigCommands(MysterriaZones plugin) {
        this.plugin = plugin;
    }

    @Execute(name = "setenter")
    public void setEnter(@Context Player player, @Arg String zoneName, @Join String message) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        String previous = zone.getEnterMessage();
        Map<String, Object> metadata = auditMetadata("enter_message", previous, message);
        zone.setEnterMessage(message);
        if (plugin.getZoneManager().saveZone(zone, player.getUniqueId(), "zone.config.updated",
                metadata)) {
            player.sendMessage(Component.text("Enter message updated for zone '" + zoneName + "'!", NamedTextColor.GREEN));
        } else {
            zone.setEnterMessage(previous);
            player.sendMessage(Component.text("Could not persist enter message for zone '" + zoneName + "'.", NamedTextColor.RED));
        }
    }

    @Execute(name = "setexit")
    public void setExit(@Context Player player, @Arg String zoneName, @Join String message) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        String previous = zone.getExitMessage();
        Map<String, Object> metadata = auditMetadata("exit_message", previous, message);
        zone.setExitMessage(message);
        if (plugin.getZoneManager().saveZone(zone, player.getUniqueId(), "zone.config.updated",
                metadata)) {
            player.sendMessage(Component.text("Exit message updated for zone '" + zoneName + "'!", NamedTextColor.GREEN));
        } else {
            zone.setExitMessage(previous);
            player.sendMessage(Component.text("Could not persist exit message for zone '" + zoneName + "'.", NamedTextColor.RED));
        }
    }

    @Execute(name = "setdisplay")
    public void setDisplay(@Context Player player, @Arg String zoneName, @Join String displayName) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        String previous = zone.getDisplayName();
        Map<String, Object> metadata = auditMetadata("display_name", previous, displayName);
        zone.setDisplayName(displayName);
        if (plugin.getZoneManager().updateZone(zone, player.getUniqueId(), "zone.config.updated",
                metadata)) {
            player.sendMessage(Component.text("Display name updated to '" + displayName + "' for zone '" + zoneName + "'!", NamedTextColor.GREEN));
        } else {
            zone.setDisplayName(previous);
            player.sendMessage(Component.text("Could not persist display name for zone '" + zoneName + "'.", NamedTextColor.RED));
        }
    }

    @Execute(name = "toggle")
    public void toggle(@Context Player player, @Arg String zoneName) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        boolean previous = zone.isProtection();
        Map<String, Object> metadata = auditMetadata("protection", previous, !previous);
        zone.setProtection(!previous);
        if (plugin.getZoneManager().updateZone(zone, player.getUniqueId(), "zone.config.updated",
                metadata)) {
            String status = zone.isProtection() ? "enabled" : "disabled";
            player.sendMessage(Component.text("Protection " + status + " for zone '" + zoneName + "'!", NamedTextColor.GREEN));
        } else {
            zone.setProtection(previous);
            player.sendMessage(Component.text("Could not persist protection for zone '" + zoneName + "'.", NamedTextColor.RED));
        }
    }

    @Execute(name = "priority")
    public void priority(@Context Player player, @Arg String zoneName, @Arg int priority) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        int previous = zone.getPriority();
        Map<String, Object> metadata = auditMetadata("priority", previous, priority);
        zone.setPriority(priority);
        if (plugin.getZoneManager().updateZone(zone, player.getUniqueId(), "zone.config.updated",
                metadata)) {
            player.sendMessage(Component.text("Priority set to " + priority + " for zone '" + zoneName + "'!", NamedTextColor.GREEN));
        } else {
            zone.setPriority(previous);
            player.sendMessage(Component.text("Could not persist priority for zone '" + zoneName + "'.", NamedTextColor.RED));
        }
    }

    private Map<String, Object> auditMetadata(String field, Object previous, Object value) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("field", field);
        if (previous != null) metadata.put("previous", previous);
        if (value != null) metadata.put("value", value);
        return metadata;
    }
}
