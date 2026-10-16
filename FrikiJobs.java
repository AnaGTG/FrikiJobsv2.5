package com.frikifest.frikijobs;

import com.frikifest.frikijobs.gui.JobsGUI;
import com.frikifest.frikijobs.listener.GUIListener;
import com.frikifest.frikijobs.listener.JobActionListener;
import com.frikifest.frikijobs.manager.EconomyManager;
import com.frikifest.frikijobs.manager.JobManager;
import com.frikifest.frikijobs.manager.PlayerJobManager;
import com.frikifest.frikijobs.model.Job;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public final class FrikiJobs extends JavaPlugin {

    private static FrikiJobs instance;
    private JobManager jobManager;
    private EconomyManager economyManager;
    private PlayerJobManager playerJobManager;

    private static final List<String> VALID_TIERS = Arrays.asList("TI", "TII", "TIII", "TIV", "TV", "TVI", "TVII");

    @Override
    public void onEnable() {
        instance = this;
        
        // Asegurar que la carpeta del plugin y config.yml se creen siempre de forma segura
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();

        this.economyManager = new EconomyManager();
        if (!economyManager.setupEconomy()) {
            getLogger().warning("Vault no encontrado o inactivo. La integración económica se deshabilitó temporalmente.");
        }

        this.jobManager = new JobManager(this);
        jobManager.loadJobs();

        this.playerJobManager = new PlayerJobManager(this);

        getServer().getPluginManager().registerEvents(new JobActionListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        getLogger().info("FrikiJobs v2.1 inicializado correctamente con compatibilidad para Paper 26.1, Vault, Essentials y LuckPerms.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("jobs")) {
            if (args.length > 0) {
                String subCommand = args[0].toLowerCase();

                if (subCommand.equals("xp") || subCommand.equals("exp")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
                        return true;
                    }

                    String currentJobId = playerJobManager.getPlayerJob(player.getUniqueId());
                    if (currentJobId == null) {
                        player.sendMessage(ChatColor.YELLOW + "[FrikiJobs] " + ChatColor.RED + "Actualmente no tienes ningún trabajo asignado.");
                        return true;
                    }

                    Job job = jobManager.getJob(currentJobId);
                    String jobName = job != null ? job.getName() : currentJobId;
                    String currentTier = playerJobManager.getPlayerTier(player.getUniqueId());
                    int xp = playerJobManager.getPlayerXp(player.getUniqueId());

                    player.sendMessage(ChatColor.GOLD + "========== [ " + ChatColor.YELLOW + "Experiencia de Trabajo" + ChatColor.GOLD + " ] ==========");
                    player.sendMessage(ChatColor.GRAY + "💼 Trabajo: " + ChatColor.GREEN + jobName);
                    player.sendMessage(ChatColor.GRAY + "⭐ Nivel: " + ChatColor.AQUA + "Tier " + currentTier);
                    player.sendMessage(ChatColor.GRAY + "⚡ XP Ganada: " + ChatColor.YELLOW + xp + " XP");
                    player.sendMessage(ChatColor.GOLD + "===============================================");
                    return true;
                }

                if (subCommand.equals("info") || subCommand.equals("stats") || subCommand.equals("status")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Este comando solo puede ser ejecutado por un jugador en el juego.");
                        return true;
                    }

                    String currentJobId = playerJobManager.getPlayerJob(player.getUniqueId());
                    if (currentJobId == null) {
                        player.sendMessage(ChatColor.YELLOW + "[FrikiJobs] " + ChatColor.RED + "Actualmente no tienes ningún trabajo asignado.");
                        player.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.GOLD + "/jobs" + ChatColor.GRAY + " para elegir un empleo.");
                        return true;
                    }

                    Job job = jobManager.getJob(currentJobId);
                    String jobName = job != null ? job.getName() : currentJobId;
                    String currentTier = playerJobManager.getPlayerTier(player.getUniqueId());
                    double earnings = playerJobManager.getPlayerEarnings(player.getUniqueId());
                    int xp = playerJobManager.getPlayerXp(player.getUniqueId());

                    player.sendMessage(ChatColor.GOLD + "========== [ " + ChatColor.YELLOW + "Tu Información de Trabajo" + ChatColor.GOLD + " ] ==========");
                    player.sendMessage(ChatColor.GRAY + "💼 Trabajo Actual: " + ChatColor.GREEN + jobName);
                    player.sendMessage(ChatColor.GRAY + "⭐ Nivel Actual: " + ChatColor.AQUA + "Tier " + currentTier);
                    player.sendMessage(ChatColor.GRAY + "💰 Ganancias Totales: " + ChatColor.GREEN + "$" + String.format("%.2f", earnings));
                    player.sendMessage(ChatColor.GRAY + "⚡ Experiencia Total: " + ChatColor.YELLOW + xp + " XP");
                    player.sendMessage(ChatColor.GRAY + "📝 Estado: " + ChatColor.GREEN + "Activo");
                    player.sendMessage(ChatColor.GOLD + "===============================================");
                    return true;
                }

                if (subCommand.equals("reload")) {
                    if (!sender.hasPermission("jobs.admin")) {
                        sender.sendMessage(ChatColor.RED + "No tienes permiso para recargar la configuración de FrikiJobs.");
                        return true;
                    }

                    reloadConfig();
                    jobManager.loadJobs();
                    sender.sendMessage(ChatColor.GREEN + "[FrikiJobs] Configuración y empleos recargados correctamente.");
                    return true;
                }

                if (!sender.hasPermission("jobs.admin")) {
                    sender.sendMessage(ChatColor.RED + "No tienes permiso para ejecutar comandos administrativos de trabajos.");
                    return true;
                }

                if (subCommand.equals("settier") || subCommand.equals("tier")) {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Uso correcto: /jobs settier <jugador> <tier>");
                        return true;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        sender.sendMessage(ChatColor.RED + "El jugador '" + args[1] + "' no ha sido encontrado.");
                        return true;
                    }

                    String currentJob = playerJobManager.getPlayerJob(target.getUniqueId());
                    if (currentJob == null) {
                        sender.sendMessage(ChatColor.RED + "El jugador " + target.getName() + " no tiene un trabajo asignado.");
                        return true;
                    }

                    String targetTier = args[2].toUpperCase();
                    if (!VALID_TIERS.contains(targetTier)) {
                        sender.sendMessage(ChatColor.RED + "El tier '" + args[2] + "' no es válido.");
                        return true;
                    }

                    playerJobManager.setPlayerTier(target.getUniqueId(), targetTier);
                    sender.sendMessage(ChatColor.GREEN + "[FrikiJobs] Se ha establecido el nivel de " + target.getName() + " a Tier " + targetTier + ".");

                    if (target.isOnline()) {
                        ((Player) target).sendMessage(ChatColor.GOLD + "[FrikiJobs] Un administrador ha cambiado tu nivel de empleo a Tier " + targetTier + ".");
                    }
                    return true;
                }

                if (subCommand.equals("set")) {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Uso correcto: /jobs set <jugador> <trabajo>");
                        return true;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        sender.sendMessage(ChatColor.RED + "El jugador '" + args[1] + "' no ha sido encontrado.");
                        return true;
                    }

                    String jobId = args[2].toLowerCase();
                    Job job = jobManager.getJob(jobId);
                    if (job == null) {
                        sender.sendMessage(ChatColor.RED + "El trabajo '" + args[2] + "' no existe.");
                        return true;
                    }

                    playerJobManager.setPlayerJob(target.getUniqueId(), jobId);
                    sender.sendMessage(ChatColor.GREEN + "[FrikiJobs] Se ha asignado el trabajo de " + job.getName() + " a " + target.getName() + ".");

                    if (target.isOnline()) {
                        ((Player) target).sendMessage(ChatColor.GOLD + "[FrikiJobs] Un administrador te ha asignado el trabajo de " + job.getName() + ".");
                    }
                    return true;
                }

                if (subCommand.equals("remove") || subCommand.equals("clear")) {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Uso correcto: /jobs remove <jugador>");
                        return true;
                    }

                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        sender.sendMessage(ChatColor.RED + "El jugador '" + args[1] + "' no ha sido encontrado.");
                        return true;
                    }

                    String currentJob = playerJobManager.getPlayerJob(target.getUniqueId());
                    if (currentJob == null) {
                        sender.sendMessage(ChatColor.YELLOW + "[FrikiJobs] El jugador " + target.getName() + " no tiene trabajo asignado.");
                        return true;
                    }

                    playerJobManager.setPlayerJob(target.getUniqueId(), null);
                    sender.sendMessage(ChatColor.GREEN + "[FrikiJobs] Se le ha retirado el trabajo a " + target.getName() + ".");

                    if (target.isOnline()) {
                        ((Player) target).sendMessage(ChatColor.RED + "[FrikiJobs] Un administrador te ha retirado tu trabajo actual.");
                    }
                    return true;
                }

                sender.sendMessage(ChatColor.RED + "Comando desconocido. Usa /jobs xp, info, reload, set, settier o remove.");
                return true;
            }

            if (sender instanceof Player player) {
                JobsGUI.openJobsMenu(player);
                return true;
            } else {
                sender.sendMessage("Uso en consola: /jobs reload | /jobs set | /jobs settier | /jobs remove");
            }
        }
        return false;
    }

    public static FrikiJobs getInstance() {
        return instance;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlayerJobManager getPlayerJobManager() {
        return playerJobManager;
    }
}
