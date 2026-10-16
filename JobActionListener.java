package com.frikifest.frikijobs.listener;

import com.frikifest.frikijobs.FrikiJobs;
import com.frikifest.frikijobs.model.JobTier;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public class JobActionListener implements Listener {

    private final FrikiJobs plugin;
    private final Set<String> playerPlacedBlocks = new HashSet<>();
    private final Map<String, Integer> brewingStandPointers = new HashMap<>();

    public JobActionListener(FrikiJobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        String blockKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        playerPlacedBlocks.add(blockKey);
    }

    // --- MINERO Y LEÑADOR Y GRANJERO ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();

        boolean wasPlacedByPlayer = playerPlacedBlocks.contains(blockKey);
        if (wasPlacedByPlayer) {
            playerPlacedBlocks.remove(blockKey);
        }

        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null) return;

        // Minero y Leñador no cobran si el bloque lo colocó un jugador
        if ((currentJob.equals("minero") || currentJob.equals("lenador")) && wasPlacedByPlayer) {
            return;
        }

        List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());

        if (currentJob.equals("minero") || currentJob.equals("lenador")) {
            for (JobTier tier : unlockedTiers) {
                if (tier.getMaterials() != null && tier.getMaterials().contains(block.getType().name())) {
                    payReward(player, currentJob, tier.getRewardMoney(), tier.getRewardXp());
                    break;
                }
            }
        } else if (currentJob.equals("granjero")) {
            // Granjero SÍ cobra por cultivos que él plantó y cultivó hasta su edad máxima
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    return; // Debe estar completamente maduro
                }
            }

            for (JobTier tier : unlockedTiers) {
                if (tier.getMaterials() != null && (tier.getMaterials().contains(block.getType().name()) || isGranjeroCropMatch(tier.getMaterials(), block.getType().name()))) {
                    payReward(player, "granjero", tier.getRewardMoney(), tier.getRewardXp());
                    break;
                }
            }
        }
    }

    private boolean isGranjeroCropMatch(List<String> mats, String blockMat) {
        for (String m : mats) {
            if (m.equals("WHEAT") && blockMat.equals("WHEAT")) return true;
            if (m.equals("CARROT") && blockMat.equals("CARROTS")) return true;
            if (m.equals("POTATO") && blockMat.equals("POTATOES")) return true;
            if (m.equals("BEETROOT") && blockMat.equals("BEETROOTS")) return true;
        }
        return false;
    }

    // --- GUERRERO ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        String currentJob = plugin.getPlayerJobManager().getPlayerJob(killer.getUniqueId());
        if (currentJob == null || !currentJob.equals("guerrero")) return;

        List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(killer.getUniqueId());
        String entityTypeName = entity.getType().name();

        for (JobTier tier : unlockedTiers) {
            if (tier.getMaterials() != null && tier.getMaterials().contains(entityTypeName)) {
                payReward(killer, "guerrero", tier.getRewardMoney(), tier.getRewardXp());
                break;
            }
        }
    }

    // --- HECHICERO: XP DEL JUEGO (TI) ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void PlayerExpChangeEvent(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int amount = event.getAmount();
        if (amount <= 0) return;

        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null || !currentJob.equals("hechicero")) return;

        List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());
        for (JobTier tier : unlockedTiers) {
            if (tier.getMaterials() != null && tier.getMaterials().contains("GAME_XP")) {
                double totalMoney = tier.getRewardMoney() * amount;
                int totalXp = tier.getRewardXp() * amount;
                payReward(player, "hechicero", totalMoney, totalXp);
                break;
            }
        }
    }

    // --- HECHICERO: MESA DE ENCANTAMIENTOS (TII - TVII) ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null || !currentJob.equals("hechicero")) return;

        List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());
        int lapisSpent = event.whichButton() + 1; // 1, 2 o 3

        for (JobTier tier : unlockedTiers) {
            if (tier.getMaterials() != null && tier.getMaterials().contains("LAPIS_SPENT")) {
                payReward(player, "hechicero", tier.getRewardMoney() * lapisSpent, tier.getRewardXp() * lapisSpent);
                break;
            }
        }
    }

    // --- HECHICERO: YUNQUE Y LIBROS ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null || !currentJob.equals("hechicero")) return;

        ItemStack result = event.getRecipe().getResult();
        if (result.getType() == Material.ENCHANTED_BOOK) {
            checkEnchantedBookReward(player, result);
        }
    }

    private void checkEnchantedBookReward(Player player, ItemStack book) {
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) return;
        int maxLvl = 0;
        for (int lvl : meta.getStoredEnchants().values()) {
            if (lvl > maxLvl) maxLvl = lvl;
        }

        String targetCode = null;
        if (maxLvl == 1 || maxLvl == 2) targetCode = "BOOK_I_II";
        else if (maxLvl == 3) targetCode = "BOOK_II_III";
        else if (maxLvl == 4) targetCode = "BOOK_III_IV";
        else if (maxLvl == 5) targetCode = "BOOK_IV_V";
        else if (maxLvl >= 6) targetCode = "BOOK_V_VI";

        if (targetCode == null) return;

        List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());
        for (JobTier tier : unlockedTiers) {
            if (tier.getMaterials() != null && tier.getMaterials().contains(targetCode)) {
                payReward(player, "hechicero", tier.getRewardMoney(), tier.getRewardXp());
                break;
            }
        }
    }

    // --- PESCADOR ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null || !currentJob.equals("pescador")) return;

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (event.getCaught() instanceof org.bukkit.entity.Item itemEntity) {
                ItemStack item = itemEntity.getItemStack();
                String matName = item.getType().name();

                List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());
                for (JobTier tier : unlockedTiers) {
                    if (tier.getMaterials() != null && tier.getMaterials().contains(matName)) {
                        payReward(player, "pescador", tier.getRewardMoney(), tier.getRewardXp());
                        break;
                    }
                }
            }
        }
    }

    // --- ALQUIMISTA: SOLO POR CADA 3 POCIONES PROCESADAS REALMENTE ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewingClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof BrewerInventory brewerInv)) return;

        String currentJob = plugin.getPlayerJobManager().getPlayerJob(player.getUniqueId());
        if (currentJob == null || !currentJob.equals("alquimista")) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot > 2) return; // Solo slots de pociones

        ItemStack takenItem = event.getCurrentItem();
        if (takenItem == null || takenItem.getType() == Material.AIR) return;

        // Validar que la poción haya sido elaborada y no insertada/retirada repetidamente
        if (!(takenItem.getItemMeta() instanceof PotionMeta meta)) return;

        PotionType type = meta.getBasePotionType();
        if (type == PotionType.WATER || type == PotionType.MUNDANE || type == PotionType.THICK || type == PotionType.AWKWARD) {
            return; // No recompensar pociones no terminadas si se metieron manualmente
        }

        String locKey = brewerInv.getLocation().toString();
        int currentCount = brewingStandPointers.getOrDefault(locKey, 0) + 1;

        if (currentCount >= 3) { // SOLO CADA 3 POCIONES
            brewingStandPointers.put(locKey, 0);

            List<JobTier> unlockedTiers = plugin.getPlayerJobManager().getAllUnlockedTiers(player.getUniqueId());
            for (JobTier tier : unlockedTiers) {
                payReward(player, "alquimista", tier.getRewardMoney(), tier.getRewardXp());
                break;
            }
        } else {
            brewingStandPointers.put(locKey, currentCount);
        }
    }

    private void payReward(Player player, String jobName, double money, int xp) {
        if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().depositPlayer(player, money);
        }

        String currentTier = plugin.getPlayerJobManager().getPlayerTier(player.getUniqueId());

        player.sendActionBar(
            ChatColor.translateAlternateColorCodes('&', 
            "&6[" + jobName.substring(0, 1).toUpperCase() + jobName.substring(1) + " &eTier " + currentTier + "&6] &a+$" + String.format("%.2f", money) + " &b(+" + xp + " XP)")
        );

        plugin.getPlayerJobManager().addPlayerEarnings(player.getUniqueId(), money);
        plugin.getPlayerJobManager().addPlayerXp(player.getUniqueId(), xp);
    }
}
