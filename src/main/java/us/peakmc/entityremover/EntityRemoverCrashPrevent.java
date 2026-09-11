package us.peakmc.entityremover;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class EntityRemoverCrashPrevent extends JavaPlugin {

    private static final long DEFAULT_INTERVAL_TICKS = 6000L;
    private static final Set<EntityType> DEFAULT_PROTECTED_TYPES = EnumSet.of(
            EntityType.PLAYER,
            EntityType.ARMOR_STAND,
            EntityType.ITEM_FRAME,
            EntityType.GLOW_ITEM_FRAME,
            EntityType.PAINTING,
            EntityType.MARKER,
            EntityType.INTERACTION,
            EntityType.TEXT_DISPLAY,
            EntityType.ITEM_DISPLAY,
            EntityType.BLOCK_DISPLAY
    );

    private BukkitTask cleanupTask;
    private int cleanupIntervalSeconds = 300;
    private long cleanupIntervalTicks = DEFAULT_INTERVAL_TICKS;
    private long nextCleanupTick;
    private boolean warningsEnabled = true;
    private boolean broadcastEnabled = true;
    private boolean namedEntitiesProtected = true;
    private boolean whitelistEnabled = false;
    private boolean blacklistEnabled = true;
    private final Set<String> worldBlacklist = new HashSet<>();
    private final Set<String> worldWhitelist = new HashSet<>();
    private final Set<EntityType> protectedEntityTypes = EnumSet.noneOf(EntityType.class);
    private final Set<EntityType> cleanupEntityTypes = EnumSet.noneOf(EntityType.class);
    private final Set<Integer> warningThresholds = new HashSet<>();
    private final Set<Integer> warnedSeconds = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        getCommand("entitycleanup").setExecutor(new EntityCleanupCommand(this));

        startAutomaticCleanupCycle();

        getLogger().info("EntityRemoverCrashPrevent enabled.");
        getLogger().info("Automatic cleanup interval: " + cleanupIntervalSeconds + " seconds");
        getLogger().info("Loaded worlds: " + Bukkit.getWorlds().size());
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void runManualCleanup(CommandSender sender) {
        if (Bukkit.isPrimaryThread()) {
            performCleanup(sender, false);
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> performCleanup(sender, false));
    }

    private void startAutomaticCleanupCycle() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        nextCleanupTick = Bukkit.getCurrentTick() + cleanupIntervalTicks;
        warnedSeconds.clear();

        cleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTick = Bukkit.getCurrentTick();
            long difference = nextCleanupTick - currentTick;
            if (difference <= 0) {
                performCleanup(null, true);
                nextCleanupTick = currentTick + cleanupIntervalTicks;
                warnedSeconds.clear();
                return;
            }

            if (!warningsEnabled || !broadcastEnabled) {
                return;
            }

            int remainingSeconds = (int) Math.ceil(difference / 20.0D);
            if (warningThresholds.contains(remainingSeconds) && warnedSeconds.add(remainingSeconds)) {
                String warning = buildWarningMessage(remainingSeconds);
                if (warning != null) {
                    broadcastMessage(Component.text(warning));
                }
            }
        }, 20L, 20L);
    }

    private String buildWarningMessage(int remainingSeconds) {
        if (remainingSeconds == 60) {
            return "§eEntity cleanup in §c60 seconds§e! Named entities and players are protected.";
        }
        if (remainingSeconds == 30) {
            return "§eEntity cleanup in §c30 seconds§e!";
        }
        if (remainingSeconds == 10) {
            return "§eEntity cleanup in §c10 seconds§e!";
        }
        if (remainingSeconds >= 1 && remainingSeconds <= 5) {
            return "§eEntity cleanup in §c" + remainingSeconds + " seconds§e!";
        }
        return null;
    }

    private void performCleanup(CommandSender sender, boolean automatic) {
        Map<String, Integer> perWorld = new LinkedHashMap<>();
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            if (!isWorldAllowed(world.getName())) {
                continue;
            }
            int removedInWorld = cleanupWorld(world);
            totalRemoved += removedInWorld;
            perWorld.put(world.getName(), removedInWorld);
            getLogger().info("World " + world.getName() + " removed " + removedInWorld + " entities.");
        }

        if (sender != null) {
            sender.sendMessage(Component.text("§aEntity cleanup complete! §f" + totalRemoved + " §aentities removed."));
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, Integer> entry : perWorld.entrySet()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(entry.getKey()).append(": ").append(entry.getValue());
            }
            sender.sendMessage(Component.text("§7Per world: " + (builder.length() == 0 ? "none" : builder)));
        }

        if (automatic) {
            getLogger().info("Entity cleanup complete. Total removed: " + totalRemoved);
            broadcastMessage(Component.text("§aEntity cleanup complete! §f" + totalRemoved + " §aentities removed."));
        }
    }

    private int cleanupWorld(World world) {
        if (world == null) {
            return 0;
        }

        int removed = 0;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity == null || entity.isDead() || !entity.isValid()) {
                continue;
            }
            if (shouldProtect(entity)) {
                continue;
            }
            if (!shouldRemove(entity)) {
                continue;
            }
            try {
                entity.remove();
                removed++;
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "Failed to remove entity " + entity.getType() + " in world " + world.getName(), ex);
            }
        }
        return removed;
    }

    private boolean shouldProtect(Entity entity) {
        if (entity == null) {
            return true;
        }
        if (entity instanceof Player) {
            return true;
        }
        if (namedEntitiesProtected && entity.getCustomName() != null) {
            return true;
        }
        if (protectedEntityTypes.contains(entity.getType())) {
            return true;
        }
        return false;
    }

    private boolean shouldRemove(Entity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return false;
        }
        if (entity instanceof Player) {
            return false;
        }
        if (namedEntitiesProtected && entity.getCustomName() != null) {
            return false;
        }
        if (protectedEntityTypes.contains(entity.getType())) {
            return false;
        }
        if (entity.getVehicle() != null || !entity.getPassengers().isEmpty()) {
            return false;
        }
        if (entity.getType() == EntityType.TNT) {
            return true;
        }
        if (cleanupEntityTypes.contains(entity.getType())) {
            return true;
        }
        if (isEssentialInternalEntity(entity.getType())) {
            return false;
        }
        return entity.getType() != EntityType.UNKNOWN;
    }

    private boolean isEssentialInternalEntity(EntityType type) {
        if (type == null) {
            return true;
        }
        return type == EntityType.MARKER
                || type == EntityType.INTERACTION
                || type == EntityType.GLOW_ITEM_FRAME
                || type == EntityType.ITEM_FRAME
                || type == EntityType.PAINTING
                || type == EntityType.ARMOR_STAND
                || type == EntityType.TEXT_DISPLAY
                || type == EntityType.ITEM_DISPLAY
                || type == EntityType.BLOCK_DISPLAY;
    }

    private void reloadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();

        cleanupIntervalSeconds = Math.max(1, config.getInt("cleanup.interval-seconds", 300));
        cleanupIntervalTicks = cleanupIntervalSeconds * 20L;
        warningsEnabled = config.getBoolean("cleanup.warnings.enabled", true);
        broadcastEnabled = config.getBoolean("cleanup.broadcast", true);
        namedEntitiesProtected = config.getBoolean("cleanup.named-entities-protected", true);
        whitelistEnabled = config.getBoolean("cleanup.worlds.whitelist-enabled", false);
        blacklistEnabled = config.getBoolean("cleanup.worlds.blacklist-enabled", true);

        worldBlacklist.clear();
        worldWhitelist.clear();
        worldBlacklist.addAll(config.getStringList("cleanup.worlds.blacklist"));
        worldWhitelist.addAll(config.getStringList("cleanup.worlds.whitelist"));

        protectedEntityTypes.clear();
        protectedEntityTypes.addAll(DEFAULT_PROTECTED_TYPES);
        for (String raw : config.getStringList("cleanup.protected-entity-types")) {
            try {
                protectedEntityTypes.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Ignoring invalid protected entity type: " + raw);
            }
        }

        cleanupEntityTypes.clear();
        for (String raw : config.getStringList("cleanup.cleanup-entity-types")) {
            try {
                cleanupEntityTypes.add(EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Ignoring invalid cleanup entity type: " + raw);
            }
        }

        warningThresholds.clear();
        List<?> warnings = config.getList("cleanup.warnings.times", Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));
        if (warnings == null) {
            warnings = Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1);
        }
        for (Object value : warnings) {
            if (value instanceof Number number) {
                warningThresholds.add(number.intValue());
            }
        }
        if (warningThresholds.isEmpty()) {
            warningThresholds.addAll(Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));
        }
    }

    private boolean isWorldAllowed(String worldName) {
        if (worldName == null) {
            return false;
        }
        if (whitelistEnabled && !worldWhitelist.isEmpty()) {
            return worldWhitelist.contains(worldName);
        }
        if (blacklistEnabled && worldBlacklist.contains(worldName)) {
            return false;
        }
        return true;
    }

    private void broadcastMessage(Component message) {
        if (!broadcastEnabled || message == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }
}
