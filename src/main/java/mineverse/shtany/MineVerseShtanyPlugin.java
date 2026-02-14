package mineverse.shtany;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

public class MineVerseShtanyPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<Player, Long> nextEventTime = new HashMap<>();
    private final Map<Player, Long> observeStartTime = new HashMap<>();
    private final Map<UUID, Integer> storyProgress = new HashMap<>();
    private final Map<UUID, Integer> fearLevel = new HashMap<>();

    private Player targetPlayer = null;

    private File storyFile;
    private FileConfiguration storyConfig;
    private List<String> storyLines = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadStoryConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        startMainLoop();
        getLogger().info("MineVerseShtany активирован - невидимая аномалия начинает охоту...");
    }

    @Override
    public void onDisable() {
        getLogger().info("MineVerseShtany деактивирован.");
    }

    private void loadStoryConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        storyFile = new File(getDataFolder(), "story.yml");
        if (!storyFile.exists()) {
            saveResource("story.yml", false);
        }
        storyConfig = YamlConfiguration.loadConfiguration(storyFile);
        storyLines = storyConfig.getStringList("story");
    }

    private void selectNewTarget() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            targetPlayer = null;
            return;
        }
        targetPlayer = onlinePlayers.get(random.nextInt(onlinePlayers.size()));
    }

    private void startMainLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    selectNewTarget();
                    return;
                }

                Player target = targetPlayer;

                nextEventTime.putIfAbsent(target, currentTime + getDelayTime());

                if (currentTime < nextEventTime.get(target)) {
                    return;
                }

                observeStartTime.putIfAbsent(target, currentTime);

                if (currentTime - observeStartTime.get(target) < getObserveTime()) {
                    performObserveStage(target);
                } else {
                    performAttackStage(target);
                    observeStartTime.remove(target);
                    nextEventTime.put(target, currentTime + getDelayTime());
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private long getDelayTime() {
        int minDelay = getConfig().getInt("timers.min-delay-seconds", 240);
        int maxDelay = getConfig().getInt("timers.max-delay-seconds", 360);
        return (minDelay * 1000L) + random.nextInt((maxDelay - minDelay) * 1000);
    }

    private long getObserveTime() {
        int minObserve = getConfig().getInt("timers.observe-min-seconds", 10);
        int maxObserve = getConfig().getInt("timers.observe-max-seconds", 20);
        return (minObserve * 1000L) + random.nextInt((maxObserve - minObserve) * 1000);
    }

    private void performObserveStage(Player player) {
        Location behindPlayer = getLocationBehind(player, 1.5);

        if (random.nextDouble() < getConfig().getDouble("chances.steps", 0.3)) {
            player.getWorld().playSound(behindPlayer, Sound.ENTITY_ZOMBIE_STEP, 0.4f, 0.7f);
        }

        if (random.nextDouble() < getConfig().getDouble("chances.whisper", 0.25)) {
            player.getWorld().playSound(behindPlayer, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.6f, 0.3f);
        }

        if (random.nextDouble() < getConfig().getDouble("chances.silhouette", 0.15)) {
            spawnSilhouette(behindPlayer);
        }

        if (random.nextDouble() < getConfig().getDouble("chances.door-event", 0.2)) {
            manipulateDoor(player);
        }
    }

    private void performAttackStage(Player player) {
        int currentFear = fearLevel.getOrDefault(player.getUniqueId(), 0) + 1;
        fearLevel.put(player.getUniqueId(), currentFear);

        int finalThreshold = getConfig().getInt("final-stage.events-before-final", 5);
        if (currentFear >= finalThreshold) {
            startFinalStage(player);
            return;
        }

        Location playerLoc = player.getLocation().clone();
        playerLoc.add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
        player.teleport(playerLoc);

        Location behind = getLocationBehind(player, 1.0);
        player.getWorld().playSound(behind, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);

        if (random.nextDouble() < getConfig().getDouble("chances.instant-kill", 0.05)) {
            player.damage(1000);
            return;
        }

        if (random.nextDouble() < getConfig().getDouble("chances.damage", 0.4)) {
            player.damage(3);
        }

        if (random.nextDouble() < getConfig().getDouble("chances.drop-note", 0.3)) {
            dropStoryNote(player.getLocation());
        }

        if (random.nextDouble() < getConfig().getDouble("chances.give-book", 0.2)) {
            giveStoryBook(player);
        }

        if (random.nextDouble() < getConfig().getDouble("chances.break-block", 0.25)) {
            breakNearbyBlock(player);
        }
    }

    private void startFinalStage(Player player) {
        new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                Location behind = getLocationBehind(player, 1.0);
                spawnSilhouette(behind);

                player.getWorld().playSound(behind, Sound.ENTITY_ZOMBIE_STEP, 0.6f, 0.6f);

                if (random.nextDouble() < getConfig().getDouble("final-stage.instant-kill-chance", 0.15)) {
                    player.damage(1000);
                    fearLevel.remove(player.getUniqueId());
                    targetPlayer = null;
                    cancel();
                    return;
                }

                tickCount++;
                int maxDuration = getConfig().getInt("final-stage.duration-seconds", 40) * 20;
                if (tickCount > maxDuration) {
                    player.damage(1000);
                    fearLevel.remove(player.getUniqueId());
                    targetPlayer = null;
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void dropStoryNote(Location location) {
        Player nearestPlayer = null;
        double nearestDistance = 5.0;

        for (Player p : location.getWorld().getPlayers()) {
            double distance = p.getLocation().distance(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPlayer = p;
            }
        }

        if (nearestPlayer == null || storyLines.isEmpty()) {
            return;
        }

        int progressIndex = storyProgress.getOrDefault(nearestPlayer.getUniqueId(), 0);

        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.setDisplayName("§7Старая записка");
        meta.setLore(Collections.singletonList("§8" + storyLines.get(progressIndex)));
        note.setItemMeta(meta);

        location.getWorld().dropItemNaturally(location, note);

        progressIndex = (progressIndex + 1) % storyLines.size();
        storyProgress.put(nearestPlayer.getUniqueId(), progressIndex);
    }

    private void giveStoryBook(Player player) {
        if (storyLines.isEmpty()) {
            return;
        }

        int progressIndex = storyProgress.getOrDefault(player.getUniqueId(), 0);

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        bookMeta.setTitle("...");
        bookMeta.setAuthor("?");
        bookMeta.addPage(storyLines.get(progressIndex));
        bookMeta.addPage("Сервер помнит тебя.");
        book.setItemMeta(bookMeta);
        player.getInventory().addItem(book);

        progressIndex = (progressIndex + 1) % storyLines.size();
        storyProgress.put(player.getUniqueId(), progressIndex);
    }

    private void spawnSilhouette(Location location) {
        ArmorStand silhouette = location.getWorld().spawn(location, ArmorStand.class);
        silhouette.setInvisible(false);
        silhouette.setMarker(true);
        silhouette.setGravity(false);
        silhouette.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        
        new BukkitRunnable() {
            @Override
            public void run() {
                silhouette.remove();
            }
        }.runTaskLater(this, 20L);
    }

    private void breakNearbyBlock(Player player) {
        Location loc = player.getLocation().clone().add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
        Block block = loc.getBlock();
        if (block.getType() != Material.AIR && block.getType().isSolid()) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
            block.breakNaturally();
        }
    }

    private void manipulateDoor(Player player) {
        for (Block block : getNearbyBlocks(player.getLocation(), 2)) {
            if (block.getType().name().contains("DOOR")) {
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0f, 0.6f);
                break;
            }
        }
    }

    private List<Block> getNearbyBlocks(Location location, int radius) {
        List<Block> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    blocks.add(location.clone().add(x, y, z).getBlock());
                }
            }
        }
        return blocks;
    }

    private Location getLocationBehind(Player player, double distance) {
        Location location = player.getLocation().clone();
        Vector direction = location.getDirection().normalize().multiply(-distance);
        location.add(direction);
        return location;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (targetPlayer != null && event.getEntity().equals(targetPlayer)) {
            targetPlayer = null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shtany")) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7/shtany reload - Перезагрузить конфигурацию");
            sender.sendMessage("§7/shtany force - Принудительно вызвать событие");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadStoryConfig();
            sender.sendMessage("§aКонфигурация перезагружена.");
            return true;
        }

        if (args[0].equalsIgnoreCase("force")) {
            if (targetPlayer != null) {
                performAttackStage(targetPlayer);
                sender.sendMessage("§cСобытие принудительно вызвано.");
            } else {
                sender.sendMessage("§7Нет активной цели.");
            }
            return true;
        }

        return true;
    }
}
