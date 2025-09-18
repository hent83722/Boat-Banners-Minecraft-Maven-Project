package com.example.bannerboat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BannerBoatPlugin extends JavaPlugin implements Listener {

    private double backOffset;
    private double yOffset;

    private final Map<UUID, BannerData> bannerMap = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadOffsets();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("BannerBoat enabled with offsets: backOffset=" + backOffset + ", yOffset=" + yOffset);
    }

    @Override
    public void onDisable() {
        for (BannerData data : bannerMap.values()) {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.remove();
            }
        }
        bannerMap.clear();
    }

    private void loadOffsets() {
        FileConfiguration config = getConfig();
        backOffset = config.getDouble("offsets.back", 0.45);
        yOffset = config.getDouble("offsets.y", -1.0);
    }

    @EventHandler
    public void onPlayerInteractBoat(PlayerInteractEntityEvent event) {
        // Only allow interaction if the clicked entity is a Boat
        if (!(event.getRightClicked() instanceof Boat boat)) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        UUID boatId = boat.getUniqueId();

        // Only allow placing banners on boats
        if (handItem == null || !handItem.getType().toString().endsWith("_BANNER")) return;

        // Prevent attaching multiple banners
        if (bannerMap.containsKey(boatId)) return;

        event.setCancelled(true);

        Location spawnLoc = boat.getLocation().add(0, 1, 0);
        ArmorStand stand = boat.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSmall(false); // full-size banner
            as.setMarker(false);
            as.getEquipment().setHelmet(new ItemStack(handItem.getType()));
            as.setHeadPose(new EulerAngle(0, 0, 0));
        });

        // Remove one banner from player's hand
        handItem.setAmount(handItem.getAmount() - 1);

        BannerData data = new BannerData(stand, new ItemStack(handItem.getType()));
        bannerMap.put(boatId, data);

        // Keep ArmorStand attached to boat
        new BukkitRunnable() {
            @Override
            public void run() {
                if (boat.isDead() || stand.isDead() || !boat.isValid() || !stand.isValid()) {
                    stand.remove();
                    cancel();
                    return;
                }

                Location boatLoc = boat.getLocation();
                Vector dir = boatLoc.getDirection().normalize();

                double x = boatLoc.getX() - dir.getX() * backOffset;
                double y = boatLoc.getY() + yOffset;
                double z = boatLoc.getZ() - dir.getZ() * backOffset;

                Location newLoc = new Location(boat.getWorld(), x, y, z, boatLoc.getYaw(), 0);
                stand.teleport(newLoc);
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @EventHandler
    public void onBoatBreak(VehicleDestroyEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;

        UUID boatId = boat.getUniqueId();
        if (!bannerMap.containsKey(boatId)) return;

        BannerData data = bannerMap.remove(boatId);
        if (data != null && data.stand != null && data.stand.isValid()) {
            data.stand.remove();
            boat.getWorld().dropItemNaturally(boat.getLocation(), data.bannerItem);
        }
    }

    private static class BannerData {
        final ArmorStand stand;
        final ItemStack bannerItem;

        BannerData(ArmorStand stand, ItemStack bannerItem) {
            this.stand = stand;
            this.bannerItem = bannerItem;
        }
    }
}
