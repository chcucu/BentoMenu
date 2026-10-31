package dev.bento.test;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic Vault economy provider used only by the test harness.
 */
public final class TestEconomy implements Economy {

    private final JavaPlugin plugin;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final double startingBalance;

    public TestEconomy(JavaPlugin plugin, double startingBalance) {
        this.plugin = plugin;
        this.startingBalance = startingBalance;
    }

    public void register() {
        plugin.getServer().getServicesManager().register(Economy.class, this, plugin, ServicePriority.Highest);
    }

    public void setBalance(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), Math.max(0, amount));
    }

    public void giveAll(double amount) {
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
        }
    }

    private double raw(OfflinePlayer player) {
        return balances.computeIfAbsent(player.getUniqueId(), ignored -> startingBalance);
    }

    private EconomyResponse ok(OfflinePlayer player, double amount) {
        return new EconomyResponse(amount, raw(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse fail(String message) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, message);
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "BentoTestEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.ROOT, "$%.2f", amount);
    }

    @Override
    public String currencyNamePlural() {
        return "coins";
    }

    @Override
    public String currencyNameSingular() {
        return "coin";
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return raw(player);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double current = raw(player);
        if (amount < 0) {
            return fail("negative amount");
        }
        if (current < amount) {
            return fail("insufficient funds");
        }
        balances.put(player.getUniqueId(), current - amount);
        return ok(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return fail("negative amount");
        }
        balances.put(player.getUniqueId(), raw(player) + amount);
        return ok(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return fail("banks unsupported");
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return fail("banks unsupported");
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }
}
