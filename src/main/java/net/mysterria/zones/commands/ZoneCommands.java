package net.mysterria.zones.commands;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mysterria.zones.MysterriaZones;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Command(name = "zone")
@Permission("myzones.zone")
public class ZoneCommands {

    private final MysterriaZones plugin;

    public ZoneCommands(MysterriaZones plugin) {
        this.plugin = plugin;
    }

    @Execute(name = "pos1")
    public void pos1(@Context Player player) {
        plugin.setPoint1(player.getLocation());
        player.sendMessage(Component.text("First position set to: " + formatLocation(player.getLocation()), NamedTextColor.GREEN));
    }

    @Execute(name = "pos2")
    public void pos2(@Context Player player) {
        plugin.setPoint2(player.getLocation());
        player.sendMessage(Component.text("Second position set to: " + formatLocation(player.getLocation()), NamedTextColor.GREEN));
    }

    @Execute(name = "create")
    public void create(@Context Player player, @Arg String zoneName) {
        if (plugin.getZoneManager().hasZone(zoneName)) {
            player.sendMessage(Component.text("Zone with name '" + zoneName + "' already exists!", NamedTextColor.RED));
            return;
        }

        Location pos1 = plugin.getPoint1();
        Location pos2 = plugin.getPoint2();

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Component.text("Please set both positions first using /zone pos1 and /zone pos2", NamedTextColor.RED));
            return;
        }

        if (plugin.getZoneManager().createZone(zoneName, pos1, pos2, player.getUniqueId())) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' created successfully!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Zone '" + zoneName + "' could not be persisted.", NamedTextColor.RED));
        }
    }

    @Execute(name = "delete")
    public void delete(@Context Player player, @Arg String zoneName) {
        if (plugin.getZoneManager().deleteZone(zoneName, player.getUniqueId())) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' deleted successfully!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
        }
    }

    @Execute(name = "list")
    public void list(@Context Player player) {
        var zones = plugin.getZoneManager().getZoneNames();

        if (zones.isEmpty()) {
            player.sendMessage(Component.text("No zones found.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Protected Zones ===", NamedTextColor.AQUA));
        for (String zoneName : zones) {
            Zone zone = plugin.getZoneManager().getZone(zoneName);
            String status = zone.isProtection() ? "§a✓" : "§c✗";
            player.sendMessage(Component.text(status + " " + zoneName + " (Priority: " + zone.getPriority() + ")", NamedTextColor.WHITE));
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f) in %s", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }
}
