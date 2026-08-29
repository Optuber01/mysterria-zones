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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

@Command(name = "zone")
@Permission("myzones.zone.admin")
public class ZoneBanishCommands {

    private final MysterriaZones plugin;

    public ZoneBanishCommands(MysterriaZones plugin) {
        this.plugin = plugin;
    }

    @Execute(name = "banish")
    public void banish(@Context Player player, @Arg String zoneName, @Arg Player target) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        if (zone.isBanished(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is already banished from zone '" + zoneName + "'!", NamedTextColor.YELLOW));
            return;
        }

        if (!plugin.getZoneManager().banishPlayer(zone, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(Component.text("Could not persist banishment for zone '" + zoneName + "'.", NamedTextColor.RED));
            return;
        }

        // Immediate ejection if player is in the zone
        if (zone.contains(target.getLocation())) {
            plugin.getZoneTrackingService().ejectBanishedPlayer(target, zone);
        }

        player.sendMessage(Component.text(target.getName() + " has been banished from zone '" + zoneName + "'!", NamedTextColor.GREEN));
        target.sendMessage(Component.text("You have been banished from ", NamedTextColor.RED)
                .append(zone.getDisplayNameComponent())
                .append(Component.text("!", NamedTextColor.RED)));
    }

    @Execute(name = "unbanish")
    public void unbanish(@Context Player player, @Arg String zoneName, @Arg OfflinePlayer target) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        if (!zone.isBanished(target.getUniqueId())) {
            player.sendMessage(Component.text(target.getName() + " is not banished from zone '" + zoneName + "'!", NamedTextColor.YELLOW));
            return;
        }

        if (!plugin.getZoneManager().unbanishPlayer(zone, target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(Component.text("Could not persist unbanishment for zone '" + zoneName + "'.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(target.getName() + " has been unbanished from zone '" + zoneName + "'!", NamedTextColor.GREEN));

        if (target.isOnline()) {
            ((Player) target).sendMessage(Component.text("You have been unbanished from ", NamedTextColor.GREEN)
                    .append(zone.getDisplayNameComponent())
                    .append(Component.text("!", NamedTextColor.GREEN)));
        }
    }

    @Execute(name = "banlist")
    public void banlist(@Context Player player, @Arg String zoneName) {
        Zone zone = plugin.getZoneManager().getZone(zoneName);

        if (zone == null) {
            player.sendMessage(Component.text("Zone '" + zoneName + "' not found!", NamedTextColor.RED));
            return;
        }

        Set<UUID> banished = plugin.getZoneManager().getBanishedPlayers(zone);

        if (banished.isEmpty()) {
            player.sendMessage(Component.text("No players are banished from zone '" + zoneName + "'.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Banished Players: " + zoneName + " ===", NamedTextColor.AQUA));
        for (UUID uuid : banished) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();
            player.sendMessage(Component.text("- " + playerName, NamedTextColor.WHITE));
        }
    }
}
