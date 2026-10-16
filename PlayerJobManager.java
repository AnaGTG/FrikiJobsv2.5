package com.frikifest.frikijobs.manager;

import com.frikifest.frikijobs.FrikiJobs;
import com.frikifest.frikijobs.model.Job;
import com.frikifest.frikijobs.model.JobTier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerJobManager {
    private final FrikiJobs plugin;
    private File file;
    private FileConfiguration config;
    
    private final Map<UUID, String> activeJobs = new HashMap<>();
    private final Map<String, String> jobTiers = new HashMap<>();
    private final Map<String, Double> jobEarnings = new HashMap<>();
    private final Map<String, Integer> jobXp = new HashMap<>();

    private static final List<String> ORDERED_TIERS = Arrays.asList("TI", "TII", "TIII", "TIV", "TV", "TVI", "TVII");

    public PlayerJobManager(FrikiJobs plugin) {
        this.plugin = plugin;
        createFile();
    }

    private void createFile() {
        file = new File(plugin.getDataFolder(), "player_jobs.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo crear player_jobs.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (config.isConfigurationSection(key)) {
                    String active = config.getString(key + ".active_job");
                    if (active != null) {
                        activeJobs.put(uuid, active.toLowerCase());
                    }
                    
                    org.bukkit.configuration.ConfigurationSection jobsSec = config.getConfigurationSection(key + ".jobs_data");
                    if (jobsSec != null) {
                        for (String jobId : jobsSec.getKeys(false)) {
                            String prefix = key + ".jobs_data." + jobId;
                            String tier = config.getString(prefix + ".tier", "TI");
                            double earnings = config.getDouble(prefix + ".earnings", 0.0);
                            int xp = config.getInt(prefix + ".xp", 0);
                            
                            jobTiers.put(uuid.toString() + "." + jobId.toLowerCase(), tier);
                            jobEarnings.put(uuid.toString() + "." + jobId.toLowerCase(), earnings);
                            jobXp.put(uuid.toString() + "." + jobId.toLowerCase(), xp);
                        }
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public String getPlayerJob(UUID uuid) {
        return activeJobs.get(uuid);
    }

    public String getPlayerTier(UUID uuid) {
        String job = activeJobs.get(uuid);
        if (job == null) return "TI";
        return jobTiers.getOrDefault(uuid.toString() + "." + job, "TI");
    }

    public double getPlayerEarnings(UUID uuid) {
        String job = activeJobs.get(uuid);
        if (job == null) return 0.0;
        return jobEarnings.getOrDefault(uuid.toString() + "." + job, 0.0);
    }

    public int getPlayerXp(UUID uuid) {
        String job = activeJobs.get(uuid);
        if (job == null) return 0;
        return jobXp.getOrDefault(uuid.toString() + "." + job, 0);
    }

    public double addPlayerEarnings(UUID uuid, double amount) {
        String job = activeJobs.get(uuid);
        if (job == null) return 0.0;
        
        String keyMap = uuid.toString() + "." + job;
        double current = jobEarnings.getOrDefault(keyMap, 0.0);
        double updated = current + amount;
        jobEarnings.put(keyMap, updated);

        config.set(uuid.toString() + ".jobs_data." + job + ".earnings", updated);
        save();
        return updated;
    }

    public int addPlayerXp(UUID uuid, int amount) {
        String jobId = activeJobs.get(uuid);
        if (jobId == null) return 0;

        String keyMap = uuid.toString() + "." + jobId;
        int currentXp = jobXp.getOrDefault(keyMap, 0);
        int newXp = currentXp + amount;
        jobXp.put(keyMap, newXp);

        config.set(uuid.toString() + ".jobs_data." + jobId + ".xp", newXp);

        // Check tier level up
        Job jobObj = plugin.getJobManager().getJob(jobId);
        if (jobObj != null) {
            String currentTier = getPlayerTier(uuid);
            String nextTier = getNextTierKey(currentTier);
            if (nextTier != null) {
                JobTier nextTierObj = jobObj.getTiers().get(nextTier);
                if (nextTierObj != null && newXp >= nextTierObj.getRequiredXp()) {
                    setPlayerTier(uuid, nextTier);
                    org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(org.bukkit.ChatColor.GOLD + "=================================================");
                        player.sendMessage(org.bukkit.ChatColor.GREEN + "🎉 ¡FELICIDADES! Has ascendido a " + org.bukkit.ChatColor.AQUA + "Tier " + nextTier + org.bukkit.ChatColor.GREEN + " en tu trabajo de " + jobObj.getName() + "!");
                        player.sendMessage(org.bukkit.ChatColor.GOLD + "=================================================");
                    }
                }
            }
        }

        save();
        return newXp;
    }

    private String getNextTierKey(String currentTier) {
        int idx = ORDERED_TIERS.indexOf(currentTier.toUpperCase());
        if (idx >= 0 && idx < ORDERED_TIERS.size() - 1) {
            return ORDERED_TIERS.get(idx + 1);
        }
        return null;
    }

    public List<JobTier> getAllUnlockedTiers(UUID uuid) {
        List<JobTier> unlocked = new ArrayList<>();
        String jobId = activeJobs.get(uuid);
        if (jobId == null) return unlocked;

        Job jobObj = plugin.getJobManager().getJob(jobId);
        if (jobObj == null) return unlocked;

        String currentTierKey = getPlayerTier(uuid);
        int maxIndex = ORDERED_TIERS.indexOf(currentTierKey.toUpperCase());
        if (maxIndex < 0) maxIndex = 0;

        for (int i = 0; i <= maxIndex; i++) {
            String tierKey = ORDERED_TIERS.get(i);
            JobTier t = jobObj.getTiers().get(tierKey);
            if (t != null) {
                unlocked.add(t);
            }
        }
        return unlocked;
    }

    public void setPlayerJob(UUID uuid, String jobId) {
        if (jobId == null) {
            activeJobs.remove(uuid);
            config.set(uuid.toString() + ".active_job", null);
        } else {
            String formattedJob = jobId.toLowerCase();
            activeJobs.put(uuid, formattedJob);
            config.set(uuid.toString() + ".active_job", formattedJob);
            
            String keyMap = uuid.toString() + "." + formattedJob;
            jobTiers.put(keyMap, "TI");
            jobEarnings.put(keyMap, 0.0);
            jobXp.put(keyMap, 0);
            config.set(uuid.toString() + ".jobs_data." + formattedJob + ".tier", "TI");
            config.set(uuid.toString() + ".jobs_data." + formattedJob + ".earnings", 0.0);
            config.set(uuid.toString() + ".jobs_data." + formattedJob + ".xp", 0);
        }
        save();
    }

    public void setPlayerTier(UUID uuid, String tierId) {
        String job = activeJobs.get(uuid);
        if (job == null) return;
        
        String formattedTier = tierId.toUpperCase();
        String keyMap = uuid.toString() + "." + job;
        jobTiers.put(keyMap, formattedTier);
        config.set(uuid.toString() + ".jobs_data." + job + ".tier", formattedTier);
        save();
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar player_jobs.yml");
        }
    }
}
