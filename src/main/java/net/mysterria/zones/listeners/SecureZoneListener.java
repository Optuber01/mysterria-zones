package net.mysterria.zones.listeners;

import lombok.Getter;
import net.mysterria.zones.MysterriaZones;
import net.mysterria.zones.audit.ZoneBypassAuditor;
import net.mysterria.zones.model.Zone;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SecureZoneListener implements Listener {

    @Getter
    private final Map<Location, BlockState> placedBlockStates = new HashMap<>();

    @Getter
    private final Map<Location, BlockState> brokenBlockStates = new HashMap<>();

    private final Random random = new Random();

    private final int minRestoreDelayTicks = 2 * 20;
    private final int maxRestoreDelayTicks = 10 * 20;
    private final ZoneBypassAuditor bypassAuditor;

    public SecureZoneListener(ZoneBypassAuditor bypassAuditor) {
        this.bypassAuditor = bypassAuditor;
    }

    @EventHandler
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getLocation());
            if (zone != null && zone.isProtection()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getRespawnLocation());
        if (zone != null && zone.isProtection()) {
            event.setRespawnLocation(new Location(player.getWorld(), 48, 79, -52));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MysterriaZones.getInstance().getZoneTrackingService().removePlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getEntity().getLocation());
            if (zone != null && zone.isProtection()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() != Material.LECTERN && block.getType() != Material.CRAFTING_TABLE) {
                if (bypassAuditor.bypasses(event.getPlayer(), "block_interact", block)) {
                    return;
                }
                Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getClickedBlock().getLocation());
                if (zone != null && zone.isProtection()) {
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (bypassAuditor.bypasses(event.getPlayer(), "block_break", block)) {
            return;
        }
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(block.getLocation());
        if (zone != null && zone.isProtection()) {
            if (!event.getPlayer().isOp()) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                recordBrokenBlock(block);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDestroy(EntityDeathEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getEntity().getLocation());
            if (zone != null && zone.isProtection()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (bypassAuditor.bypasses(event.getPlayer(), "block_place", block)) {
            return;
        }
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(block.getLocation());
        if (zone != null && zone.isProtection()) {
            if (Tag.SHULKER_BOXES.isTagged(event.getBlockPlaced().getType())) {
                event.setCancelled(true);
            }
            if (!event.getPlayer().isOp()) {
                recordPlacedBlock(block);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getBlock().getLocation());
        if (zone != null && zone.isProtection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getLocation());
        if (zone != null && zone.isProtection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getBlock().getLocation());
        if (zone != null && zone.isProtection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(event.getBlock().getLocation());
        if (zone != null && zone.isProtection()) {
            event.setCancelled(true);
        }
    }

    private void handleExplosion(List<Block> blockList) {
        blockList.removeIf(block -> {
            Zone zone = MysterriaZones.getInstance().getZoneManager().getHighestPriorityZone(block.getLocation());
            if (zone != null && zone.isProtection()) {
                recordBrokenBlock(block);
                return true;
            }
            return false;
        });
    }

    private void recordBrokenBlock(Block block) {
        if (!placedBlockStates.containsKey(block.getLocation())) {
            if (!brokenBlockStates.containsKey(block.getLocation())) {
                brokenBlockStates.put(block.getLocation(), block.getState());
                scheduleBlockRestoration(block.getLocation());
            }
        }
    }

    private void recordPlacedBlock(Block block) {
        placedBlockStates.put(block.getLocation(), block.getState());
        scheduleBlockRemoval(block.getLocation());
    }

    private void scheduleBlockRestoration(Location location) {
        new BukkitRunnable() {
            @Override
            public void run() {
                restoreBlock(location, brokenBlockStates);
            }
        }.runTaskLater(MysterriaZones.getInstance(), getRandomDelay());
    }

    private void scheduleBlockRemoval(Location location) {
        new BukkitRunnable() {
            @Override
            public void run() {
                removePlacedBlock(location, placedBlockStates);
            }
        }.runTaskLater(MysterriaZones.getInstance(), getRandomDelay());
    }

    private long getRandomDelay() {
        return random.nextInt(maxRestoreDelayTicks - minRestoreDelayTicks) + minRestoreDelayTicks;
    }

    public void restoreBlock(Location location, Map<Location, BlockState> blockStateMap) {
        if (!blockStateMap.containsKey(location)) {
            return;
        }

        BlockState originalState = blockStateMap.get(location);
        originalState.update(true, false);
        blockStateMap.remove(location);
    }

    public void removePlacedBlock(Location location, Map<Location, BlockState> blockStateMap) {
        if (!blockStateMap.containsKey(location)) {
            return;
        }

        BlockState originalState = blockStateMap.get(location);
        if (location.getBlock().getState().getType() == originalState.getType()) {
            location.getBlock().setType(Material.AIR);
        }
        blockStateMap.remove(location);
    }
}