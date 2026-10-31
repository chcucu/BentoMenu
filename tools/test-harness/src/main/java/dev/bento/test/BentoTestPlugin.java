package dev.bento.test;

import dev.bento.menu.api.v1.BentoApi;
import dev.bento.menu.api.v1.BentoApiProvider;
import dev.bento.menu.api.v1.MenuOpenOptions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only plugin: deterministic Vault economy and a TPS probe for load tests.
 * Never shipped with BentoMenu.
 */
public final class BentoTestPlugin extends JavaPlugin implements CommandExecutor {

    private TestEconomy economy;
    private BukkitTask probe;
    private final DateTimeFormatter timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onEnable() {
        economy = new TestEconomy(this, 500.0);
        economy.register();
        getCommand("bentotest").setExecutor(this);
        String serverName = Bukkit.getName().toLowerCase(Locale.ROOT);
        boolean folia = serverName.contains("folia");
        if (!folia) {
            long period = Math.max(20, getConfig().getLong("probe-interval-ticks", 100));
            probe = Bukkit.getScheduler().runTaskTimer(this, this::logTps, period, period);
        }
        getLogger().info("Bento test harness enabled with Vault test economy (folia=" + folia + ").");
    }

    @Override
    public void onDisable() {
        if (probe != null) {
            probe.cancel();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /bentotest <openall|closeall|balance|giveall|tps|status>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "openall" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /bentotest openall <menu>");
                    return true;
                }
                BentoApi api = BentoApiProvider.get();
                int opened = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    api.open(player, args[1], MenuOpenOptions.builder().build());
                    opened++;
                }
                sender.sendMessage("Opened " + args[1] + " for " + opened + " players.");
            }
            case "closeall" -> {
                BentoApi api = BentoApiProvider.get();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    api.close(player);
                }
                sender.sendMessage("Closed all menu sessions.");
            }
            case "balance" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /bentotest balance <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not online.");
                    return true;
                }
                economy.setBalance(target, Double.parseDouble(args[2]));
                sender.sendMessage("Balance set.");
            }
            case "giveall" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /bentotest giveall <amount>");
                    return true;
                }
                double amount = Double.parseDouble(args[1]);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    economy.depositPlayer(player, amount);
                }
                sender.sendMessage("Gave " + amount + " to " + Bukkit.getOnlinePlayers().size() + " players.");
            }
            case "tps" -> sender.sendMessage(tpsLine());
            case "status" -> {
                sender.sendMessage("online=" + Bukkit.getOnlinePlayers().size()
                        + " " + tpsLine()
                        + " economy=" + economy.getName());
            }
            default -> sender.sendMessage("Unknown test subcommand.");
        }
        return true;
    }

    private void logTps() {
        String line = timestamp.format(LocalDateTime.now()) + " " + tpsLine()
                + " online=" + Bukkit.getOnlinePlayers().size();
        File file = new File(getDataFolder(), "tps.log");
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // The console line is enough if the file cannot be written.
        }
        if (getConfig().getBoolean("log-console", false)) {
            getLogger().info("[PROBE] " + line);
        }
    }

    private String tpsLine() {
        double[] tps = new double[]{20, 20, 20};
        double avgTick = -1;
        try {
            Method method = Bukkit.class.getMethod("getTPS");
            tps = (double[]) method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method method = Bukkit.class.getMethod("getAverageTickTime");
            avgTick = ((Number) method.invoke(null)).doubleValue();
        } catch (ReflectiveOperationException ignored) {
        }
        return String.format(java.util.Locale.ROOT, "tps=%.2f/%.2f/%.2f avgTick=%.2fms",
                tps[0], tps[1], tps[2], avgTick);
    }
}
